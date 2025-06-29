/*
 * AiPaneResponses.java
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

import org.rstudio.core.client.Point;
import org.rstudio.core.client.regex.Pattern;
import org.rstudio.core.client.regex.Match;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.studio.client.workbench.views.ai.widgets.AiStreamingPanel;
import org.rstudio.studio.client.workbench.views.ai.widgets.AiConsoleWidget;
import org.rstudio.core.client.Debug;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONValue;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.event.dom.client.LoadEvent;
import com.google.gwt.event.shared.HandlerRegistration;

/**
 * Handles AI pane responses and related functionality
 */
public class AiPaneResponses
{
   public AiPaneResponses(AiPane pane, AiServerOperations server, GlobalDisplay globalDisplay, AiOrchestrator orchestrator)
   {
      aiPane_ = pane;
      server_ = server;
      globalDisplay_ = globalDisplay;
      orchestrator_ = orchestrator;
      aiPaneEventHandlers_ = pane.getEventHandlers();
      
      setupConsoleCheck();
      setupTerminalCheck();
   }
   
   // Set up a timer to check if console execution is complete
   private void setupConsoleCheck()
   {
      consoleCheckTimer_ = new Timer()
      {
         @Override
         public void run()
         {
            checkConsoleComplete(consoleMessageId_);
         }
      };
   }
   
   // Set up a timer to check if terminal execution is complete
   private void setupTerminalCheck()
   {
      terminalCheckTimer_ = new Timer()
      {
         @Override
         public void run()
         {
            checkTerminalComplete(terminalMessageId_);
         }
      };
   }

   private void trackNewConsoleOutput(String currentContent) {
      if (currentContent != null && initialConsoleContent_ != null) {
         // Find new content that wasn't in the initial output
         if (currentContent.length() > initialConsoleContent_.length()) {
            String newContent = currentContent.substring(initialConsoleContent_.length());
            if (!newContent.isEmpty()) {
               String filteredContent = filterConsoleOutput(newContent);
               consoleOutput_.append(filteredContent);
               initialConsoleContent_ = currentContent; // Update baseline for next check
            }
         }
      }
   }
   
   private String filterConsoleOutput(String content) {
      if (content == null || content.isEmpty()) {
         return content;
      }
      
      String[] lines = content.split("\n", -1); // -1 to preserve trailing empty strings
      StringBuilder result = new StringBuilder();
      
      for (int i = 0; i < lines.length; i++) {
         String line = lines[i];
         
         if (line.trim().startsWith(">")) {
            // Check if the next line(s) also start with ">"
            int j = i + 1;
            while (j < lines.length && lines[j].trim().startsWith(">")) {
               j++;
            }
            
            // If we found consecutive ">" lines, only keep the last one
            if (j > i + 1) {
               // Skip to the last ">" line in this sequence
               i = j - 1;
               line = lines[i];
            }
            
            // Add this ">" line (either standalone or last in sequence)
            if (result.length() > 0) {
               result.append("\n");
            }
            result.append(line);
         } else {
            // Non-">" line, add it as is
            if (result.length() > 0) {
               result.append("\n");
            }
            result.append(line);
         }
      }
      
      return result.toString();
   }
   
   private void finalizeConsoleWithOutput(final int messageId, final String consoleOutput) {
      // Try to get the request_id from the console widget
      final String requestId;
      if (aiPane_ != null) {
         AiStreamingPanel streamingPanel = aiPane_.getStreamingPanel();
         if (streamingPanel != null) {
            AiConsoleWidget consoleWidget = streamingPanel.getConsoleWidget(String.valueOf(messageId));
            if (consoleWidget != null) {
               requestId = consoleWidget.getRequestId();
            } else {
               requestId = null;
            }
         } else {
            requestId = null;
            Debug.log("DEBUG: streamingPanel is null");
         }
      } else {
         requestId = null;
         Debug.log("DEBUG: aiPane_ is null");
      }
      
      // requestId must always be present - if it's missing, this indicates a bug
      if (requestId == null || requestId.isEmpty()) {
         consoleCheckTimer_.cancel();
         String errorMessage = "CRITICAL ERROR: Console command finalization failed - missing request_id for messageId: " + messageId;
         Debug.log(errorMessage);
         globalDisplay_.showErrorMessage("Console Command Error", errorMessage);
         return;
      }
      
      // Finalize console command with request_id and console output
      server_.finalizeConsoleCommand(messageId, requestId, consoleOutput, new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject result) {
            consoleCheckTimer_.cancel();
            // Reset tracking variables
            initialConsoleContent_ = null;
            consoleOutput_ = null;
            
            // Check if the result contains a status that needs processing
            if (result != null) {
               JSONObject resultObj = new JSONObject(result);
               if (resultObj.containsKey("status")) {
                  String status = getString(resultObj, "status", "");
                  
                  if ("continue_silent".equals(status)) {
                     // R wants us to continue the conversation
                     Integer relatedToId = null;
                     Integer conversationIndex = null;
                     
                     if (resultObj.containsKey("data")) {
                        JSONObject dataObj = resultObj.get("data").isObject();
                        if (dataObj != null) {
                           if (dataObj.containsKey("related_to_id")) {
                              relatedToId = getInteger(dataObj, "related_to_id", null);
                           }
                           if (dataObj.containsKey("conversation_index")) {
                              conversationIndex = getInteger(dataObj, "conversation_index", null);
                           }
                        }
                     }
                     
                     // Validate required parameters
                     if (conversationIndex == null) {
                        globalDisplay_.showErrorMessage("Error", "Console command response missing conversation_index");
                        return;
                     }
                     
                     if (relatedToId == null) {
                        globalDisplay_.showErrorMessage("Error", "Console command response missing related_to_id");
                        return;
                     }
                     
                     // Use orchestrator to continue the conversation
                     if (orchestrator_ != null) {
                        orchestrator_.continueConversation(relatedToId, conversationIndex, requestId);
                     } else {
                        Debug.log("[AiPaneResponses] ERROR: orchestrator_ is null!");
                     }
                     return;
                  }
                  else if ("done".equals(status)) {
                     // Processing is complete - no further action needed
                     return;
                  }
                  else if ("pending".equals(status)) {
                     // Function is pending user interaction - no action needed, UI will handle
                     return;
                  }
                  else if ("error".equals(status)) {
                     // Handle error status
                     String errorMessage = getString(resultObj, "error", "Console command failed");
                     globalDisplay_.showErrorMessage("Error", errorMessage);
                     return;
                  }
               }
            }
            globalDisplay_.showErrorMessage("Error", "Console command failed");
         }
         
         @Override
         public void onError(ServerError error) {
            consoleCheckTimer_.cancel();
            // Reset tracking variables
            initialConsoleContent_ = null;
            consoleOutput_ = null;
         }
      });
   }

   // Check if a previously run terminal execution is complete
   private void checkTerminalComplete(final int messageId)
   {
      server_.checkTerminalComplete(messageId, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean isComplete)
         {
            if (isComplete != null && isComplete)
            {
               // Terminal execution complete, finalize the command
               // Try to get the request_id from the terminal widget
               final String requestId;
               if (aiPane_ != null) {
                  AiStreamingPanel streamingPanel = aiPane_.getStreamingPanel();
                  if (streamingPanel != null) {
                     AiTerminalWidget terminalWidget = streamingPanel.getTerminalWidget(String.valueOf(messageId));
                     if (terminalWidget != null) {
                        requestId = terminalWidget.getRequestId();
                     } else {
                        requestId = null;
                        Debug.log("DEBUG: terminalWidget is null for messageId: " + messageId);
                     }
                  } else {
                     requestId = null;
                     Debug.log("DEBUG: streamingPanel is null");
                  }
               } else {
                  requestId = null;
                  Debug.log("DEBUG: aiPane_ is null");
               }
               
               // requestId must always be present - if it's missing, this indicates a bug
               if (requestId == null || requestId.isEmpty()) {
                  terminalCheckTimer_.cancel();
                  String errorMessage = "CRITICAL ERROR: Terminal command finalization failed - missing request_id for messageId: " + messageId;
                  Debug.log(errorMessage);
                  globalDisplay_.showErrorMessage("Terminal Command Error", errorMessage);
                  return;
               }
               
               // Finalize terminal command with request_id
               server_.finalizeTerminalCommand(messageId, requestId, new ServerRequestCallback<JavaScriptObject>() {
                  @Override
                  public void onResponseReceived(JavaScriptObject result) {
                     terminalCheckTimer_.cancel();
                     // Check if the result contains a status that needs processing
                     if (result != null) {
                        JSONObject resultObj = new JSONObject(result);
                        if (resultObj.containsKey("status")) {
                           String status = getString(resultObj, "status", "");
                           
                           if ("continue_silent".equals(status)) {
                              // R wants us to continue the conversation
                              Integer relatedToId = null;
                              Integer conversationIndex = null;
                              
                              if (resultObj.containsKey("data")) {
                                 JSONObject dataObj = resultObj.get("data").isObject();
                                 if (dataObj != null) {
                                    if (dataObj.containsKey("related_to_id")) {
                                       relatedToId = getInteger(dataObj, "related_to_id", null);
                                    }
                                    if (dataObj.containsKey("conversation_index")) {
                                       conversationIndex = getInteger(dataObj, "conversation_index", null);
                                    }
                                 }
                              }
                              
                              // Validate required parameters
                              if (conversationIndex == null) {
                                 globalDisplay_.showErrorMessage("Error", "Terminal command response missing conversation_index");
                                 return;
                              }
                              
                              if (relatedToId == null) {
                                 globalDisplay_.showErrorMessage("Error", "Terminal command response missing related_to_id");
                                 return;
                              }
                              
                              // Use orchestrator to continue the conversation
                              if (orchestrator_ != null) {
                                 orchestrator_.continueConversation(relatedToId, conversationIndex, requestId);
                              }
                              return;
                           }
                           else if ("done".equals(status)) {
                              // Processing is complete - no further action needed
                              return;
                           }
                           else if ("pending".equals(status)) {
                              // Function is pending user interaction - no action needed, UI will handle
                              return;
                           }
                           else if ("error".equals(status)) {
                              // Handle error status
                              String errorMessage = getString(resultObj, "error", "Terminal command failed");
                              globalDisplay_.showErrorMessage("Error", errorMessage);
                              return;
                           }
                        }
                     }
                     globalDisplay_.showErrorMessage("Error", "Terminal command failed");
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     // Error finalizing terminal command
                     terminalCheckTimer_.cancel();
                  }
               });
            }
            else if (isComplete == null)
            {
               // Cancelling timer due to null response
               terminalCheckTimer_.cancel();
            }
            else
            {
               // Terminal not complete yet, continuing to poll for messageId: messageId
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            // Error checking terminal status
            terminalCheckTimer_.cancel();
         }
      });
   }
   
   // Start checking if a file has completed execution
   public void startConsoleCheck(int messageId)
   {
      consoleMessageId_ = messageId;
      consoleCheckTimer_.schedule(500);
   }
   
   // Start checking if a terminal execution has completed
   public void startTerminalCheck(int messageId)
   {
      terminalMessageId_ = messageId;
      terminalCheckTimer_.schedule(500);
   }
      
   private Timer consoleCheckTimer_;
   private Timer terminalCheckTimer_;
   
   // Store specific message IDs for different operations
   private int consoleMessageId_;
   private int terminalMessageId_;
   
   private final AiPane aiPane_;
   private final AiServerOperations server_;
   private final GlobalDisplay globalDisplay_;
   private final AiOrchestrator orchestrator_;
   private final AiPaneEventHandlers aiPaneEventHandlers_;
   
   // New variables for console tracking
   private String initialConsoleContent_;
   private StringBuilder consoleOutput_;
   
   /**
    * Utility method for extracting string values from JSON objects that may be arrays (from R).
    */
   public String getString(JSONObject obj, String key, String defaultValue)
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
   
   /**
    * Utility method for extracting integer values from JSON objects that may be arrays (from R).
    */
   public Integer getInteger(JSONObject obj, String key, Integer defaultValue)
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

   // Initialize console tracking when command starts (called from handleAcceptConsoleCommand)
   public void initializeConsoleTracking() {
      AiStreamingPanel streamingPanel = aiPane_.getStreamingPanel();
      if (streamingPanel != null) {
         initialConsoleContent_ = streamingPanel.getConsoleContent();
         consoleOutput_ = new StringBuilder();
      } else {
         initialConsoleContent_ = "";
         consoleOutput_ = new StringBuilder();
      }
   }

   // Check if a previously run file execution is complete
   private void checkConsoleComplete(final int messageId)
   {
      // Console tracking should already be initialized by handleAcceptConsoleCommand
      if (initialConsoleContent_ == null) {
         throw new IllegalStateException("Console tracking not initialized");
      }
      pollConsoleStatus(messageId);
   }
   
   private void pollConsoleStatus(final int messageId) {
      AiStreamingPanel streamingPanel = aiPane_.getStreamingPanel();
      if (streamingPanel != null) {
         boolean isBusy = streamingPanel.isConsoleBusy();
         if (isBusy) {
            // Console still busy, check for new output and continue polling
            String currentContent = streamingPanel.getConsoleContent();
            trackNewConsoleOutput(currentContent);
            // Schedule next check
            consoleCheckTimer_.schedule(500);
         } else {
            // Console execution complete, get final output and finalize
            String finalContent = streamingPanel.getConsoleContent();
            trackNewConsoleOutput(finalContent);
            String accumulatedOutput = consoleOutput_.toString();
            finalizeConsoleWithOutput(messageId, accumulatedOutput);
         }
      } else {
         throw new IllegalStateException("Streaming panel is null");
      }
   }
} 