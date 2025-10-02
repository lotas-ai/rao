"use strict";
/*---------------------------------------------------------------------------------------------
 *  Copyright (C) 2025 by Lotas Inc.
 *  Licensed under the AGPL-3.0 License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/
Object.defineProperty(exports, "__esModule", { value: true });
exports.StreamingProxyHelper = void 0;
class StreamingProxyHelper {
    /**
     * Safely write to output stream with error handling
     */
    safeWriteToOutputStream(outputStream, data, _requestId, streamState) {
        if (!outputStream) {
            return false;
        }
        try {
            outputStream.write(data);
            return true;
        }
        catch (e) {
            // Client disconnected - this is normal, don't log as error
            if (e.code === 'EPIPE' || e.code === 'ECONNRESET') {
                return false;
            }
            // Log write errors only once to prevent spam
            if (streamState && !streamState.writeErrorLogged) {
                console.error('Unexpected error writing to output stream:', e.message);
                streamState.writeErrorLogged = true;
            }
            return false;
        }
    }
    /**
     * Create a standard error event
     */
    createErrorEvent(requestId, errorMessage) {
        const event = {
            request_id: requestId,
            error: errorMessage,
            isComplete: true
        };
        return `data: ${JSON.stringify(event)}\n\n`;
    }
    /**
     * Create a text completion event
     */
    createTextCompleteEvent(requestId, content) {
        const event = {
            request_id: requestId,
            response: content,
            isComplete: true
        };
        return `data: ${JSON.stringify(event)}\n\n`;
    }
    /**
     * Create a text delta event
     */
    createTextDeltaEvent(requestId, delta) {
        const event = {
            request_id: requestId,
            delta,
            field: 'response',
            isComplete: false
        };
        return `data: ${JSON.stringify(event)}\n\n`;
    }
    /**
     * Create an end_turn event
     */
    createEndTurnEvent(requestId) {
        const event = {
            request_id: requestId,
            end_turn: true,
            isComplete: true
        };
        return `data: ${JSON.stringify(event)}\n\n`;
    }
    /**
     * Create a timeout event
     */
    createTimeoutEvent(requestId, serviceName, timeoutSeconds) {
        const errorMessage = `Stream timeout - no response from ${serviceName} for ${timeoutSeconds} seconds`;
        return this.createErrorEvent(requestId, errorMessage);
    }
    /**
     * Create web search call event
     */
    createWebSearchCallEvent(requestId, webSearchCallJson) {
        const event = {
            request_id: requestId,
            web_search_call: JSON.parse(webSearchCallJson),
            field: 'web_search_call',
            isComplete: false
        };
        return `data: ${JSON.stringify(event)}\n\n`;
    }
    /**
     * Create web search results event
     */
    createWebSearchResultsEvent(requestId, webSearchResultsJson) {
        const event = {
            request_id: requestId,
            web_search_results: JSON.parse(webSearchResultsJson),
            field: 'web_search_results',
            isComplete: false
        };
        return `data: ${JSON.stringify(event)}\n\n`;
    }
    /**
     * Create annotations event
     */
    createAnnotationsEvent(requestId, annotationsJson) {
        const event = {
            request_id: requestId,
            annotations: JSON.parse(annotationsJson),
            field: 'annotations',
            isComplete: false
        };
        return `data: ${JSON.stringify(event)}\n\n`;
    }
    /**
     * Create response ID event
     */
    createResponseIdEvent(requestId, responseId) {
        const event = {
            request_id: requestId,
            response_id: responseId,
            isComplete: false
        };
        return `data: ${JSON.stringify(event)}\n\n`;
    }
    /**
     * Send streaming function delta event
     */
    sendStreamingFunctionDelta(requestId, outputStream, functionName, callId, delta, streamState) {
        const event = {
            request_id: requestId,
            field: functionName,
            call_id: callId,
            delta,
            isComplete: false
        };
        return this.safeWriteToOutputStream(outputStream, `data: ${JSON.stringify(event)}\n\n`, requestId, streamState);
    }
    /**
     * Handle function call completion
     * This consolidates the repetitive completion logic found in both OpenAI and Anthropic services
     */
    handleFunctionCallCompletion(requestId, outputStream, functionName, callId, functionArguments, originalRequest, streamState, functionCallCompletionSent) {
        // Handle special function calls first
        if (functionName === 'end_turn') {
            return this.safeWriteToOutputStream(outputStream, this.createEndTurnEvent(requestId), requestId, streamState);
        }
        if (functionName === 'web_search') {
            return true; // Skip web_search - handled separately
        }
        // Handle streaming function calls - send completion event first for streaming functions
        if (functionName === 'search_replace') {
            if (!functionCallCompletionSent) {
                return this.sendStreamingFunctionCompletionInternal(requestId, outputStream, functionName, callId, functionArguments, originalRequest, streamState);
            }
        }
        else if (functionName === 'run_console_cmd' || functionName === 'run_terminal_cmd') {
            if (!functionCallCompletionSent) {
                return this.sendStreamingFunctionCompletionInternal(requestId, outputStream, functionName, callId, functionArguments, originalRequest, streamState);
            }
        }
        else {
            // Regular function call
            return this.sendFunctionCallEventInternal(requestId, outputStream, functionName, callId, functionArguments, originalRequest, streamState);
        }
        return true;
    }
    /**
     * Send streaming function completion event (internal method)
     */
    sendStreamingFunctionCompletionInternal(requestId, outputStream, fieldName, callId, _functionArguments, _originalRequest, streamState) {
        try {
            const event = {
                request_id: requestId,
                field: fieldName,
                call_id: callId,
                response: null, // Minimal completion event
                isComplete: true
            };
            const completeEvent = `data: ${JSON.stringify(event)}\n\n`;
            return this.safeWriteToOutputStream(outputStream, completeEvent, requestId, streamState);
        }
        catch (e) {
            console.error('Failed to serialize streaming function completion event:', e);
            throw new Error('Critical error: Failed to serialize streaming function completion event');
        }
    }
    /**
     * Send regular function call event (internal method)
     */
    sendFunctionCallEventInternal(requestId, outputStream, functionName, callId, functionArguments, _originalRequest, streamState) {
        try {
            const functionCall = {
                name: functionName,
                call_id: callId,
                arguments: functionArguments.length === 0 ? '{}' : functionArguments
            };
            const event = {
                request_id: requestId,
                isComplete: true,
                action: 'function_call',
                function_call: functionCall
            };
            const functionCallEvent = `data: ${JSON.stringify(event)}\n\n`;
            return this.safeWriteToOutputStream(outputStream, functionCallEvent, requestId, streamState);
        }
        catch (e) {
            console.error('Error creating function call event:', e);
            throw new Error('Critical error: Failed to serialize function call event');
        }
    }
}
exports.StreamingProxyHelper = StreamingProxyHelper;
//# sourceMappingURL=streamingProxyHelper.js.map