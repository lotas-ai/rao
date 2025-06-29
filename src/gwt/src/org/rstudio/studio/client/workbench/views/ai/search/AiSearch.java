/*
 * AiSearch.java
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
package org.rstudio.studio.client.workbench.views.ai.search;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.inject.Inject;

import org.rstudio.core.client.Debug;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.core.client.events.SelectionCommitEvent;
import org.rstudio.core.client.widget.MessageDialog;
import org.rstudio.core.client.widget.SearchDisplay;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerErrorCause;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.views.ai.events.ShowAiEvent;
import org.rstudio.studio.client.workbench.views.ai.events.UpdateThinkingMessageEvent;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.views.ai.AiPane;
import org.rstudio.studio.client.workbench.views.ai.AiOrchestrator;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.core.client.Scheduler;

public class AiSearch
{
   public interface Display
   {
      SearchDisplay getSearchDisplay();
   }

   @Inject
   public AiSearch(Display display,
                     AiServerOperations server,
                     EventBus eventBus)
   {
      display_ = display;
      eventBus_ = eventBus;
      server_ = server;
      pendingThinkingTimer_ = null;

      // Proactively fetch the WebSocket port for cancellation
      fetchAndStoreWebSocketPort();

      display_.getSearchDisplay().addSelectionHandler((SelectionEvent<Suggestion> event) ->
      {
         fireShowAiEvent(event.getSelectedItem().getDisplayString());
      });

      display_.getSearchDisplay().addSelectionCommitHandler((SelectionCommitEvent<String> event) ->
      {
         fireShowAiEvent(event.getSelectedItem());
      });
      
      // Register for thinking message update events
      eventBus_.addHandler(UpdateThinkingMessageEvent.TYPE, 
         new UpdateThinkingMessageEvent.Handler() {
            @Override
            public void onUpdateThinkingMessage(UpdateThinkingMessageEvent event) {
               // Get the current AiPane instance
               AiPane aiPane = AiPane.getCurrentInstance();
               if (aiPane != null && aiPane.getStreamingPanel() != null) {
                  // Handle thinking message in streaming panel
                  if (event.getMessage().isEmpty() || event.getHideCancel()) {
                     aiPane.getStreamingPanel().hideThinkingMessage();
                     aiPane.hideCancelButton();
                  } else {
                     // Pass the actual message content to show the specific thinking message
                     aiPane.getStreamingPanel().showThinkingMessage(event.getMessage());
                     aiPane.showCancelButton();
                  }
               } else {
                  Debug.log("DEBUG: aiPane or streaming panel is null - cannot handle thinking message");
               }
            }
         });         
   }

   /**
    * Proactively fetches the WebSocket port and stores it for later use
    * This ensures we don't need to make RPC calls when cancelling
    */
   private void fetchAndStoreWebSocketPort() {
      server_.getTerminalWebsocketPort(new ServerRequestCallback<Double>() {
         @Override
         public void onResponseReceived(Double port) {
            if (port != null && port > 0) {
               // Store the port in AiPane for use during cancellation
               // This will also fetch the channel ID automatically
               AiPane.setWebsocketPort(port.intValue());
            } else {
               Debug.log("Error: Received invalid WebSocket port: " + port);
            }
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error fetching WebSocket port: " + error.getMessage());
         }
      });
   }

   /**
    * Ensures we have a WebSocket port available for cancellation
    * This should be called before initiating any AI search request
    */
   private void ensureWebSocketPortAvailable() {
      // Check if we need to fetch the port
      if (AiPane.getWebsocketPort() <= 0) {
         fetchAndStoreWebSocketPort();
         
         // Since fetchAndStoreWebSocketPort is asynchronous, we can't guarantee
         // the port will be available immediately. For a more robust solution,
         // we could block until the port is available, but that's beyond the
         // scope of this current implementation.
      }
   }

   public SearchDisplay getSearchWidget()
   {
      return display_.getSearchDisplay();
   }

   private void fireShowAiEvent(String topic) {
      // Check if this is the special continue token
      boolean isContinue = "__INTERNAL_AI_CONTINUE__".equals(topic);
      
      // Get the current AiPane instance
      AiPane aiPane = AiPane.getCurrentInstance();
      if (aiPane == null) {
         Debug.log("[ERROR] AiSearch.java: AiPane.getCurrentInstance() returned null");
         return;
      }
      
      // Ensure we have the WebSocket port ready for cancellation
      ensureWebSocketPortAvailable();
      
      // Generate and store a unique request ID for this search
      String requestId = null;
      if (aiPane.getEventHandlers() != null) {
         requestId = aiPane.getEventHandlers().generateAndStoreRequestId();
         
         // Store this as the current active request ID
         currentRequestId = requestId;
      }
      
      // Use the new flat architecture via AiOrchestrator
      AiOrchestrator orchestrator = aiPane.getAiOrchestrator();
      
      if (orchestrator != null) {
         orchestrator.startAiSearch(topic, requestId);
      } else {
         handleSearchError(new ServerError() {
            @Override
            public String getUserMessage() {
               return "AI Orchestrator not available";
            }
            
            @Override
            public int getCode() {
               return 500;
            }
            
            @Override
            public String getMessage() {
               return "AI Orchestrator not available";
            }
            
            @Override
            public ServerErrorCause getCause() {
               return new ServerErrorCause(500, "ORCHESTRATOR_UNAVAILABLE", "AI Orchestrator not available");
            }
            
            @Override
            public JSONValue getClientInfo() {
               return new JSONString("");
            }
            
            @Override
            public String getRedirectUrl() {
               return "";
            }
         });
      }
   }
   
   // Display the "Thinking..." message
   public void showThinkingMessage(AiPane aiPane) {
      if (aiPane != null) {
         // Cancel any existing pending timer
         if (pendingThinkingTimer_ != null) {
            pendingThinkingTimer_.cancel();
            pendingThinkingTimer_ = null;
         }
         
         // Create a new timer
         pendingThinkingTimer_ = new com.google.gwt.user.client.Timer() {
            @Override
            public void run() {
               // Clear the pending timer reference
               pendingThinkingTimer_ = null;
               
               WindowEx contentWindow = aiPane.getContentWindow();
               if (contentWindow != null) {
                  // Check if a thinking message already exists before adding a new one
                  boolean hasThinkingMessage = hasExistingThinkingMessage(contentWindow);
                  if (!hasThinkingMessage) {
                  addThinkingMessage(contentWindow);
                  // Signal AiPane to show the cancel button in toolbar instead
                  aiPane.showCancelButton();
                  }
               }
            }
         };
         
         // Schedule the timer
         pendingThinkingTimer_.schedule(750);
      }
   }
   
   // Helper method to check if a thinking message already exists
   private native boolean hasExistingThinkingMessage(WindowEx window) /*-{
      if (!window || !window.document) return false;
      
      // Check for existing thinking message by ID
      var thinkingMessage = window.document.getElementById('ai-thinking-message');
      return !!thinkingMessage;
   }-*/;
   
   // Hide the "Thinking..." message
   public void hideThinkingMessage(AiPane aiPane) {      
      // Cancel any pending thinking message timer
      if (pendingThinkingTimer_ != null) {
         pendingThinkingTimer_.cancel();
         pendingThinkingTimer_ = null;
      }
      
      if (aiPane != null) {
         WindowEx contentWindow = aiPane.getContentWindow();
         if (contentWindow != null) {
            removeThinkingMessage(contentWindow);
            // Signal AiPane to hide the cancel button
            aiPane.hideCancelButton();
         }
      }
   }
   
   // Update the existing "Thinking..." message with new text
   private native void updateThinkingMessage(WindowEx window, String message) /*-{
      if (!window || !window.document) return;
      
      // Find the existing thinking message
      var thinkingText = window.document.querySelector('.thinking-text');
      if (thinkingText) {
         // Update the text content
         thinkingText.textContent = message;
      } else {
         // If there's no existing thinking message, create one immediately
         var thinkingContainer = window.document.getElementById('ai-thinking-message');
         if (!thinkingContainer) {
            this.@org.rstudio.studio.client.workbench.views.ai.search.AiSearch::addThinkingMessage(Lorg/rstudio/core/client/dom/WindowEx;)(window);
            
            // Try to update the message text again
            thinkingText = window.document.querySelector('.thinking-text');
            if (thinkingText) {
               thinkingText.textContent = message;
            }
         }
      }
   }-*/;
   
   // Method to send cancel request to server
   public void cancelAiRequest() {
      
      // Get the current AiPane instance first to remove the thinking message
      final AiPane aiPane = AiPane.getCurrentInstance();
      if (aiPane != null) {
         WindowEx contentWindow = aiPane.getContentWindow();
         if (contentWindow != null) {
            // Remove the thinking message immediately for instant feedback
            removeThinkingMessage(contentWindow);
         }
         
         // Hide the cancel button immediately for instant visual feedback
         aiPane.hideCancelButton();
         
         // Get the request ID directly from AiPane, which is the source of truth
         String requestId = aiPane.getActiveRequestId();
         
         if (requestId != null && !requestId.isEmpty()) {
            // Directly use AiPane's WebSocket cancellation method
            aiPane.sendCancellationViaWebSocket(requestId);
         }
      }
   }
   
   // Add a "Thinking..." message to the conversation display
   private native void addThinkingMessage(WindowEx window) /*-{
      if (!window || !window.document) return;
      
      // Remove any existing thinking messages first to prevent duplicates
      this.@org.rstudio.studio.client.workbench.views.ai.search.AiSearch::removeThinkingMessage(Lorg/rstudio/core/client/dom/WindowEx;)(window);
      
      // Create the new message element with proper classes and attributes
      var thinkingContainer = window.document.createElement("div");
      thinkingContainer.className = "assistant-container thinking-message-container";
      thinkingContainer.setAttribute("data-thinking", "true");
      thinkingContainer.id = "ai-thinking-message"; // Add an ID for easy identification
      
      var messageDiv = window.document.createElement("div");
      messageDiv.className = "message assistant thinking-message";
      
      var textDiv = window.document.createElement("div");
      textDiv.className = "text";
      
      // Create a container for the dots animation
      var thinkingContent = window.document.createElement("div");
      thinkingContent.className = "thinking-content";
      thinkingContent.style.display = "flex";
      thinkingContent.style.alignItems = "center";
      
      // Add the entire "Thinking..." text as a single element that will be animated together
      var thinkingText = window.document.createElement("span");
      thinkingText.textContent = "Thinking...";
      thinkingText.className = "thinking-text"; // Class for animation
      
      // Add CSS animation for the pulsing effect
      var style = window.document.createElement('style');
      style.id = "thinking-message-style";
      style.textContent = 
         '@keyframes thinking-pulse { ' +
         '  0%, 100% { opacity: 0.7; transform: scale(0.98); } ' +
         '  50% { opacity: 1; transform: scale(1.02); } ' +
         '}' +
         '.thinking-message { opacity: 0.8; font-style: italic; }' +
         '.thinking-text { animation: thinking-pulse 1.5s infinite ease-in-out; }';
      
      window.document.head.appendChild(style);
      
      // Add the text to the container
      thinkingContent.appendChild(thinkingText);
      
      textDiv.appendChild(thinkingContent);
      messageDiv.appendChild(textDiv);
      thinkingContainer.appendChild(messageDiv);
      
      // Add to the conversation
      var conversationContainer = window.document.querySelector('.conversation-container');
      if (conversationContainer) {
         // Check for client-generated user messages
         var clientGeneratedMessages = window.document.querySelectorAll('[data-client-generated="true"]');
         if (clientGeneratedMessages.length > 0) {
            // Get the last client-generated message
            var lastClientMessage = clientGeneratedMessages[clientGeneratedMessages.length - 1];
            // Insert the thinking message after the last client-generated message
            if (lastClientMessage.nextSibling) {
               conversationContainer.insertBefore(thinkingContainer, lastClientMessage.nextSibling);
            } else {
               conversationContainer.appendChild(thinkingContainer);
            }
         } else {
            // No client-generated messages, append to the end
            conversationContainer.appendChild(thinkingContainer);
         }
      } else {
         window.document.body.appendChild(thinkingContainer);
      }
      
      // Force a reflow/repaint to ensure the message is shown immediately
      void thinkingContainer.offsetWidth;
      
      // Note: Scrolling is now handled by AiStreamingPanel.showThinkingMessage()
   }-*/;
   
   // Remove the "Thinking..." message
   private native void removeThinkingMessage(WindowEx window) /*-{
      if (!window || !window.document) return;
            
      // Get the conversation container and its current height before removing anything
      var conversationContainer = window.document.querySelector('.conversation-container');
      if (conversationContainer) {
         // Measure the container dimensions before making any changes
         var currentHeight = conversationContainer.offsetHeight;
         var currentScrollHeight = conversationContainer.scrollHeight;
                  
         // Get the thinking message element to check its dimensions
         var thinkingMessage = window.document.getElementById('ai-thinking-message');
         var thinkingMessageHeight = 0;
         
         if (thinkingMessage) {
            thinkingMessageHeight = thinkingMessage.offsetHeight;
         }
         
         // Store the current height as a fixed pixel value to maintain stability
         // Use max(currentHeight, thinkingMessageHeight) to ensure we preserve enough space
         var heightToPreserve = Math.max(currentHeight, thinkingMessageHeight);
         
         // Store a minimum value to prevent too small values
         heightToPreserve = Math.max(heightToPreserve, 100);
         
         // Apply a min-height to ensure the container doesn't collapse when the thinking message is removed
         conversationContainer.style.minHeight = heightToPreserve + "px";
         
         // Debug: Add an attribute to track that we've set this height preservation
         conversationContainer.setAttribute("data-height-preserved", "true");
         conversationContainer.setAttribute("data-preserved-height", heightToPreserve);
         
         // Store in element property for JavaScript access
         conversationContainer._preservedHeight = heightToPreserve;
      }
      
      // Remove the thinking message by ID
      var thinkingMessage = window.document.getElementById('ai-thinking-message');
      if (thinkingMessage && thinkingMessage.parentNode) {
         thinkingMessage.parentNode.removeChild(thinkingMessage);
      }
      
      // Also look for any elements with data-thinking="true" attribute
      var thinkingElements = window.document.querySelectorAll('[data-thinking="true"]');
      if (thinkingElements && thinkingElements.length > 0) {
         for (var i = 0; i < thinkingElements.length; i++) {
            var element = thinkingElements[i];
            if (element && element.parentNode) {
               element.parentNode.removeChild(element);
            }
         }
      }
      
      // Remove the style element
      var styleElement = window.document.getElementById('thinking-message-style');
      if (styleElement && styleElement.parentNode) {
         styleElement.parentNode.removeChild(styleElement);
      }
      
      // If we've removed the thinking message, ensure our debug information is added to the document
      if (conversationContainer) {         
         // Double-check if minHeight correctly applied
         if (!conversationContainer.style.minHeight || 
             conversationContainer.style.minHeight === "0px" || 
             conversationContainer.style.minHeight === "auto") {
            var attrHeight = conversationContainer.getAttribute("data-preserved-height");
            if (attrHeight) {
               conversationContainer.style.minHeight = attrHeight + "px";
            }
         }
      }
   }-*/;
   
   private void handleSearchError(ServerError error)
   {
      // Always make sure the thinking message is hidden, regardless of error type
      hideThinkingMessage(AiPane.getCurrentInstance());      
      String errorMsg = error.getUserMessage();
      
      // Check if this is a JSON error message
      try {
         int jsonStart = errorMsg.indexOf("{");
         if (jsonStart >= 0) {
            errorMsg = errorMsg.substring(jsonStart).trim();
         }
         
         // Try to parse the error message as JSON
         JSONValue jsonValue = JSONParser.parseStrict(errorMsg);
         
         if (jsonValue != null && jsonValue.isObject() != null) {
            JSONObject jsonObj = jsonValue.isObject();
            
            // Check if this is our special error format with refresh flag
            if (jsonObj.containsKey("error") && 
                jsonObj.containsKey("message") &&
                jsonObj.containsKey("refresh")) {
               
               // Extract the actual error message
               JSONValue messageVal = jsonObj.get("message");
               JSONValue refreshVal = jsonObj.get("refresh");
               
               if (messageVal != null && messageVal.isString() != null &&
                   refreshVal != null && refreshVal.isBoolean() != null) {
                  String message = messageVal.isString().stringValue();
                  
                  // Show the error message
                  RStudioGinjector.INSTANCE.getGlobalDisplay()
                     .showErrorMessage("AI Error", message);
                  
                  // Check if we should refresh
                  boolean shouldRefresh = refreshVal.isBoolean().booleanValue();
                  if (shouldRefresh) {
                     // Instead of creating a new conversation, just reload the current one
                     
                     try {
                        // Get the main AiPane class via the static method
                        AiPane aiPane = AiPane.getCurrentInstance();
                        
                        if (aiPane != null) {
                           // Get the window from the iframe and reload it
                           WindowEx contentWindow = aiPane.getContentWindow();
                           if (contentWindow != null) {
                              contentWindow.reload();
                           }
                        }
                     } catch (Exception e) {
                        Debug.log("Error during refresh: " + e.getMessage());
                     }
                  }
                  
                  // We've handled this error specially, so return
                  return;
               }
            }
         }
      } catch (Exception e) {
         // Not valid JSON or other parsing error, fall through to default handling
         Debug.log("JSON parsing error: " + e.getMessage());
      }
      
      // Default error handling
      RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage("AI Error", error.getUserMessage());
   }

   public void submitQuery(String topic) {
      fireShowAiEvent(topic);
   }

   private final AiServerOperations server_;
   private final EventBus eventBus_;
   private final Display display_;
   private com.google.gwt.user.client.Timer pendingThinkingTimer_;
   
   // Store the current request ID directly in this class
   private String currentRequestId;
} 