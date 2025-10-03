/*
 * AiViewManager.java
 *
 * Copyright (C) 2025 by Lotas Inc.
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
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.shared.HandlerRegistration;
import org.rstudio.studio.client.application.events.EventBus;
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
import org.rstudio.studio.client.application.Desktop;
import org.rstudio.core.client.StringUtil;
import com.google.gwt.user.client.Timer;
import org.rstudio.studio.client.workbench.views.ai.events.AiAuthenticationCompletedEvent;

/**
 * Manages switching between Settings view and Conversation streaming view
 */
public class AiViewManager
      implements AiAuthenticationCompletedEvent.Handler 
{
   private final AiStreamingPanel streamingPanel_;
   private final SimplePanel iframeContainer_;
   private final RStudioThemedFrame frame_;
   private final AiServerOperations server_;
   private final AiPane aiPane_;
   private final SimplePanel searchContainer_;
   private final org.rstudio.studio.client.application.events.EventBus eventBus_;
   private final DockLayoutPanel mainPanel_;  // AiViewManager creates and owns this
   private final SimplePanel centerContainer_;  // Center content switching
   private final AutoGlassPanel glassPanel_;  // Wrapper for proper styling
   private final SimplePanel settingsContainer_;  // Container for settings widget
   private final AiSettingsWidget settingsWidget_;  // New settings widget
   
   private boolean isInSettingsMode_ = true; // Start in Settings mode to avoid race conditions
   private boolean isInitialized_ = false; // Track initialization state
   
   // No longer need authentication polling state - using loopback server
   
   public AiViewManager(AiStreamingPanel streamingPanel,
                       SimplePanel iframeContainer,
                       SimplePanel searchContainer,
                       RStudioThemedFrame frame,
                       AiServerOperations server,
                       AiPane aiPane,
                       org.rstudio.studio.client.application.events.EventBus eventBus)
   {
      streamingPanel_ = streamingPanel;
      iframeContainer_ = iframeContainer;
      searchContainer_ = searchContainer;
      frame_ = frame;
      server_ = server;
      aiPane_ = aiPane;
      eventBus_ = eventBus;
      
      // Register for authentication completion events
      eventBus.addHandler(AiAuthenticationCompletedEvent.TYPE, this);
      
      // Set up the global authentication completion handler
      setupAuthenticationHandler();
      
      // Create settings container and widget
      settingsContainer_ = new SimplePanel();
      settingsContainer_.setSize("100%", "100%");
      settingsContainer_.addStyleName("ace_editor"); // Get ACE theme background from .rstheme files
      settingsContainer_.addStyleName("ace_editor_theme"); // Theme context marker
      
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
         public void onSignInWithWebsite() {
            server_.signInWithWebsite("", new ServerRequestCallback<String>() {
               @Override
               public void onResponseReceived(String signInUrl) {
                  if (signInUrl != null && !signInUrl.isEmpty()) {
                     // Open the sign-in URL in a new window
                     openSignInWindow(signInUrl);
                  } else {
                     RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                        "Error", "Received empty sign-in URL");
                  }
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("Sign-in URL error: " + error.getMessage());
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to get sign-in URL: " + error.getMessage());
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
         
         @Override
         public void onSecurityModeChange(String mode) {
            server_.setSecurityMode(mode, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
                  settingsWidget_.onSecurityModeChanged(mode);
                  
                  // Update PostHog tracking based on security mode
                  updatePostHogForSecurityMode(mode);
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("setSecurityMode failed: " + error.getMessage());
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set security mode: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onWebSearchEnabledChange(boolean enabled) {
            server_.setWebSearchEnabled(enabled, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
                  settingsWidget_.onWebSearchEnabledChanged(enabled);
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("setWebSearchEnabled failed: " + error.getMessage());
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set web search enabled: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onAddRule(String rule) {
            server_.addUserRule(rule, new ServerRequestCallback<JavaScriptObject>() {
               @Override
               public void onResponseReceived(JavaScriptObject response) {
                  settingsWidget_.onRuleAdded();
               }
               
               @Override
               public void onError(ServerError error) {
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to add rule: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onEditRule(int index, String rule) {
            server_.editUserRule(index, rule, new ServerRequestCallback<JavaScriptObject>() {
               @Override
               public void onResponseReceived(JavaScriptObject response) {
                  settingsWidget_.onRuleEdited();
               }
               
               @Override
               public void onError(ServerError error) {
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to edit rule: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onDeleteRule(int index) {
            server_.deleteUserRule(index, new ServerRequestCallback<JavaScriptObject>() {
               @Override
               public void onResponseReceived(JavaScriptObject response) {
                  settingsWidget_.onRuleDeleted();
               }
               
               @Override
               public void onError(ServerError error) {
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to delete rule: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onAutoAcceptEditsChange(boolean enabled) {
            server_.setAutoAcceptEdits(enabled, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
               }
               
               @Override
               public void onError(ServerError error) {
                  // setAutoAcceptEdits failed
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set auto-accept edits: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onAutoAcceptConsoleChange(boolean enabled) {
            server_.setAutoAcceptConsole(enabled, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
               }
               
               @Override
               public void onError(ServerError error) {
                  // setAutoAcceptConsole failed
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set auto-accept console: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onAutoAcceptTerminalChange(boolean enabled) {
            server_.setAutoAcceptTerminal(enabled, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
               }
               
               @Override
               public void onError(ServerError error) {
                  // setAutoAcceptTerminal failed
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set auto-accept terminal: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onAutoRunFilesChange(boolean enabled) {
            server_.setAutoRunFiles(enabled, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("setAutoRunFiles failed: " + error.getMessage());
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set auto-run files: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onAutoDeleteFilesChange(boolean enabled) {
            server_.setAutoDeleteFiles(enabled, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("setAutoDeleteFiles failed: " + error.getMessage());
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set auto-delete files: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onAutoAcceptConsoleAllowAnythingChange(boolean enabled) {
            server_.setAutoAcceptConsoleAllowAnything(enabled, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
               }
               
               @Override
               public void onError(ServerError error) {
                  // setAutoAcceptConsoleAllowAnything failed
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set auto-accept console allow anything: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onAutoAcceptTerminalAllowAnythingChange(boolean enabled) {
            server_.setAutoAcceptTerminalAllowAnything(enabled, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
               }
               
               @Override
               public void onError(ServerError error) {
                  // setAutoAcceptTerminalAllowAnything failed
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set auto-accept terminal allow anything: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onAutoRunFilesAllowAnythingChange(boolean enabled) {
            server_.setAutoRunFilesAllowAnything(enabled, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("setAutoRunFilesAllowAnything failed: " + error.getMessage());
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to set auto-run files allow anything: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onBYOKEnabledChange(String provider, boolean enabled) {
            // Update AI settings to enable/disable BYOK for this provider
            Debug.log("BYOK " + provider + " " + (enabled ? "enabled" : "disabled"));
            // The setting is already saved via the R function when toggle is clicked
            // Just start/stop the proxy if needed
            if (enabled) {
               server_.startLocalBackendProxy(new ServerRequestCallback<String>() {
                  @Override
                  public void onResponseReceived(String proxyUrl) {
                     Debug.log("Local backend proxy started at: " + proxyUrl);
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     Debug.log("Failed to start local backend proxy: " + error.getMessage());
                     RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                        "Error", "Failed to start local backend: " + error.getMessage());
                  }
               });
            }
         }
         
         @Override
         public void onBYOKApiKeySet(String provider, String apiKey) {
            Debug.log("BYOK API key set for " + provider);
            // Key is already saved via the server call in the widget
         }
         
         @Override
         public void onBYOKApiKeyDeleted(String provider) {
            Debug.log("BYOK API key deleted for " + provider);
            // Key is already deleted via the server call in the widget
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
      centerContainer_.addStyleName("ace_editor"); // Get ACE theme background from .rstheme files
      centerContainer_.addStyleName("ace_editor_theme"); // Theme context marker
      
      // Set up iframe container with the frame (remove from current parent first)
      if (frame_.getParent() != null) {
         frame_.removeFromParent();
      }
      iframeContainer_.setWidget(frame_);
      iframeContainer_.setSize("100%", "100%");
      iframeContainer_.addStyleName("ace_editor"); // Get ACE theme background from .rstheme files
      iframeContainer_.addStyleName("ace_editor_theme"); // Theme context marker
      
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
      
      // Add theme classes to the main panel for CSS theme selectors
      addThemeClasses();
      
      // Register for theme change events
      eventBus_.addHandler(org.rstudio.studio.client.application.events.ThemeChangedEvent.TYPE, 
         new org.rstudio.studio.client.application.events.ThemeChangedEvent.Handler() {
            @Override
            public void onThemeChanged(org.rstudio.studio.client.application.events.ThemeChangedEvent event) {
               updateThemeClasses();
            }
         });
      
      // Mark as initialized
      isInitialized_ = true;
   }
   
   /**
    * Add theme classes to enable CSS theme selectors to work properly
    */
   private void addThemeClasses()
   {
      // Get current theme from body class
      String themeName = org.rstudio.core.client.theme.ThemeHelper.getCurrentTheme();
      
      // Add appropriate theme class to the glass panel
      if (themeName.equals("dark-grey")) {
         glassPanel_.addStyleName("rstudio-themes-dark-grey");
      } else if (themeName.equals("alternate")) {
         glassPanel_.addStyleName("rstudio-themes-alternate");
      } else {
         glassPanel_.addStyleName("rstudio-themes-default");
      }
   }
   
   /**
    * Update theme classes when theme changes at runtime
    */
   private void updateThemeClasses()
   {
      // Remove all existing theme classes
      glassPanel_.removeStyleName("rstudio-themes-default");
      glassPanel_.removeStyleName("rstudio-themes-dark-grey");
      glassPanel_.removeStyleName("rstudio-themes-alternate");
      
      // Add the current theme class
      addThemeClasses();
   }
   
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
      // Clear persistent diff indicators when entering settings mode
      if (streamingPanel_ != null) {
         streamingPanel_.clearPersistentDiffIndicators();
      }
      
      if (!isInSettingsMode_) {
         // Hide search container by setting size to 0 (don't remove it)
         mainPanel_.setWidgetSize(searchContainer_, 0);
         searchContainer_.setVisible(false);
         
         // Show settings container in center
         centerContainer_.setWidget(settingsContainer_);
         
         isInSettingsMode_ = true;
      }
      
      settingsWidget_.refreshAllSettings();
      
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
         
         // Re-initialize persistent diff indicators when returning to conversations
         if (streamingPanel_ != null) {
            streamingPanel_.reinitializePersistentDiffIndicators();
         }
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
   
   /**
    * Update PostHog tracking based on security mode
    * @param mode The security mode ("secure" or "make_rao_better")
    */
   private void updatePostHogForSecurityMode(String mode) {
      updatePostHogForSecurityModeImpl(mode);
   }
   
   /**
    * Native method to update PostHog tracking based on security mode
    */
   private native void updatePostHogForSecurityModeImpl(String mode) /*-{
      if ($wnd.PostHogHelper && $wnd.PostHogHelper.updateTrackingForSecurityMode) {
         $wnd.PostHogHelper.updateTrackingForSecurityMode(mode);
      } else {
         console.warn("PostHog helper not available for security mode update");
      }
   }-*/;
   
   /**
    * Open sign-in window using standard OAuth loopback flow
    */
   private void openSignInWindow(String signInUrl) {      
      // Clean up any previous auth servers
      server_.cleanupAuthServer(new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Server cleanup completed, now get the sign-in URL which will start loopback server
            server_.signInWithWebsite(signInUrl, new ServerRequestCallback<String>() {
               @Override
               public void onResponseReceived(String authUrl) {
                  // Open authentication URL - desktop uses external browser, browser uses popup
                  if (Desktop.hasDesktopFrame()) {
                     Desktop.getFrame().browseUrl(StringUtil.notNull(authUrl));
                  } else {
                     openAuthWindow(authUrl);
                  }
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("Failed to get auth URL: " + error.getMessage());
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to start authentication: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Failed to cleanup auth server: " + error.getMessage());
            // Continue anyway
            server_.signInWithWebsite(signInUrl, new ServerRequestCallback<String>() {
               @Override
               public void onResponseReceived(String authUrl) {
                  // Open authentication URL - desktop uses external browser, browser uses popup
                  if (Desktop.hasDesktopFrame()) {
                     Desktop.getFrame().browseUrl(StringUtil.notNull(authUrl));
                  } else {
                     openAuthWindow(authUrl);
                  }
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("Failed to get auth URL: " + error.getMessage());
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
                     "Error", "Failed to start authentication: " + error.getMessage());
               }
            });
         }
      });
   }
   

   

   

   
   /**
    * Open authentication window in browser mode
    */
   private native void openAuthWindow(String authUrl) /*-{
      // Open popup window for browser mode - loopback server handles the callback
      $wnd.open(authUrl, "rao_signin", "width=500,height=600,scrollbars=yes,resizable=yes");
   }-*/;
   
   /**
    * Refresh settings when authentication completes
    * This will be called when the loopback server successfully receives the API key
    */
   public void onAuthenticationComplete() {
      // Update UI - API key is already saved by the loopback server
      aiPane_.refreshSettings();
      
      // Show green message prompting user to set working directory (same as API key saved)
      settingsWidget_.onAuthenticationCompleted();
   }
   
   /**
    * Set up the global JavaScript handler for authentication completion events
    */
   public static native void setupAuthenticationHandler() /*-{
      $wnd.aiAuthenticationCompleted = function() {
         @org.rstudio.studio.client.workbench.views.ai.AiViewManager::handleAuthenticationCompleted()();
      };
   }-*/;
   
   /**
    * Static method to handle authentication completion from JavaScript
    */
   public static void handleAuthenticationCompleted() {
      // Get the current AI pane instance and trigger authentication completion
      org.rstudio.studio.client.workbench.views.ai.AiPane aiPane = 
         org.rstudio.studio.client.workbench.views.ai.AiPane.getCurrentInstance();
      if (aiPane != null) {
         aiPane.onAuthenticationComplete();
      }
   }
   
   /**
    * Handler for authentication completion events from the EventBus
    */
   @Override
   public void onAiAuthenticationCompleted(AiAuthenticationCompletedEvent event) {
      onAuthenticationComplete();
   }
   
} 