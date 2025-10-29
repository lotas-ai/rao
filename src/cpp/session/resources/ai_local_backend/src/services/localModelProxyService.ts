/*
 * Copyright (C) 2025 Lotas Inc. All rights reserved.
 * Licensed under the AGPL-3.0 License. See License.txt in the project root for license information.
 */

interface ILocalModelProxyService {
	processStreamingResponsesWithCallback(
		requestBody: string,
		user: any,
		originalHeaders: any,
		request_id: string,
		outputStream: any,
		originalRequest?: any
	): Promise<void>;
}

import { httpRequest } from './httpClient.js';
import { StreamingProxyHelper } from './streamingProxyHelper.js';

enum StreamResult {
	SUCCESS = 'SUCCESS',
}

interface LocalModelStreamState {
	textContent: string;
	hasTextContent: boolean;
	hasFunctionCall: boolean;
	textStreamingComplete: boolean;
	firstDeltaAtMs: number;
	lastDeltaAtMs: number;
	deltaCount: number;
	parallelFunctionCalls: Map<string, FunctionCallData>;
	hasParallelFunctionCalls: boolean;
	userStreamingStarted: boolean;
	originalRequest: any | null;
	modifiedRequest: any | null;
	cancelled: boolean;
	cancelledMessageLogged: boolean;
	writeErrorLogged: boolean;
	functionCallCompletionSent: boolean;
	currentToolCallIndex: number;
}

interface FunctionCallData {
	functionName: string;
	callId: string;
	functionArguments: string;
	argumentsComplete: boolean;
	functionCallCompletionSent: boolean;
}

export class LocalModelProxyService implements ILocalModelProxyService {
	private streamingHelper = new StreamingProxyHelper();

	async processStreamingResponsesWithCallback(
		requestBody: string,
		user: any,
		originalHeaders: any,
		request_id: string,
		outputStream: any,
		originalRequest?: any
	): Promise<void> {
		await this.processStreamingResponsesWithCallbackInternal(requestBody, user, originalHeaders, request_id, outputStream, originalRequest);
	}

	private async processStreamingResponsesWithCallbackInternal(
		requestBody: string, 
		_user: any, 
		_originalHeaders: any, 
		request_id: string, 
		outputStream: any, 
		originalRequest?: any
	): Promise<StreamResult> {
		const requestBodyJson = JSON.parse(requestBody);
		
		// Get endpoint URL and model name from request
		// The R code will have already populated these from user settings
		const endpointBase = requestBodyJson.localmodel_endpoint || 'http://localhost:11434';
		const apiUrl = `${endpointBase}/v1/chat/completions`;
		
		// Get API key from BYOK keys (optional for local models)
		const apiKey = requestBodyJson.byok_keys?.localmodel;
		
		// Get the actual model name to use
		const actualModelName = requestBodyJson.localmodel_name || 'llama3.2:1b';
		
		const inactivityTimeoutMs = 30000;
		
		// Convert from internal format to OpenAI Chat Completions format
		const chatCompletionRequest: any = {
			model: actualModelName,
			messages: requestBodyJson.messages,
			stream: true
		};
		
		// Add temperature if present and not null
		if (requestBodyJson.temperature !== undefined && requestBodyJson.temperature !== null) {
			chatCompletionRequest.temperature = requestBodyJson.temperature;
		}
		
		// Add tools (function definitions) if present
		if (requestBodyJson.tools && requestBodyJson.tools.length > 0) {
			chatCompletionRequest.tools = requestBodyJson.tools;
		}
		
		// Build headers
		const headers: Record<string, string> = {
			'Content-Type': 'application/json'
		};
		
		// Add authorization if API key is provided
		if (apiKey) {
			headers['Authorization'] = `Bearer ${apiKey}`;
		}
		
		const streamState: LocalModelStreamState = {
			textContent: '',
			hasTextContent: false,
			hasFunctionCall: false,
			textStreamingComplete: false,
			firstDeltaAtMs: -1,
			lastDeltaAtMs: -1,
			deltaCount: 0,
			parallelFunctionCalls: new Map(),
			hasParallelFunctionCalls: false,
			userStreamingStarted: false,
			originalRequest: originalRequest,
			modifiedRequest: null,
			cancelled: false,
			cancelledMessageLogged: false,
			writeErrorLogged: false,
			functionCallCompletionSent: false,
			currentToolCallIndex: -1
		};
		
		let lastStreamEventTime = Date.now();
		let firstEventLogged = false;
		let sseBuffer = '';

		const response = await httpRequest(apiUrl, {
			method: 'POST',
			headers,
			body: JSON.stringify(chatCompletionRequest)
		});

		if (!response.ok) {
			throw new Error(`Local model API error: ${response.status} ${response.statusText}`);
		}

		const stream = await response.body();
		if (!stream) {
			throw new Error('No response stream available');
		}
		
		await new Promise<void>((resolve, reject) => {
			const timeoutInterval = setInterval(() => {					
				const timeSinceLastEvent = Date.now() - lastStreamEventTime;
				if (timeSinceLastEvent > inactivityTimeoutMs) {
					const timeoutSeconds = Math.floor(inactivityTimeoutMs / 1000);
					this.streamingHelper.safeWriteToOutputStream(outputStream,
						this.streamingHelper.createTimeoutEvent(request_id, "LocalModel", timeoutSeconds));
					clearInterval(timeoutInterval);
					resolve();
				}
			}, 1000);

			stream.on('data', (chunk: Buffer) => {
				try {
					if (streamState.cancelled) {
						clearInterval(timeoutInterval);
						resolve();
						return;
					}
					
					const nowMs = Date.now();
					lastStreamEventTime = nowMs;
					if (!firstEventLogged) {
						firstEventLogged = true;
					}
			
					const chunkStr = chunk.toString('utf8');
					sseBuffer += chunkStr;
					
					// Parse SSE format: data: {...}\n\n
					while (sseBuffer.includes('\n\n')) {
						const eventEnd = sseBuffer.indexOf('\n\n');
						const eventBlock = sseBuffer.substring(0, eventEnd);
						sseBuffer = sseBuffer.substring(eventEnd + 2);
						
						if (eventBlock.trim()) {
							const lines = eventBlock.split('\n');
							let eventData: string | null = null;
							
							for (const line of lines) {
								if (line.startsWith('data: ')) {
									eventData = line.substring(6).trim();
								}
							}
							
							if (eventData && eventData !== '[DONE]') {
								try {
									const jsonData = JSON.parse(eventData);
									this.processStreamingChunk(jsonData, request_id, outputStream, streamState, originalRequest);
								} catch (e) {
									console.error('Failed to parse SSE data:', eventData, e);
								}
							}
						}
					}
					
				} catch (e) {
					if (streamState.cancelled) {
						clearInterval(timeoutInterval);
						resolve();
						return;
					}
				}
			});

			stream.on('error', (error: Error) => {
				clearInterval(timeoutInterval);
				let isCancellation = false;
				const errorMessage = error.message;
				
				if (errorMessage && 
					(errorMessage.includes("Connection reset") || 
					 errorMessage.includes("Connection closed") ||
					 errorMessage.includes("cancelled"))) {
					isCancellation = true;
				}
				
				if (isCancellation) {
					streamState.cancelled = true;
					resolve();
					return;
				}
				
				try {
					let finalErrorMessage = "Stream error: " + errorMessage;
					
					this.streamingHelper.safeWriteToOutputStream(outputStream,
						this.streamingHelper.createErrorEvent(request_id, finalErrorMessage));
				} catch (e) {
					console.error("Could not send error to client:", (e as Error).message);
				}
				reject(error);
			});

			stream.on('end', () => {
				clearInterval(timeoutInterval);
				if (streamState.cancelled) {
					resolve();
					return;
				}
				
				try {
					this.handleStreamCompletion(request_id, outputStream, streamState);
				} catch (e) {
					console.error("Error in local model stream completion:", (e as Error).message);
				}
				resolve();
			});
		});
	
		return StreamResult.SUCCESS;
	}

	private handleStreamCompletion(request_id: string, outputStream: any, streamState: LocalModelStreamState): void {
		if (streamState.cancelled) {
			return;
		}
		
		// If we have text content that hasn't been sent as complete, send it now
		if (streamState.hasTextContent && !streamState.textStreamingComplete && streamState.textContent.length > 0) {
			this.streamingHelper.safeWriteToOutputStream(outputStream, 
				this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent));
		}
	}

	private processStreamingChunk(
		chunkNode: any, 
		request_id: string, 
		outputStream: any, 
		streamState: LocalModelStreamState,
		_originalRequest?: any
	): void {
		
		if (streamState.cancelled) {
			if (!streamState.cancelledMessageLogged) {
				streamState.cancelledMessageLogged = true;
			}
			return;
		}
		
		// Parse OpenAI Chat Completions streaming format
		// Format: {"id": "...", "object": "chat.completion.chunk", "choices": [{"delta": {...}, "index": 0}]}
		
		if (!chunkNode.choices || !Array.isArray(chunkNode.choices) || chunkNode.choices.length === 0) {
			return;
		}
		
		const choice = chunkNode.choices[0];
		const delta = choice.delta;
		
		if (!delta) {
			return;
		}
		
		const now = Date.now();
		if (streamState.firstDeltaAtMs < 0) streamState.firstDeltaAtMs = now;
		streamState.lastDeltaAtMs = now;
		streamState.deltaCount++;
		
		// Handle text content
		if (delta.content) {
			const contentDelta = delta.content;
			
			streamState.textContent += contentDelta;
			streamState.hasTextContent = true;
			streamState.userStreamingStarted = true;
			
			if (!this.streamingHelper.safeWriteToOutputStream(outputStream, 
				this.streamingHelper.createTextDeltaEvent(request_id, contentDelta))) {
				return;
			}
		}
		
		// Handle tool calls (function calling)
		if (delta.tool_calls && Array.isArray(delta.tool_calls)) {
			for (const toolCall of delta.tool_calls) {
				const toolCallIndex = toolCall.index !== undefined ? toolCall.index : 0;
				const toolCallId = toolCall.id || `call_${toolCallIndex}`;
				
				// Tool call started
				if (toolCall.function) {
					const functionData = toolCall.function;
					
					// Initialize or update function call tracking
					if (!streamState.parallelFunctionCalls.has(toolCallId)) {
						// New function call
						const functionName = functionData.name || '';
						
						// Complete text streaming before starting function call
						if (streamState.hasTextContent && !streamState.textStreamingComplete && streamState.userStreamingStarted) {
							if (!this.streamingHelper.safeWriteToOutputStream(outputStream, 
								this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent))) {
								return;
							}
							streamState.textStreamingComplete = true;
						}
						
						streamState.parallelFunctionCalls.set(toolCallId, {
							functionName: functionName,
							callId: toolCallId,
							functionArguments: functionData.arguments || '',
							argumentsComplete: false,
							functionCallCompletionSent: false
						});
						streamState.hasParallelFunctionCalls = true;
						streamState.hasFunctionCall = true;
					} else {
						// Continuing existing function call - accumulate arguments
						const functionCall = streamState.parallelFunctionCalls.get(toolCallId)!;
						const argsDelta = functionData.arguments || '';
						
						if (argsDelta) {
							functionCall.functionArguments += argsDelta;
							
							// Stream search_replace, run_console_cmd, run_terminal_cmd arguments as deltas
							if (functionCall.functionName === 'search_replace' || 
								functionCall.functionName === 'run_console_cmd' || 
								functionCall.functionName === 'run_terminal_cmd') {
								if (!this.streamingHelper.sendStreamingFunctionDelta(request_id, outputStream, 
									functionCall.functionName, toolCallId, argsDelta, streamState)) {
									streamState.cancelled = true;
									return;
								}
							}
						}
					}
				}
			}
		}
		
		// Check if this is the final chunk (finish_reason is set)
		if (choice.finish_reason) {
			// Complete any pending function calls
			for (const [callId, functionCall] of streamState.parallelFunctionCalls) {
				if (!functionCall.argumentsComplete) {
					functionCall.argumentsComplete = true;
					
					// Send function call completion
					if (!this.handleFunctionCallCompletion(request_id, outputStream, 
						functionCall.functionName, callId, functionCall.functionArguments, 
						streamState.originalRequest, streamState, functionCall.functionCallCompletionSent)) {
						return;
					}
					
					if (functionCall.functionName === 'search_replace' || 
						functionCall.functionName === 'run_console_cmd' || 
						functionCall.functionName === 'run_terminal_cmd') {
						functionCall.functionCallCompletionSent = true;
					}
				}
			}
			
			// Complete text if we have any
			if (streamState.hasTextContent && !streamState.textStreamingComplete) {
				if (this.streamingHelper.safeWriteToOutputStream(outputStream, 
					this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent))) {
					streamState.textStreamingComplete = true;
				}
			}
		}
	}

	private handleFunctionCallCompletion(
		request_id: string, 
		outputStream: any, 
		functionName: string, 
		callId: string, 
		functionArguments: string,
		originalRequest: any, 
		streamState: LocalModelStreamState, 
		functionCallCompletionSent: boolean
	): boolean {
		return this.streamingHelper.handleFunctionCallCompletion(request_id, outputStream, 
			functionName, callId, functionArguments, originalRequest, streamState, functionCallCompletionSent);
	}
}
