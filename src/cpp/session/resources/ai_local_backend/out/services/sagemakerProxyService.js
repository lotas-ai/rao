"use strict";
/*---------------------------------------------------------------------------------------------
 * Copyright (c) Lotas Inc. All rights reserved.
 * Licensed under the MIT License.
 *--------------------------------------------------------------------------------------------*/
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.SagemakerProxyService = void 0;
const streamingProxyHelper_js_1 = require("./streamingProxyHelper.js");
var StreamResult;
(function (StreamResult) {
    StreamResult["SUCCESS"] = "SUCCESS";
})(StreamResult || (StreamResult = {}));
class SagemakerProxyService {
    constructor() {
        this.streamingHelper = new streamingProxyHelper_js_1.StreamingProxyHelper();
    }
    /**
     * Process streaming requests with direct OutputStream callback
     * Used by the /ai/query endpoint for unified streaming
     */
    async processStreamingResponsesWithCallback(requestBody, user, originalHeaders, request_id, outputStream, originalRequest) {
        await this.processStreamingResponsesWithCallbackInternal(requestBody, user, originalHeaders, request_id, outputStream, originalRequest);
    }
    /**
     * Internal method with original request parameter for retry logic
     */
    async processStreamingResponsesWithCallbackInternal(requestBody, _user, _originalHeaders, request_id, outputStream, originalRequest) {
        // Parse the request body - it's already in the correct format from SessionAiApiService
        const requestBodyJson = JSON.parse(requestBody);
        // Get SageMaker configuration from BYOK keys
        const sagemakerConfig = requestBodyJson.byok_keys?.sagemaker;
        const endpointName = sagemakerConfig?.endpointName;
        const region = sagemakerConfig?.region || 'us-east-1';
        if (!endpointName) {
            throw new Error('SageMaker endpoint name not configured in BYOK settings.');
        }
        // Get AWS credentials from request body BYOK keys
        const awsCredentials = requestBodyJson.byok_keys?.aws;
        if (!awsCredentials || !awsCredentials.accessKeyId || !awsCredentials.secretAccessKey) {
            throw new Error('AWS credentials not found in request. Please ensure AWS credentials are properly configured.');
        }
        // Model is available in requestBodyJson.model if needed for future use
        // Restore standard inactivity timeout logic (30s)
        const disableInactivityTimeout = false;
        const inactivityTimeoutMs = 30000;
        const sagemakerRequest = this.convertToSagemakerFormat(requestBodyJson);
        // Initialize AWS SDK with stored credentials
        const AWS = await Promise.resolve().then(() => __importStar(require('@aws-sdk/client-sagemaker-runtime')));
        const client = new AWS.SageMakerRuntimeClient({
            region,
            credentials: {
                accessKeyId: awsCredentials.accessKeyId,
                secretAccessKey: awsCredentials.secretAccessKey
            }
        });
        // Enhanced streaming state
        const streamState = {
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
            functionCallCompletionSent: false
        };
        // Track last stream event time for timeout policy
        let lastStreamEventTime = Date.now();
        let sseBuffer = ''; // Buffer for incomplete SSE chunks
        try {
            const command = new AWS.InvokeEndpointWithResponseStreamCommand({
                EndpointName: endpointName,
                Body: JSON.stringify(sagemakerRequest),
                ContentType: 'application/json'
            });
            const response = await client.send(command);
            if (!response.Body) {
                throw new Error('No response stream available from SageMaker');
            }
            // Use promise to block until streaming completes
            await new Promise((resolve, reject) => {
                const timeoutInterval = setInterval(() => {
                    const timeSinceLastEvent = Date.now() - lastStreamEventTime;
                    if (!disableInactivityTimeout && timeSinceLastEvent > inactivityTimeoutMs) {
                        const timeoutSeconds = Math.floor(inactivityTimeoutMs / 1000);
                        this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTimeoutEvent(request_id, "SageMaker", timeoutSeconds));
                        clearInterval(timeoutInterval);
                        resolve();
                    }
                }, 1000);
                // Process streaming response
                (async () => {
                    try {
                        for await (const event of response.Body) {
                            // Check for cancellation
                            if (streamState.cancelled) {
                                clearInterval(timeoutInterval);
                                resolve();
                                return;
                            }
                            // Compute timing metrics
                            const nowMs = Date.now();
                            lastStreamEventTime = nowMs;
                            if (event.PayloadPart?.Bytes) {
                                const chunk = new TextDecoder().decode(event.PayloadPart.Bytes);
                                sseBuffer += chunk;
                                while (sseBuffer.includes('\n\n')) {
                                    const eventEnd = sseBuffer.indexOf('\n\n');
                                    const eventBlock = sseBuffer.substring(0, eventEnd);
                                    sseBuffer = sseBuffer.substring(eventEnd + 2);
                                    if (eventBlock.trim()) {
                                        const lines = eventBlock.split('\n');
                                        let eventData = null;
                                        for (const line of lines) {
                                            if (line.startsWith('data: ')) {
                                                eventData = line.substring(6).trim();
                                            }
                                        }
                                        if (eventData && eventData !== '[DONE]') {
                                            try {
                                                const jsonData = JSON.parse(eventData);
                                                // Process the clean JSON (similar to OpenAI processing)
                                                this.processStreamingChunk(jsonData, request_id, outputStream, streamState, originalRequest);
                                            }
                                            catch (e) {
                                                // Skip malformed JSON
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        clearInterval(timeoutInterval);
                        // Process any remaining content when stream completes
                        try {
                            this.handleStreamCompletion(request_id, outputStream, streamState);
                        }
                        catch (e) {
                            console.error("Error in SageMaker stream completion:", e.message);
                        }
                        resolve();
                    }
                    catch (error) {
                        clearInterval(timeoutInterval);
                        // Handle cancellation gracefully without errors
                        let isCancellation = false;
                        const errorMessage = error.message;
                        // Detect cancellation scenarios
                        if (errorMessage &&
                            (errorMessage.includes("Connection reset") ||
                                errorMessage.includes("Connection closed") ||
                                errorMessage.includes("cancelled"))) {
                            isCancellation = true;
                        }
                        if (isCancellation) {
                            // Cancellation is normal - just complete the stream silently
                            streamState.cancelled = true;
                            resolve();
                            return;
                        }
                        // Only log and handle non-cancellation errors
                        try {
                            let finalErrorMessage = "SageMaker stream error: " + errorMessage;
                            // Handle specific AWS/SageMaker error types
                            if (errorMessage.includes('ValidationException')) {
                                finalErrorMessage = 'Invalid request to SageMaker endpoint';
                            }
                            else if (errorMessage.includes('ModelError')) {
                                finalErrorMessage = 'SageMaker model error - check model configuration';
                            }
                            else if (errorMessage.includes('ServiceUnavailable')) {
                                finalErrorMessage = 'SageMaker endpoint temporarily unavailable';
                            }
                            else if (errorMessage.includes('ThrottlingException')) {
                                finalErrorMessage = 'SageMaker request throttled - too many requests';
                            }
                            this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createErrorEvent(request_id, finalErrorMessage));
                        }
                        catch (e) {
                            console.error("Could not send error to client:", e.message);
                        }
                        reject(error);
                    }
                })();
            });
        }
        catch (error) {
            // Handle AWS SDK and SageMaker-specific errors
            let errorMessage = error.message || 'Unknown SageMaker error';
            if (error.name === 'ValidationException') {
                errorMessage = 'SageMaker endpoint validation failed - check configuration';
            }
            else if (error.name === 'ResourceNotFound') {
                errorMessage = 'SageMaker endpoint not found - check endpoint name and region';
            }
            else if (error.name === 'AccessDeniedException') {
                errorMessage = 'Access denied to SageMaker endpoint - check AWS credentials and permissions';
            }
            else if (error.name === 'ThrottlingException') {
                errorMessage = 'SageMaker requests are being throttled - reduce request frequency';
            }
            else if (error.name === 'ServiceUnavailableException') {
                errorMessage = 'SageMaker service temporarily unavailable';
            }
            this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createErrorEvent(request_id, errorMessage));
        }
        return StreamResult.SUCCESS;
    }
    convertToSagemakerFormat(openaiRequest) {
        // The request is already in OpenAI ChatCompletion format from callSagemakerStreaming
        // Just pass it through with any necessary adjustments
        const result = {
            model: openaiRequest.model,
            messages: openaiRequest.messages,
            max_tokens: openaiRequest.max_tokens,
            temperature: openaiRequest.temperature,
            stream: true
        };
        // Add tools if they exist
        if (openaiRequest.tools && openaiRequest.tools.length > 0) {
            result.tools = openaiRequest.tools;
            result.tool_choice = openaiRequest.tool_choice || "auto";
        }
        return result;
    }
    /**
     * Handle stream completion - send any remaining content
     */
    handleStreamCompletion(request_id, outputStream, streamState) {
        // Check for cancellation first - if cancelled, don't send any completion events
        if (streamState.cancelled) {
            return;
        }
        // If we have text content that hasn't been sent as complete, send it now
        if (streamState.hasTextContent && !streamState.textStreamingComplete && streamState.textContent.length > 0) {
            this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent));
        }
    }
    /**
     * Process individual streaming chunks and send appropriate SSE events
     * Based on OpenAI chunk processing but adapted for SageMaker OpenAI-compatible format
     */
    processStreamingChunk(chunkNode, request_id, outputStream, streamState, _originalRequest) {
        // Check for cancellation first - if cancelled, don't process any chunks
        if (streamState.cancelled) {
            // Only log once to avoid spam
            if (!streamState.cancelledMessageLogged) {
                streamState.cancelledMessageLogged = true;
            }
            return;
        }
        // Process OpenAI-compatible streaming response from SageMaker
        if (chunkNode.choices && chunkNode.choices.length > 0) {
            const choice = chunkNode.choices[0];
            if (choice.delta) {
                // Handle content streaming
                if (choice.delta.content) {
                    // Record delta timing
                    const now = Date.now();
                    if (streamState.firstDeltaAtMs < 0)
                        streamState.firstDeltaAtMs = now;
                    streamState.lastDeltaAtMs = now;
                    streamState.deltaCount++;
                    const delta = choice.delta.content;
                    // Add text to accumulated buffer
                    streamState.textContent += delta;
                    streamState.hasTextContent = true;
                    streamState.userStreamingStarted = true;
                    const textDeltaEvent = this.streamingHelper.createTextDeltaEvent(request_id, delta);
                    if (!this.streamingHelper.safeWriteToOutputStream(outputStream, textDeltaEvent)) {
                        streamState.cancelled = true;
                        return;
                    }
                }
                // Handle tool calls streaming
                if (choice.delta.tool_calls) {
                    const now = Date.now();
                    if (streamState.firstDeltaAtMs < 0)
                        streamState.firstDeltaAtMs = now;
                    streamState.lastDeltaAtMs = now;
                    streamState.deltaCount++;
                    for (const toolCall of choice.delta.tool_calls) {
                        const index = toolCall.index || 0;
                        const callKey = `call_${index}`;
                        // Create entry if it doesn't exist (first chunk will have the ID)
                        if (!streamState.parallelFunctionCalls.has(callKey)) {
                            streamState.parallelFunctionCalls.set(callKey, {
                                functionName: '',
                                callId: toolCall.id || callKey, // Store actual ID when available
                                functionArguments: '',
                                argumentsComplete: false,
                                functionCallCompletionSent: false
                            });
                            streamState.hasParallelFunctionCalls = true;
                        }
                        const functionCall = streamState.parallelFunctionCalls.get(callKey);
                        if (toolCall.function) {
                            if (toolCall.function.name) {
                                functionCall.functionName += toolCall.function.name;
                            }
                            if (toolCall.function.arguments) {
                                const argDelta = toolCall.function.arguments;
                                functionCall.functionArguments += argDelta;
                                // Stream function arguments for specific functions
                                if (functionCall.functionName === 'search_replace' ||
                                    functionCall.functionName === 'run_console_cmd' ||
                                    functionCall.functionName === 'run_terminal_cmd') {
                                    if (!this.streamingHelper.sendStreamingFunctionDelta(request_id, outputStream, functionCall.functionName, functionCall.callId, argDelta, streamState)) {
                                        streamState.cancelled = true;
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Handle finish reason
            if (choice.finish_reason) {
                if (choice.finish_reason === 'tool_calls' && streamState.hasParallelFunctionCalls) {
                    // Complete text first if needed
                    if (!this.completeTextStreamingIfNeeded(request_id, outputStream, streamState.textContent, streamState.hasTextContent, streamState.textStreamingComplete, streamState.userStreamingStarted, streamState)) {
                        return;
                    }
                    streamState.textStreamingComplete = true;
                    // Complete all function calls
                    for (const [, functionCall] of streamState.parallelFunctionCalls) {
                        if (!functionCall.functionCallCompletionSent && functionCall.functionName) {
                            if (!this.handleFunctionCallCompletion(request_id, outputStream, functionCall.functionName, functionCall.callId, functionCall.functionArguments, streamState.originalRequest, streamState, functionCall.functionCallCompletionSent)) {
                                return;
                            }
                            if (functionCall.functionName === 'search_replace' ||
                                functionCall.functionName === 'run_console_cmd' ||
                                functionCall.functionName === 'run_terminal_cmd') {
                                functionCall.functionCallCompletionSent = true;
                            }
                        }
                    }
                    streamState.hasFunctionCall = true;
                }
                else if (choice.finish_reason === 'stop') {
                    // Response completed - only send completion if we haven't handled function call
                    if (streamState.hasTextContent && !streamState.hasFunctionCall && !streamState.textStreamingComplete) {
                        // Pure text response - complete it now
                        if (this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent))) {
                            streamState.textStreamingComplete = true;
                        }
                    }
                }
            }
        }
        else {
            // Handle chunks without choices field (if any)
            console.warn("SageMaker chunk without 'choices' field:", JSON.stringify(chunkNode));
        }
    }
    /**
     * Handle function call completion with all the version-specific logic
     * This consolidates the repetitive completion logic found in both OpenAI and Anthropic services
     */
    handleFunctionCallCompletion(request_id, outputStream, functionName, callId, functionArguments, originalRequest, streamState, functionCallCompletionSent) {
        // Use centralized helper for function call completion (matches OpenAI/Anthropic exactly)
        return this.streamingHelper.handleFunctionCallCompletion(request_id, outputStream, functionName, callId, functionArguments, originalRequest, streamState, functionCallCompletionSent);
    }
    /**
     * Complete text streaming if needed
     */
    completeTextStreamingIfNeeded(request_id, outputStream, textContent, hasTextContent, textStreamingComplete, userStreamingStarted, _streamState) {
        if (hasTextContent && !textStreamingComplete && userStreamingStarted) {
            if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, textContent))) {
                return false;
            }
        }
        return true;
    }
}
exports.SagemakerProxyService = SagemakerProxyService;
//# sourceMappingURL=sagemakerProxyService.js.map