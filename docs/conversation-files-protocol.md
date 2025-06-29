# Conversation files protocol

## Core Structure

### conversation_log.json

Primary conversation storage containing chronologically ordered message objects.

**Message Schema:**
```json
{
  "id": "number",                    // ✓ Unique message identifier (auto-assigned)
  "role": "user|assistant",          // ✓ Message sender
  "content": "string|array",         // ✓ Message content or structured data
  "related_to": "number",           // ✓ Links to parent message ID
  
  // User message fields
  "original_query": "boolean",       // ✓ True for initial conversation query
  "procedural": "boolean",          // ✓ True to hide from conversation UI
  "cancelled": "boolean",           // ✓ True if message was cancelled
  
  // Assistant message fields  
  "response_id": "string",          // ✓ OpenAI reasoning models only (format: "resp_[hex]")
  "partial_content": "boolean",     // ✓ True for cancelled partial responses
  
  // Function call messages
  "function_call": {
    "name": "string",               // ✓ Function name ("read_file", "run_file")
    "arguments": "string",          // ✓ JSON string format (not object)
    "call_id": "string",           // ✓ Format: "call_[alphanumeric]" or "toolu_[alphanumeric]"
    "msg_id": "number"             // ✓ Matches message ID
  },
  "request_id": "string",          // ✓ Format: "req_[timestamp]_[number]" (verified: "req_1751138380544_6770")
  "source_function": "string",     // ✓ Source context (e.g. "edit_file")
  "modified_script": "string",     // ✓ Added to run_file, run_console_cmd, run_terminal_cmd, delete_file when accepted
  
  // Function output messages
  "type": "function_call_output",  // ✓ Marks function result
  "call_id": "string",            // ✓ Links to function_call.call_id
  "output": "string",             // ✓ Function execution result
  "procedural": "boolean",        // ✓ Function outputs can be marked procedural
  
  // read_file outputs only
  "start_line": "number",         // ✓ Starting line number of read range
  "end_line": "number",           // ✓ Ending line number of read range
  
  // edit_file outputs only
  "start_line": "number|object",  // ✓ Line parameters (often {} for non-range edits)
  "end_line": "number|object",    // ✓ Line parameters (often {} for non-range edits)  
  "insert_line": "number|object", // ✓ Insert position (often {} for non-insert edits)
  
  // Image/plot messages  
  "plots": ["string"],            // ✓ Plot names array (verified: ["plot_20250628_121954"])
  "plots_file": "string"          // ✓ Full path to plots JSON file (verified)
}
```

## Key Field Meanings

### Core Relationship Fields

- **`id`**: Auto-assigned unique message identifier, determines chronological order
- **`related_to`**: Creates parent-child relationships between messages
  - Assistant responses link to user messages that triggered them
  - Edit file contents (an assistant message) link to the edit_file function call they're in response to
  - Function call outputs link to function call messages  
  - Images/plots link to function calls that generated them
- **`request_id`**: Links messages to specific API requests for tracking and cancellation

### Message Flow Control

- **`procedural: true`**: Hides messages from conversation UI (internal state tracking)
- **`original_query: true`**: Marks the initial user message that started the conversation
- **`cancelled: true`**: Indicates user cancelled the message during processing

### Function Call Coordination

- **`call_id`**: ✓ Format "call_[alphanumeric]" or "toolu_[alphanumeric]" linking function calls to their outputs  
- **`msg_id`**: ✓ Always equals the parent message's `id` field (redundant but present)
- **`type: "function_call_output"`**: ✓ Marks messages containing function execution results
- **`request_id`**: ✓ Format "req_[timestamp]_[number]" for API request tracking

### Reasoning Model Support  

- **`response_id`**: ✓ OpenAI reasoning models only - identifier for chaining responses (format: "resp_[hex]")
- **`partial_content: true`**: Preserves content from cancelled streaming responses

## Folder Structure

**Base AI Directory:** `~/.rstudio/ai/`
- **`conversation_names.csv`**: Global conversation name mappings

**Individual Conversations:** `~/.rstudio/ai/conversations/conversation_N/`

### Core Files

- **`conversation_log.json`**: Primary message storage (required)
- **`conversation_vars.rds`**: R binary file storing conversation-specific variables

### File Management

- **`script_history.tsv`**: File creation/execution order tracking
- **`file_changes.json`**: Comprehensive file modification audit log  
- **`conversation_diffs.json`**: Edit diffs indexed by function call ID

### UI State Management

- **`message_buttons.csv`**: Accept/reject button click states for interactive commands
- **`attachments.csv`**: File attachment metadata with vector store references

### Background Processing

- **`summaries.json`**: Stored conversation summaries for memory management
- **`background_summarization.json`**: Async summarization state tracking

### Generated Content

- **`plots/`**: Directory containing plot data as base64-encoded JSON
  - **`plots_[message_id].json`**: Base64-encoded plot data keyed by timestamp
- **Temporary files**: Stream files and temporary processing files during execution

## Message Types & Patterns

### User Messages
```json
// Initial query
{"id": 1, "role": "user", "content": "...", "original_query": true}

// Follow-up
{"id": 3, "role": "user", "content": "...", "related_to": 2}

// Procedural (hidden)  
{"id": 5, "role": "user", "content": "Response pending...", "related_to": 4, "procedural": true}
```

### Assistant Messages
```json
// Text response (OpenAI reasoning model)  ✓ Verified response_id format
{"id": 2, "role": "assistant", "content": "...", "related_to": 1, "response_id": "resp_[hex]"}

// Text response (standard models)
{"id": 2, "role": "assistant", "content": "...", "related_to": 1}

// Function call  ✓ Verified complete structure
{"id": 6, "role": "assistant", "function_call": {"name": "read_file", "arguments": "{\"filename\":\"generate_normals.R\",...}", "call_id": "call_u37qyv1KHIk5gTNeIQRBbk1o", "msg_id": 6}, "related_to": 3, "request_id": "req_1751131447155_8766"}

// Function call with modified script  ✓ run_file specific
{"id": 10, "role": "assistant", "function_call": {"name": "run_file", "arguments": "{...}", "call_id": "call_fPiLNAA75aIVBro4kQXrn4Ex", "msg_id": 10}, "related_to": 3, "request_id": "req_1751131447155_8766", "modified_script": "..."}
```

### Function Outputs
```json
// read_file result  
{"id": 7, "type": "function_call_output", "call_id": "call_u37qyv1KHIk5gTNeIQRBbk1o", "output": "File: generate_normals.R\nEntire file content...", "related_to": 6, "start_line": 1, "end_line": 32}

// run_file result  ✓ Verified procedural format
{"id": 11, "type": "function_call_output", "call_id": "call_fPiLNAA75aIVBro4kQXrn4Ex", "output": "> cat(\"Generated 10 normal...\nGenerated 10 normal...", "related_to": 10, "procedural": true}

// run_console_cmd result  
{"id": 12, "type": "function_call_output", "call_id": "call_xyz", "output": "R console output...", "related_to": 11, "procedural": true}

// run_terminal_cmd result  
{"id": 13, "type": "function_call_output", "call_id": "call_abc", "output": "Terminal output...", "related_to": 12, "procedural": true}

// edit_file completion  
{"id": 8, "type": "function_call_output", "call_id": "...", "output": "Edit completed", "related_to": 4, "start_line": {}, "end_line": {}, "insert_line": {}}
```

### Special Messages  
```json
// Plot/image  
{"id": 9, "role": "user", "content": [{"type": "input_text", "text": "Generated plot:"}, {"type": "input_image", "image_url": "data:image/png;base64,..."}], "related_to": 4, "plots": ["plot_20240101_120000"], "plots_file": "/path/to/plots/plots_9.json"}

// Cancelled response  ✓ Verified structure
{"id": 2, "role": "assistant", "content": "partial...", "related_to": 1, "cancelled": true, "partial_content": true}
```

## Processing Rules

1. **Message Ordering**: Always sort by `id` field for chronological processing
2. **Relationship Tracking**: Use `related_to` to build message hierarchies  
3. **UI Filtering**: Exclude `procedural: true` messages from conversation display
4. **Function Coordination**: Match `call_id` between function calls and outputs
5. **Cancellation Handling**: Preserve `cancelled: true` messages for context
6. **Diff Attribution**: Use `related_to` field to link diffs to originating function calls
7. **File Deletion Reversion**: Files with `action: "remove"` are restored by recreating them with `previous_content` during revert operations

## File Schemas

### conversation_names.csv
```csv
conversation_id,name              // ✓ Both columns verified
361,"List Project Documentation Contents"  // ✓ Data structure verified
```

### script_history.tsv
```tsv
filename	order	conversation_index // ✓ All columns verified
```

### file_changes.json
```json
{
  "changes": [                      // ✓ Array structure verified
    {
      "id": 1,                      // ✓ Verified
      "conversation_id": 123,       // ✓ Verified
      "conversation_index": 1,      // ✓ Verified
      "timestamp": "2024-01-01 12:00:00", // ✓ Verified
      "action": "create|modify|remove", // ✓ Verified ("create", "remove")
      "file_path": "/path/to/file.R", // ✓ Verified
      "content": "new_file_content", // ✓ Verified (empty string for remove actions)
      "previous_content": "original_content",    // ✓ Verified (for modify/remove actions)
      "diff_type": "modify|prepend|append|replace|delete", // ✓ Verified ("replace", "append", "delete")
      "was_unsaved": false         // ✓ Verified
    }
  ]
}
```

### conversation_diffs.json  
```json
{
  "diffs": {                        // ✓ Object structure verified
    "[function_call_id]": {         // ✓ Function call ID keys verified ("5")
      "message_id": "123",          // ✓ Verified
      "conversation_index": 1,      // ✓ Verified
      "timestamp": "2024-01-01 12:00:00", // ✓ Verified
      "diff_data": [                // ✓ Array verified
        {
          "type": "added|deleted|unchanged", // ✓ Verified ("added", "deleted", "unchanged")
          "content": "line_content", // ✓ Verified
          "display_line": 15,       // ✓ Verified 
          "old_line": 10,           // ✓ Verified (as "NA" for added)
          "new_line": 15            // ✓ Verified
        }
      ],
      "old_content": "complete_old_file_content", // ✓ Verified (empty string)
      "new_content": "complete_new_file_content", // ✓ Verified
      "flags": {                    // ✓ Optional: Only present for special edit modes
        "is_start_edit": false,     // ✓ Verified  
        "is_end_edit": true,        // ✓ Verified (true in actual data)
        "is_insert_mode": false,    // ✓ Verified
        "is_line_range_mode": false, // ✓ Verified
        "start_line": {},           // ✓ Verified
        "end_line": {},             // ✓ Verified
        "insert_line": {}           // ✓ Verified
      }
    }
  }
}
```

### message_buttons.csv
```csv
message_id,buttons_run            // ✓ Both columns verified
123,TRUE                          // ✓ Data structure verified
```

### attachments.csv
```csv
timestamp,message_id,file_path,file_id,vector_store_id  // ✓ All columns verified
2025-06-28 09:50:43,1,~/Documents/Lotas/Rao/yc_demo_2/data/test.txt,file-RPhfFe1oTvDT4mfLGA2e2B,vs_68601d675aa081919a8c8e220836b587  // ✓ Data structure verified
```

### summaries.json
```json
{
  "summaries": {                    // ✓ Object structure verified
    "1": {                          // ✓ Query number as key verified
      "query_number": 10,           // ✓ Verified
      "timestamp": "2024-01-01 12:00:00", // ✓ Verified
      "summary_text": "Summary of conversation up to query 10..." // ✓ Verified
    }
  }
}
```

### background_summarization.json ✓
**Purpose**: Tracks active background summarization processes  
**When created**: During background conversation summarization (3+ queries trigger summarization of previous queries)  
**Lifecycle**: Created → Read multiple times → Deleted (with timestamped backup)  
```json
{
  "request_id": "summary_1751130604_99708",     // ✓ Unique summarization request ID
  "target_query": 2,                            // ✓ Query number being summarized  
  "stream_file": "/tmp/Rtmp2uUPow/bg_summary_summary_1751130604_99708.txt", // ✓ Temporary stream output file
  "process_id": 81854,                          // ✓ Background process ID
  "timestamp": "2025-01-01 00:00:00"           // ✓ Process start time (YYYY-MM-DD HH:MM:SS)
}
```
**Notes**: 
- File is automatically deleted after completion with backup saved as `background_summarization.json_backup_YYYYMMDD_HHMMSS`
- Stream file is in system temp directory and gets cleaned up separately

### plots/plots_[message_id].json
```json
{
  "plot_timestamp_name": "base64_encoded_plot_data" // ✓ Structure verified
}
```

## Integration Patterns

- **Message Relationships**: Use `related_to` field to link messages across files
- **Function Call Tracking**: Match `call_id` between function calls and outputs  
- **Diff Attribution**: `conversation_diffs.json` keys match function call IDs, not assistant message IDs
- **Button State**: `message_buttons.csv` tracks accept/reject clicks by message ID
- **File Attachments**: `attachments.csv` links files to conversations via message IDs and vector stores
- **Execution History**: `script_history.tsv` maintains file creation order across sessions
- **Variable Persistence**: `conversation_vars.rds` preserves R session state between conversation switches

## Diff Flags Behavior

The `flags` object in `conversation_diffs.json` appears only for **special edit modes**:

- **Present**: Start/end edits, insert mode, line range edits
  - `is_start_edit: true` - Edits at beginning of file
  - `is_end_edit: true` - Appends at end of file
  - `is_insert_mode: true` - Line insertion mode
  - `is_line_range_mode: true` - Range-based edits

- **Absent**: Regular edits, file creation, standard replacements
  - File creation (like entry "5") 
  - Normal content replacement (like entry "28")

This conditional presence allows the system to distinguish between AI-directed special edit operations and standard content modifications. 