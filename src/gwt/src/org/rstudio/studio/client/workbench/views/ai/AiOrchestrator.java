/*
 * AiOrchestrator.java
 *
 * Copyright (C) 2025 by William Nickols
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

package org.rstudio.studio.client.workbench.views.ai;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsonUtils;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Timer;

import java.util.HashMap;
import java.util.Map;

import org.rstudio.core.client.Debug;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.workbench.views.ai.events.ShowAiEvent;
import com.google.gwt.core.client.Scheduler;

/**
 * Orchestrates AI operations using a flat architecture where each function call
 * is processed individually and Java controls the overall flow.
 */
public class AiOrchestrator
{
   // Status constants matching R-side constants
   private static final String AI_STATUS_DONE = "done";
   private static final String AI_STATUS_CONTINUE_SILENT = "continue_silent";
   private static final String AI_STATUS_CONTINUE_AND_DISPLAY = "continue_and_display";
   private static final String AI_STATUS_FUNCTION_CALL = "function_call";
   private static final String AI_STATUS_PENDING = "pending";
   private static final String AI_STATUS_ERROR = "error";
   
   private final AiServerOperations server_;
   private final AiPane aiPane_;
   private final EventBus eventBus_;
   private boolean isProcessing_ = false;
   private String currentRequestId_ = null;
   private Map<String, Integer> requestToUserMessageId_ = new HashMap<>();
   
   public AiOrchestrator(AiServerOperations server, AiPane aiPane, EventBus eventBus)
   {
      server_ = server;
      aiPane_ = aiPane;
      eventBus_ = eventBus;
   }
   
   /**
    * Starts a new AI search using the flat architecture.
    */
   public void startAiSearch(String query, String requestId)
   {
      // Set processing state and store request ID
      isProcessing_ = true;
      currentRequestId_ = requestId;
      
      // CRITICAL FIX: Clear cancellation state for new operations
      if (aiPane_ != null) {
         aiPane_.clearCancellationState();
      }
      
      // Switch to streaming view before starting conversation
      if (aiPane_ != null && aiPane_.getToolbars() != null && aiPane_.getToolbars().getViewManager() != null) {
         aiPane_.getToolbars().getViewManager().forceShowConversations();
      }
      
      // Immediately show user message in streaming panel
      if (aiPane_ != null && aiPane_.getStreamingPanel() != null) {
         aiPane_.getStreamingPanel().addUserMessage(query);
      }
      
      // Initialize conversation first
      initializeConversation(query, requestId);
   }
   
   /**
    * Initializes conversation with user query and then makes API call.
    */
   /**
    * Initializes conversation with user query following established patterns.
    */
   private void initializeConversation(String query, String requestId)
   {
      JSONObject params = new JSONObject();
      params.put("operation_type", new JSONString("initialize_conversation"));
      params.put("query", new JSONString(query));
      
      if (requestId != null) {
         params.put("request_id", new JSONString(requestId));
      }
      
      server_.processAiOperation(params.getJavaScriptObject(), new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject response)
         {
            // After initializing conversation, make the API call
            JSONObject responseObj = new JSONObject(response);
            String status = getString(responseObj, "status", "");
            if ("done".equals(status)) {
               JSONValue dataValue = responseObj.get("data");
               if (dataValue != null && dataValue.isObject() != null) {
                  JSONObject dataObj = dataValue.isObject();
                  Integer conversationIndex = getInteger(dataObj, "conversation_index", null);
                  if (conversationIndex == null) {
                     handleError("Initialize conversation response missing conversation_index");
                     return;
                  }
                  Integer userMessageId = getInteger(dataObj, "user_message_id", null);
                  
                  // Store the user message ID for this specific request
                  if (userMessageId != null && requestId != null) {
                     requestToUserMessageId_.put(requestId, userMessageId);
                  }
                  makeApiCall(conversationIndex, userMessageId, null, requestId);
               }
            } else {
               // Use central handler for all other status types
               handleOperationResult(status, responseObj);
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            handleError("Conversation initialization failed: " + error.getMessage());
         }
      });
   }
   
   /**
    * Makes an API call and processes the response following established patterns.
    */
   private void makeApiCall(Integer conversationIndex, Integer relatedToId, String model, String requestId)
   {
      // related_to_id is now required
      if (relatedToId == null) {
         handleError("[AiOrchestrator] related_to_id is required for makeApiCall and cannot be null");
         return;
      }
      
      JSONObject params = new JSONObject();
      params.put("operation_type", new JSONString("make_api_call"));
      params.put("related_to_id", new JSONNumber(relatedToId));
      
      if (conversationIndex != null) {
         params.put("conversation_index", new JSONNumber(conversationIndex));
      }
      if (model != null) {
         params.put("model", new JSONString(model));
      }
      if (requestId != null) {
         params.put("request_id", new JSONString(requestId));
      }
      
      server_.processAiOperation(params.getJavaScriptObject(), new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject response)
         {
            // Handle the response
            JSONObject responseObj = new JSONObject(response);
            handleOperationResult(getString(responseObj, "status", ""), responseObj);
         }
         
         @Override
         public void onError(ServerError error)
         {
            handleError("Make API call failed: " + error.getMessage());
         }
      });
   }
   
   /**
    * Processes a single function call.
    */
   private void processFunctionCall(JavaScriptObject functionCall, Integer relatedToId, Integer conversationIndex, String requestId)
   {
      // related_to_id is now required and cannot be null
      if (relatedToId == null) {
         handleError("related_to_id is required for processFunctionCall and cannot be null");
         return;
      }
      
      JSONObject params = new JSONObject();
      params.put("operation_type", new JSONString("process_function_call"));
      params.put("function_call", new JSONObject(functionCall));
      params.put("related_to_id", new JSONNumber(relatedToId));
      
      if (conversationIndex != null) {
         params.put("conversation_index", new JSONNumber(conversationIndex));
      }
      if (requestId != null) {
         params.put("request_id", new JSONString(requestId));
      }
      
      server_.processAiOperation(params.getJavaScriptObject(), new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject response)
         {
            JSONObject responseObj = new JSONObject(response);
            String status = getString(responseObj, "status", "");
            JSONValue dataValue = responseObj.get("data");
            if (dataValue != null && dataValue.isObject() != null) {
               JSONObject dataObj = dataValue.isObject();
               String commandType = getString(dataObj, "command_type", null);
               Integer messageId = getInteger(dataObj, "message_id", null);               
            }
            
            handleOperationResult(status, responseObj);
         }
         
         @Override
         public void onError(ServerError error)
         {
            handleError("Process function call failed: " + error.getMessage());
         }
      });
   }
   
   /**
    * Handles the result of an operation based on status.
    */
   private void handleOperationResult(String status, JSONObject responseObj)
   {            

      String preservedRequestId = null;
      switch (status) {
         case AI_STATUS_DONE:
            // Processing complete - check for result data
            handleSearchCompletion(responseObj);
            finishProcessing();
            break;
            
         case AI_STATUS_CONTINUE_SILENT:
            // CRITICAL FIX: Check for cancellation before continuing
            // If user cancelled during the operation, don't make another API call
            if (aiPane_ != null && aiPane_.isCancellationRequested()) {
               // User cancelled - stop processing
               finishProcessing();
               break;
            }
            
            // Continue silently without updating display - extract related_to_id and make another API call            
            JSONValue dataValue = responseObj.get("data");
            if (dataValue == null || dataValue.isObject() == null) {
               handleError("Continue response missing data object");
               break;
            }
            
            JSONObject dataObj = dataValue.isObject();
            Integer continueRelatedToId = getInteger(dataObj, "related_to_id", null);
            if (continueRelatedToId == null) {
               handleError("Continue response missing related_to_id");
               break;
            }
            
            // Extract conversation_index - it should always be provided in the response
            Integer continueConversationIndex = getInteger(dataObj, "conversation_index", null);
            if (continueConversationIndex == null) {
               handleError("Continue response missing conversation_index");
               break;
            }
            
            // Extract request_id from response data if available, fallback to currentRequestId_
            preservedRequestId = getString(dataObj, "request_id", currentRequestId_);
            
            makeContinueApiCall(continueConversationIndex, continueRelatedToId, preservedRequestId);
            break;
            
         case AI_STATUS_CONTINUE_AND_DISPLAY:
            // CRITICAL FIX: Check for cancellation before continuing
            // If user cancelled during the operation, don't make another API call
            if (aiPane_ != null && aiPane_.isCancellationRequested()) {
               // User cancelled - stop processing but still update display for partial results
               handleSearchCompletion(responseObj);
               finishProcessing();
               break;
            }
            
            // Update display like "done" but then make another API call
            handleSearchCompletion(responseObj);
            
            // Extract related_to_id from continue response data
            JSONValue displayDataValue = responseObj.get("data");
            if (displayDataValue == null || displayDataValue.isObject() == null) {
               handleError("Continue and display response missing data object");
               break;
            }
            
            JSONObject displayDataObj = displayDataValue.isObject();
            Integer displayRelatedToId = getInteger(displayDataObj, "related_to_id", null);
            if (displayRelatedToId == null) {
               handleError("Continue and display response missing related_to_id");
               break;
            }
            
            // Continue immediately after triggering display update - preserve original request ID
            Integer conversationIndex = getInteger(displayDataObj, "conversation_index", null);
            if (conversationIndex == null) {
               handleError("Continue and display response missing conversation_index");
               break;
            }

            // Extract request_id from response data if available, fallback to currentRequestId_
            preservedRequestId = getString(displayDataObj, "request_id", currentRequestId_);
            
            makeContinueApiCall(conversationIndex, displayRelatedToId, preservedRequestId);
            break;
                        
         case AI_STATUS_FUNCTION_CALL:
            // Process a function call
            JSONValue functionCallValue = responseObj.get("function_call");
            if (functionCallValue != null && functionCallValue.isObject() != null) {
               JSONObject functionCallDataObj = responseObj.get("data") != null ? responseObj.get("data").isObject() : null;
               Integer relatedToId = functionCallDataObj != null ? getInteger(functionCallDataObj, "related_to_id", null) : null;
               if (relatedToId == null) {
                  // Fall back to the user message ID for this request
                  relatedToId = requestToUserMessageId_.get(currentRequestId_);
                  if (relatedToId == null) {
                     handleError("Function call response missing related_to_id and no fallback user message ID available for request: " + currentRequestId_);
                     break;
                  }
               }
               Integer functionCallConversationIndex = functionCallDataObj != null ? getInteger(functionCallDataObj, "conversation_index", null) : null;
               if (functionCallConversationIndex == null) {
                  handleError("Function call response missing conversation_index");
                  break;
               }
               
               processFunctionCall(functionCallValue.isObject().getJavaScriptObject(), relatedToId, functionCallConversationIndex, currentRequestId_);
            } else {
               handleError("Function call status received but no function call data");
            }
            break;
            
         case AI_STATUS_PENDING:
            // User interaction required - update display to show pending command, then finish
            handleSearchCompletion(responseObj);
            finishProcessing();
            break;
            
         case AI_STATUS_ERROR:
            // Error occurred
            String errorMsg = getString(responseObj, "error", "Unknown error");
            handleError(errorMsg);
            break;
            
         default:
            Debug.log("AiOrchestrator received unknown status: '" + status + "'");
            handleError("Unknown status: " + status);
            break;
      }
   }
   
   /**
    * Handles search completion with result data.
    */
   private void handleSearchCompletion(JSONObject responseObj)
   {
      try {
         JSONValue dataValue = responseObj.get("data");
         if (dataValue != null && dataValue.isObject() != null) {
            JSONObject dataObj = dataValue.isObject();
            
            if (aiPane_ != null) {
               // CRITICAL FIX: Check if we're in streaming mode - if so, DON'T navigate
               boolean isInStreamingMode = false;
               if (aiPane_.getToolbars() != null && aiPane_.getToolbars().getViewManager() != null) {
                  isInStreamingMode = !aiPane_.getToolbars().getViewManager().isInApiMode();
               }
               
               if (isInStreamingMode) {
                  // In streaming mode, the DOM is already updated via streaming events
                  // No navigation needed - everything is handled in-place
               }
            }
         } else {
            Debug.log("AiOrchestrator handleSearchCompletion - no data or data is not object");
         }
      } catch (Exception e) {
         Debug.log("AiOrchestrator handleSearchCompletion - exception: " + e.getMessage());
      }
   }
   
   /**
    * Finishes processing and cleans up.
    */
   private void finishProcessing()
   {
      // Clean up the request mapping
      if (currentRequestId_ != null) {
         requestToUserMessageId_.remove(currentRequestId_);
      }
      
      isProcessing_ = false;
      currentRequestId_ = null;
      
      // Hide thinking message in both iframe and streaming modes
      if (aiPane_ != null) {
         // Check if we're in streaming mode
         boolean isInStreamingMode = false;
         if (aiPane_.getToolbars() != null && aiPane_.getToolbars().getViewManager() != null) {
            isInStreamingMode = !aiPane_.getToolbars().getViewManager().isInApiMode();
         }
         
         if (isInStreamingMode && aiPane_.getStreamingPanel() != null) {
            // Hide thinking message in streaming panel
            aiPane_.getStreamingPanel().hideThinkingMessage();
         } else if (aiPane_.getSearch() != null) {
            // Hide thinking message in iframe
            aiPane_.getSearch().hideThinkingMessage(aiPane_);
         }
      }
   }
   
   /**
    * Handles errors during processing.
    */
   private void handleError(String errorMessage)
   {
      // Clean up the request mapping
      if (currentRequestId_ != null) {
         requestToUserMessageId_.remove(currentRequestId_);
      }
      
      isProcessing_ = false;
      currentRequestId_ = null;
      
      // Hide thinking message in both iframe and streaming modes
      if (aiPane_ != null) {
         // Check if we're in streaming mode
         boolean isInStreamingMode = false;
         if (aiPane_.getToolbars() != null && aiPane_.getToolbars().getViewManager() != null) {
            isInStreamingMode = !aiPane_.getToolbars().getViewManager().isInApiMode();
         }
         
         if (isInStreamingMode && aiPane_.getStreamingPanel() != null) {
            // Hide thinking message in streaming panel
            aiPane_.getStreamingPanel().hideThinkingMessage();
         } else if (aiPane_.getSearch() != null) {
            // Hide thinking message in iframe
            aiPane_.getSearch().hideThinkingMessage(aiPane_);
         }
      }
   }
   
   /**
    * Creates a JavaScript object containing a string value.
    */
   private native JavaScriptObject createStringObject(String value) /*-{
      return { "content": value };
   }-*/;
   
   /**
    * Utility methods for extracting values from JSON objects.
    */
   private String getString(JSONObject obj, String key, String defaultValue)
   {
      JSONValue value = obj.get(key);
      if (value != null && value.isString() != null) {
         return value.isString().stringValue();
      }
      // Handle case where R returns character vector as array
      if (value != null && value.isArray() != null) {
         if (value.isArray().size() > 0) {
            JSONValue firstElement = value.isArray().get(0);
            if (firstElement != null && firstElement.isString() != null) {
               return firstElement.isString().stringValue();
            }
         }
      }
      return defaultValue;
   }
   
   private boolean getBoolean(JSONObject obj, String key, boolean defaultValue)
   {
      JSONValue value = obj.get(key);
      if (value != null && value.isBoolean() != null) {
         return value.isBoolean().booleanValue();
      }
      // Handle case where R returns logical vector as array
      if (value != null && value.isArray() != null) {
         if (value.isArray().size() > 0) {
            JSONValue firstElement = value.isArray().get(0);
            if (firstElement != null && firstElement.isBoolean() != null) {
               return firstElement.isBoolean().booleanValue();
            }
         }
      }
      return defaultValue;
   }
   
   private Integer getInteger(JSONObject obj, String key, Integer defaultValue)
   {
      JSONValue value = obj.get(key);
      if (value != null && value.isNumber() != null) {
         return (int) value.isNumber().doubleValue();
      }
      // Handle case where R returns numeric vector as array
      if (value != null && value.isArray() != null) {
         if (value.isArray().size() > 0) {
            JSONValue firstElement = value.isArray().get(0);
            if (firstElement != null && firstElement.isNumber() != null) {
               return (int) firstElement.isNumber().doubleValue();
            }
         }
      }
      return defaultValue;
   }
   
   /**
    * Checks if currently processing.
    */
   public boolean isProcessing()
   {
      return isProcessing_;
   }
   
   /**
    * Cancels current processing.
    */
   public void cancel()
   {
      if (isProcessing_) {
         finishProcessing();
      }
   }
   
   /**
    * Continues a conversation by making an API call with the current conversation state.
    * Used when finalize_console_command returns "continue" status.
    */
   public void continueConversation(Integer relatedToId, Integer conversationIndex, String requestId)
   {
      if (conversationIndex == null) {
         handleError("[AiOrchestrator] conversationIndex is required but was null");
         return;
      }
      
      if (relatedToId == null) {
         handleError("[AiOrchestrator] related_to_id is required but was null");
         return;
      }
      
      // Set processing state
      isProcessing_ = true;
      
      makeContinueApiCall(conversationIndex, relatedToId, requestId);
   }
   
   /**
    * Makes an API call for continuing a conversation, preserving the relatedToId.
    */
   private void makeContinueApiCall(Integer conversationIndex, Integer relatedToId, String requestId)
   {
      // related_to_id is now required
      if (relatedToId == null) {
         handleError("[AiOrchestrator] related_to_id is required for makeContinueApiCall and cannot be null");
         return;
      }
      
      // Build parameters object following established patterns
      JSONObject params = new JSONObject();
      params.put("operation_type", new JSONString("make_api_call"));
      params.put("related_to_id", new JSONNumber(relatedToId));
      
      if (conversationIndex != null) {
         params.put("conversation_index", new JSONNumber(conversationIndex));
      }
      if (requestId != null && !requestId.trim().isEmpty()) {
         params.put("request_id", new JSONString(requestId));
      } else {
         Debug.log("[AiOrchestrator] WARNING: request_id is null or empty!");
      }
      
      // Make server call following established patterns
      server_.processAiOperation(params.getJavaScriptObject(), new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject response)
         {
            JSONObject responseObj = new JSONObject(response);
            String status = getString(responseObj, "status", "");
            
            // Handle all statuses through the central handler
            handleOperationResult(status, responseObj);
         }
         
         @Override
         public void onError(ServerError error)
         {
            handleError("Continue API call failed: " + error.getMessage());
         }
      });
   }
} 