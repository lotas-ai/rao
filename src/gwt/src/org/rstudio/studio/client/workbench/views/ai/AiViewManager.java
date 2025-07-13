/*
 * AiViewManager.java
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

import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.dom.client.Style.Unit;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.widget.RStudioThemedFrame;
import org.rstudio.studio.client.common.AutoGlassPanel;
import org.rstudio.studio.client.workbench.views.ai.widgets.AiStreamingPanel;
import org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.core.client.Point;
import org.rstudio.core.client.Debug;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.application.events.EventBus;

/**
 * Manages switching between Settings view and Conversation streaming view
 */
public class AiViewManager 
{
   private final AiStreamingPanel streamingPanel_;
   private final SimplePanel iframeContainer_;
   private final RStudioThemedFrame frame_;
   private final AiServerOperations server_;
   private final AiPane aiPane_;
   private final SimplePanel searchContainer_;
   private final DockLayoutPanel mainPanel_;  // AiViewManager creates and owns this
   private final SimplePanel centerContainer_;  // Center content switching
   private final AutoGlassPanel glassPanel_;  // Wrapper for proper styling
   private final SimplePanel settingsContainer_;  // Container for settings widget
   private final AiSettingsWidget settingsWidget_;  // New settings widget
   
   private boolean isInSettingsMode_ = true; // Start in Settings mode to avoid race conditions
   private boolean isInitialized_ = false; // Track initialization state
   
   public AiViewManager(AiStreamingPanel streamingPanel, 
                       SimplePanel iframeContainer,
                       SimplePanel searchContainer,
                       RStudioThemedFrame frame,
                       AiServerOperations server,
                       AiPane aiPane)
   {
      streamingPanel_ = streamingPanel;
      iframeContainer_ = iframeContainer;
      searchContainer_ = searchContainer;
      frame_ = frame;
      server_ = server;
      aiPane_ = aiPane;
      
      // Create settings container and widget
      settingsContainer_ = new SimplePanel();
      settingsContainer_.setSize("100%", "100%");
      
      // Create settings widget with handler
      AiSettingsWidget.SettingsHandler settingsHandler = new AiSettingsWidget.SettingsHandler() {
         @Override
         public void onSaveApiKey(String apiKey) {
            server_.saveApiKey("rao", apiKey, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
                  settingsWidget_.onApiKeySaved();
               }
               
               @Override
               public void onError(ServerError error) {
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to save API key: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onDeleteApiKey() {
            server_.deleteApiKey("rao", new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
                  settingsWidget_.onApiKeyDeleted();
               }
               
               @Override
               public void onError(ServerError error) {
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to delete API key: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onModelChange(String model) {
            server_.setModel("rao", model, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
                  settingsWidget_.onModelChanged(model);
               }
               
               @Override
               public void onError(ServerError error) {
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to change model: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onWorkingDirectoryChange(String directory) {
            server_.setAiWorkingDirectory(directory, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
                  settingsWidget_.updateDirectoryPath(directory);
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("setAiWorkingDirectory failed: " + error.getMessage());
                  settingsWidget_.showDirectoryError("Failed to set working directory: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onBrowseDirectory() {
            server_.browseDirectory(new ServerRequestCallback<com.google.gwt.core.client.JavaScriptObject>() {
               @Override
               public void onResponseReceived(com.google.gwt.core.client.JavaScriptObject response) {
                  // Extract directory path from response
                  String selectedDir = extractDirectoryPath(response);
                  if (selectedDir != null && !selectedDir.isEmpty()) {
                     // Set the directory on the server (same logic as onWorkingDirectoryChange)
                     server_.setAiWorkingDirectory(selectedDir, new ServerRequestCallback<java.lang.Void>() {
                        @Override
                        public void onResponseReceived(java.lang.Void response) {
                           settingsWidget_.updateDirectoryPath(selectedDir);
                        }
                        
                        @Override
                        public void onError(ServerError error) {
                           Debug.log("setAiWorkingDirectory failed: " + error.getMessage());
                           settingsWidget_.showDirectoryError("Failed to set working directory: " + error.getMessage());
                        }
                     });
                  }
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("Browse directory failed: " + error.getMessage());
                  settingsWidget_.showDirectoryError("Failed to browse directory: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onTemperatureChange(double temperature) {
            server_.setTemperature(temperature, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
                  settingsWidget_.onTemperatureChanged(temperature);
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("setTemperature failed: " + error.getMessage());
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set temperature: " + error.getMessage());
               }
            });
         }
      };
      
      settingsWidget_ = new AiSettingsWidget(
         settingsHandler,
         server_,
         RStudioGinjector.INSTANCE.getEventBus(),
         RStudioGinjector.INSTANCE.getGlobalDisplay()
      );
      
      settingsContainer_.setWidget(settingsWidget_);
      
      // Create the main panel that this view manager owns
      mainPanel_ = new DockLayoutPanel(Unit.PX);
      mainPanel_.setSize("100%", "100%");
      
      // Create center container for switching between settings and streaming container
      centerContainer_ = new SimplePanel();
      centerContainer_.setSize("100%", "100%");
      
      // Set up iframe container with the frame (remove from current parent first)
      if (frame_.getParent() != null) {
         frame_.removeFromParent();
      }
      iframeContainer_.setWidget(frame_);
      iframeContainer_.setSize("100%", "100%");
      
      // Add widgets to main panel (these are the ONLY widgets we'll ever add)
      mainPanel_.addSouth(searchContainer_, 0);   // Search initially hidden (size 0) for Settings mode
      mainPanel_.add(centerContainer_);           // Content in center
      
      // Initially hide search container for Settings mode
      searchContainer_.setVisible(false);
      
      // Show settings container in center initially
      centerContainer_.setWidget(settingsContainer_);
      
      // Wrap in AutoGlassPanel for proper CSS styling (like other WorkbenchPanes)
      glassPanel_ = new AutoGlassPanel(mainPanel_);
      glassPanel_.setSize("100%", "100%");
      
      // Mark as initialized
      isInitialized_ = true;
   }
   
   private native String extractDirectoryPath(com.google.gwt.core.client.JavaScriptObject response) /*-{
      if (response && response.directory) {
         return response.directory;
      }
      return null;
   }-*/;
   
   /**
    * Get the main widget that should be returned by createMainWidget()
    */
   public Widget getMainWidget()
   {
      return glassPanel_;
   }
   
   /**
    * Get the DockLayoutPanel for direct manipulation by other classes
    */
   public DockLayoutPanel getDockLayoutPanel()
   {
      return mainPanel_;
   }
   
   /**
    * Switch to Settings view
    */
   public void showSettings()
   {
      if (!isInSettingsMode_) {
         // Hide search container by setting size to 0 (don't remove it)
         mainPanel_.setWidgetSize(searchContainer_, 0);
         searchContainer_.setVisible(false);
         
         // Show settings container in center
         centerContainer_.setWidget(settingsContainer_);
         
         isInSettingsMode_ = true;
      }
      
      // Refresh subscription status to ensure up-to-date information
      settingsWidget_.refreshSubscriptionStatus();
      
      // Update title
      aiPane_.updateTitle("Settings");
   }
   
   /**
    * Switch to conversation streaming view
    */
   public void showConversations()
   {      
      // During initialization, prevent premature switching to conversations
      if (!isInitialized_ && isInSettingsMode_) {
         return;
      }
      
      if (isInSettingsMode_) {
         // Show search container by setting size back to 100 and making visible
         mainPanel_.setWidgetSize(searchContainer_, 100);
         searchContainer_.setVisible(true);
         
         isInSettingsMode_ = false;
      }
      
      // Always show streaming panel in center (regardless of previous mode)
      centerContainer_.setWidget(streamingPanel_);
      
      // Update title for conversation mode
      aiPane_.updateTitle("New conversation");
   }
   
   /**
    * Load historical messages from a conversation into the streaming panel
    */
   public void loadConversationHistory(int conversationId)
   {
      loadConversationHistory(conversationId, true);
   }
   
   /**
    * Load historical messages from a conversation into the streaming panel
    * @param conversationId The conversation ID
    * @param shouldLoadLog Whether to actually load the conversation log (false for new conversations)
    */
   public void loadConversationHistory(int conversationId, boolean shouldLoadLog)
   {
      // Make sure we're in conversation mode
      showConversations();
      
      // Switch to the target conversation (this saves/loads sequence state)
      streamingPanel_.switchToConversation(conversationId);
      
      if (!shouldLoadLog) {
         // Get the title
         server_.getConversationName(conversationId, new ServerRequestCallback<String>() {
            @Override
            public void onResponseReceived(String name) {
               if (name != null && !name.isEmpty()) {
                  aiPane_.updateTitle(name);
               }
            }
            
            @Override
            public void onError(ServerError error) {
               // Keep default title if we can't get the conversation name
            }
         });
         return;
      }
      
      // For existing conversations, load conversation data from server
      server_.getConversationLog(conversationId, new ServerRequestCallback<org.rstudio.studio.client.workbench.views.ai.model.ConversationLogResult>() {
         @Override
         public void onResponseReceived(org.rstudio.studio.client.workbench.views.ai.model.ConversationLogResult result) {
            if (result.getSuccess()) {
               // Check if conversation is blank (no messages)
               com.google.gwt.core.client.JsArray<org.rstudio.studio.client.workbench.views.ai.model.ConversationMessage> messages = result.getMessages();
               boolean isBlankConversation = (messages == null || messages.length() == 0);
               
               // Update title to reflect the loaded conversation
               server_.getConversationName(conversationId, new ServerRequestCallback<String>() {
                  @Override
                  public void onResponseReceived(String name) {
                     if (name != null && !name.isEmpty()) {
                        aiPane_.updateTitle(name);
                     }
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     // Keep default title if we can't get the conversation name
                  }
               });
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // If loading fails, let R-side background recreation handle clearing (treat as blank)
            // This ensures atomic swap behavior even for error cases
         }
      });
   }
   

   
   /**
    * Check if currently in Settings mode
    */
   public boolean isInSettingsMode()
   {
      return isInSettingsMode_;
   }
      
   /**
    * Allow explicit switching to conversations after initialization is complete
    */
   public void forceShowConversations()
   {
      if (isInSettingsMode_) {
         // Show search container by setting size back to 100 and making visible
         mainPanel_.setWidgetSize(searchContainer_, 100);
         searchContainer_.setVisible(true);
         
         isInSettingsMode_ = false;
      }
      
      // Always show streaming panel in center (regardless of previous mode)
      centerContainer_.setWidget(streamingPanel_);
      
      // Update title for conversation mode
      aiPane_.updateTitle("New conversation");
   }
   
   /**
    * Get the streaming panel
    */
   public AiStreamingPanel getStreamingPanel()
   {
      return streamingPanel_;
   }
   
   /**
    * Get the iframe container
    */
   public SimplePanel getIFrameContainer()
   {
      return iframeContainer_;
   }
   
   /**
    * Get the center container that manages the content switching
    */
   public SimplePanel getCenterContainer()
   {
      return centerContainer_;
   }
   

} 