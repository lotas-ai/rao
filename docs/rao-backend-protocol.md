# Rao Backend Communication Protocol

## Primary AI Endpoints

### POST /ai/query

Main AI query endpoint with streaming SSE responses.

**Request Structure:**
```json
{
  "request_type": "ai_api_call|generate_conversation_name|summarize_conversation",
  "conversation": [/* array of message objects */],
  "provider": "openai|anthropic",
  "model": "string",
  "request_id": "string",
  "client_version": "string",
  "symbols_note": {
    "direct_context": [
      {
        "type": "file|directory",
        "name": "string",
        "path": "string",
        "content": "string|array",  // For files
        "contents": ["string"],     // For directories
        "start_line": "number",     // Optional line range
        "end_line": "number",
        "symbols": [/* symbol objects */]
      }
    ],
    "keywords": [{"name": "string", "type": "string"}],
    "environment_variables": {
      "Data": [{"name": "string", "description": "string"}],
      "Function": [{"name": "string", "description": "string"}],
      "Value": [{"name": "string", "description": "string"}]
    },
    "open_files": [
      {
        "id": "string",
        "path": "string",
        "type": "string",
        "dirty": "boolean",
        "name": "string",
        "minutes_since_last_update": "number",
        "is_active": "boolean"
      }
    ],
    "attached_images": [
      {
        "filename": "string",
        "original_path": "string",
        "local_path": "string",
        "mime_type": "string",
        "base64_data": "string",
        "timestamp": "string"
      }
    ]
  },
  "auth": {
    "api_key": "string"
  },
  "user_os_version": "string",
  "user_workspace_path": "string",
  "user_shell": "string",
  "last_function_was_edit_file": "boolean",
  
  // Optional fields
  "attachments": [
    {
      "file_path": "string",
      "file_name": "string",
      "file_id": "string",
      "vector_store_id": "string",
      "timestamp": "string",
      "message_id": "string"
    }
  ],
  "has_attachments": "boolean",
  "previous_response_id": "string",
  "target_query_number": "number",         // For summarization
  "previous_summary": {
    "query_number": "number",
    "timestamp": "string",
    "summary_text": "string"
  },
  "function_call_depth": "number",
}
```

**Response Format (SSE):**
```
data: {"requestId": "string", "delta": "text_chunk", "field": "response", "isComplete": false}
data: {"requestId": "string", "action": "function_call", "function_call": {...}}
data: {"requestId": "string", "response": "full_text", "isComplete": true}
data: {"requestId": "string", "end_turn": true, "isComplete": true}
data: {"requestId": "string", "conversation_name": "string", "isComplete": true}
data: {"requestId": "string", "error": {...}}
data: {"requestId": "string", "response_id": "string", "field":"response_id","isComplete":false}
```

### POST /ai/cancel

Cancel an active streaming request.

**Request:**
```
POST /ai/cancel?requestId=string
```

**Response:**
```json
{
  "message": "Query cancelled successfully",
  "request_id": "string"
}
```

## Response Event Types

- **Delta Events**: Stream text content with `delta` field plus `requestId` and `field`
- **Function Call Events**: `action: "function_call"` with `function_call` object
- **Completion Events**: `isComplete: true` marks message completion
- **End Turn Events**: `end_turn: true` signals conversation turn completion
- **Conversation Name Events**: `conversation_name` field for name generation
- **Error Events**: Structured `error` object with user-facing message
- **Response ID Events**: `response_id` for reasoning model chaining

## Final Response Assembly

After processing streaming events:

```json
{
  "using_backend": true,
  "response": "string",                    // Text content
  "conversation_name": "string",           // For name generation
  "function_call": {...},                  // Function call data
  "error": {...},                          // Error information
  "response_id": "string",                 // For reasoning models
  "end_turn": "boolean",                   // Turn completion flag from Anthropic
  "action": "string",                     // Action type for function calls
  "assistant_message_id": "number",        // Generated message ID
  "message": "string"                      // Simplified error message
}
```

## Error Handling

Structured error responses with types and actions:

```json
{
  "error": {
    "error_type": "SUBSCRIPTION_LIMIT_REACHED|TRIAL_EXPIRED|PAYMENT_ACTION_REQUIRED|OVERAGE_PAYMENT_FAILED|...",
    "error_message": "string",
    "user_message": "string",
    "action_required": "enable_usage_billing|subscribe|payment_action_required|retry_payment|...",
    "subscription_status": "string",
    "can_process_queries": "boolean",
    "is_complete": "boolean",
    "complete": "boolean",               // Additional completion flag
    "http_status": "number"              // HTTP status code
  }
}
```