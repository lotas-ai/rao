"use strict";
/*---------------------------------------------------------------------------------------------
 * Copyright (c) Lotas Inc. All rights reserved.
 * Licensed under the MIT License.
 *--------------------------------------------------------------------------------------------*/
Object.defineProperty(exports, "__esModule", { value: true });
exports.StreamingService = void 0;
class StreamingService {
    constructor() {
    }
    /**
     * Send error event - matches SseErrorEvent format
     * Format: {"request_id":"req_123","error":"Error message","isComplete":true}
     */
    sendErrorEvent(onData, request_id, errorMessage) {
        const event = {
            type: 'error',
            request_id: request_id,
            error: { message: errorMessage, type: 'error', details: {} },
            isComplete: true
        };
        onData(event);
    }
    /**
     * Send end_turn event - matches SseEndTurnEvent format
     * Format: {"request_id":"req_123","end_turn":true,"isComplete":true}
     */
    sendEndTurnEvent(onData, request_id) {
        const event = {
            type: 'end_turn',
            request_id: request_id,
            end_turn: true,
            isComplete: true
        };
        onData(event);
    }
    /**
     * Send complete event - matches SseTextEvent format and custom field format
     * For response field: {"request_id":"req_123","response":"Complete text","isComplete":true}
     * For other fields: {"request_id":"req_123","field_name":"value","isComplete":true}
     */
    sendCompleteEvent(onData, request_id, field, value) {
        const event = {
            request_id: request_id,
            isComplete: true
        };
        // Handle special case for action field (function calls)
        if (field === 'action') {
            // Parse the JSON value for function calls
            try {
                const parsedAction = JSON.parse(value);
                Object.assign(event, parsedAction);
            }
            catch (e) {
                // If parsing fails, treat as regular field
                event[field] = value;
            }
        }
        else {
            // Regular field assignment
            event[field] = value;
        }
        onData(event);
    }
    /**
     * Send delta event - matches SseDeltaEvent format
     * Format: {"request_id":"req_123","delta":"partial text","field":"response","isComplete":false}
     */
    sendDeltaEvent(onData, request_id, field, delta) {
        const event = {
            request_id: request_id,
            delta: delta,
            field: field,
            isComplete: false
        };
        onData(event);
    }
}
exports.StreamingService = StreamingService;
//# sourceMappingURL=streamingService.js.map