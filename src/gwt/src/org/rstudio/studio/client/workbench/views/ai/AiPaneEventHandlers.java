/*
 * AiPaneEventHandlers.java
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

import org.rstudio.core.client.BrowseCap;
import org.rstudio.core.client.Point;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.core.client.dom.ElementEx;
import org.rstudio.core.client.dom.EventProperty;
import org.rstudio.core.client.dom.IFrameElementEx;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.core.client.events.NativeKeyDownEvent;
import org.rstudio.core.client.command.KeyboardShortcut;
import org.rstudio.core.client.command.ShortcutManager;
import org.rstudio.core.client.hyperlink.AiPageShower;
import org.rstudio.core.client.hyperlink.HyperlinkPopupPanel;
import org.rstudio.core.client.regex.Match;
import org.rstudio.core.client.regex.Pattern;
import org.rstudio.core.client.widget.CanFocus;
import org.rstudio.core.client.widget.FocusHelper;
import org.rstudio.core.client.widget.MessageDialog;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.views.console.events.SendToConsoleEvent;
import org.rstudio.core.client.Debug;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.core.client.widget.Operation;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.event.dom.client.LoadEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.DockLayoutPanel;

public class AiPaneEventHandlers
{   
   private final AiPane pane_;
   private final Commands commands_;
   private final EventBus events_;
   private GlobalDisplay globalDisplay_;
   private String targetUrl_;
   private AiServerOperations server_;
   
   public AiPaneEventHandlers(AiPane pane, Commands commands, EventBus events)
   {
      pane_ = pane;
      commands_ = commands;
      events_ = events;
   }
   
   // Sets the GlobalDisplay reference which is needed for some methods
   public void setGlobalDisplay(GlobalDisplay globalDisplay)
   {
      globalDisplay_ = globalDisplay;
   }
   
   // Sets the server operations reference
   public void setServer(AiServerOperations server)
   {
      server_ = server;
   }
   
   public void handleKeyDown(NativeEvent e)
   {
      // determine whether this key-combination means we should focus find
      int mod = KeyboardShortcut.getModifierValue(e);
      if (mod == (BrowseCap.hasMetaKey() ? KeyboardShortcut.META
                                         : KeyboardShortcut.CTRL))
      {
         if (e.getKeyCode() == 'F')
         {
            e.preventDefault();
            e.stopPropagation();
            WindowEx.get().focus();
            pane_.lifecycle_.focusFindTextBox();
            return;
         }
         else if (e.getKeyCode() == KeyCodes.KEY_ENTER)
         {
            // extract the selected code, if any
            String code = pane_.getFrameWindow().getSelectedText();
            if (code.isEmpty())
               return;

            // send it to the console
            events_.fireEvent(new SendToConsoleEvent(
                  code,
                  true, // execute
                  false // focus
                  ));
            return;
         }
      }

      // don't let backspace perform browser back
      DomUtils.preventBackspaceCausingBrowserBack(e);
      
      // delegate to the shortcut manager
      NativeKeyDownEvent evt = new NativeKeyDownEvent(e);
      ShortcutManager.INSTANCE.onKeyDown(evt);
      if (evt.isCanceled())
      {
         e.preventDefault();
         e.stopPropagation();

         // since this is a shortcut handled by the main window
         // we set focus to it
         WindowEx.get().focus();
      }
   }
   
   private native String decodeURIComponent(String encoded) /*-{
      return decodeURIComponent(encoded);
   }-*/;
   
   // Methods moved from AiPane.java
   
   public String getTerm()
   {
      return pane_.findTextBox_.getValue().trim();
   }

   public void findNext()
   {
      String term = getTerm();
      if (term.length() > 0)
         performFind(term, true, false);
   }

   public void findPrev()
   {
      String term = getTerm();
      if (term.length() > 0)
         performFind(term, false, false);
   }

   public void performFind(String term,
                           boolean forwards,
                           boolean incremental)
   {
      WindowEx contentWindow = pane_.getContentWindow();
      if (contentWindow == null)
         return;

      // if this is an incremental search then reset the selection first
      if (incremental)
         contentWindow.removeSelection();

      contentWindow.find(term, false, !forwards, true, false);
   }

   // Firefox changes focus during our typeahead search (it must take
   // focus when you set the selection into the iframe) which breaks
   // typeahead entirely. rather than code around this we simply
   // disable it for Firefox
   public boolean isIncrementalFindSupported()
   {
      return !BrowseCap.isFirefox();
   }

   public String getUrl()
   {
      String url = null;
      try
      {
         if (pane_.getIFrameEx() != null && pane_.getIFrameEx().getContentWindow() != null)
            url = pane_.getIFrameEx().getContentWindow().getLocationHref();
      }
      catch (Exception e)
      {
         // attempting to get the URL can throw with a DOM security exception if
         // the current URL is on another domain--in this case we'll just want
         // to return null, so eat the exception.
      }
      return url;
   }

   public String getDocTitle()
   {
      return pane_.getIFrameEx().getContentDocument().getTitle();
   }

   public void focusSearchAi()
   {
      if (pane_.getSearchWidget() != null)
         FocusHelper.setFocusDeferred(pane_.getSearchWidget());
   }

   public void showAi(String url)
   {
      pane_.bringToFront();
      setLocation(url, Point.create(0, 0));
      pane_.setNavigated(true);
   }

   public void setLocation(final String url,
                          final Point scrollPos)
   {
      // Convert url to proper String to handle type issues
      String urlString = (url != null) ? url.toString() : null;
      
      // Use the URL as-is - no "fixing" needed
      targetUrl_ = urlString;
      
      // Save current search container dimensions to reapply after navigation
      final int searchContainerHeight = getSearchContainerHeight();
      
      // Check if this is an API key management page
      boolean isApiKeyPage = (urlString != null && urlString.contains("api_key_management"));
      
      // For API management pages, use direct loading to avoid timing issues
      if (isApiKeyPage) {
         pane_.hideSearchContainer();
         pane_.updateTitle("API Key Management");
         
         // Use direct frame loading - no background loading, no safety timers
         pane_.getFrame().setUrl(targetUrl_);
         
         // Restore scroll position if provided
         if (scrollPos != null) {
            com.google.gwt.core.client.Scheduler.get().scheduleDeferred(() -> {
               WindowEx window = pane_.getIFrameEx().getContentWindow();
               if (window != null) {
                  window.scrollTo(scrollPos.getX(), scrollPos.getY());
               }
            });
         }
         
         return; // Exit early - no need for complex background loading
      }
      
      // Use the helper methods in AiPane for non-API pages
      pane_.restoreSearchContainer();

      // Start with a check if we're just reloading the current URL
      if (targetUrl_ == getUrl()) {
         // Reload case - use the smooth background-to-foreground transfer for consistency
         // This ensures all reloads (including code execution refreshes) use the same smooth mechanism
         
         // Note: Scroll position saving is now handled by AiStreamingPanel
         
         // Fall through to use the same background loading mechanism as navigation
         // This ensures smooth transitions even for reloads
      }
      
      // We're navigating to a new URL (conversation pages only at this point)
      final boolean isConversationDisplay = targetUrl_.contains("conversation_display");
      
      // Add a safety timeout in case loading takes too long or fails
      final Timer[] safetyTimerRef = new Timer[1];
      safetyTimerRef[0] = new Timer() {
         @Override
         public void run() {
            Debug.log("Safety timeout triggered for URL: " + targetUrl_);
            // If we hit the timeout, load directly in the main frame as a fallback
            pane_.getFrame().setUrl(targetUrl_);
            // After direct load, restore search container height
            restoreSearchContainerHeight(searchContainerHeight);
         }
      };
      
      // Safety timeout of 8 seconds
      safetyTimerRef[0].schedule(8000);
      
      // Set up load handler for background frame
      final HandlerRegistration[] bgHandlerRef = new HandlerRegistration[1];
      bgHandlerRef[0] = pane_.getBackgroundFrame().addLoadHandler(new LoadHandler() {
         @Override
         public void onLoad(LoadEvent event) {
            // Background frame has loaded
            
            // Cancel the safety timer since we've loaded successfully
            if (safetyTimerRef[0] != null) {
               safetyTimerRef[0].cancel();
               safetyTimerRef[0] = null;
            }
            
            WindowEx bgWindow = pane_.getBackgroundIFrameEx().getContentWindow();
            
            // Note: Scroll position restoration is now handled by AiStreamingPanel
            
            // For conversation display, we need special handling
            if (isConversationDisplay) {
               // Hide the main frame before transfer to prevent flash of content at wrong scroll position
               preventFlashBeforeTransfer(pane_.getIFrameEx());
               
               // First transfer the content to the main frame
               transferDocumentContent(
                  pane_.getBackgroundIFrameEx().getContentWindow(),
                  pane_.getIFrameEx().getContentWindow()
               );
               
               // Get the main window reference
               WindowEx mainWindow = pane_.getIFrameEx().getContentWindow();
               
               // Update the history and URL state in the main window
               updateBrowserHistory(mainWindow, bgWindow.getLocationHref());
               
               // Note: Scroll handling is now done by AiStreamingPanel
               
               // Show the main frame now that everything is ready
               showFrameAfterTransfer(pane_.getIFrameEx());
               
               // After all content operations, restore search container height
               restoreSearchContainerHeight(searchContainerHeight);
            } else {
               // For non-conversation pages, process in background then transfer
               
               // Hide the main frame before transfer to prevent flash of content at wrong scroll position
               preventFlashBeforeTransfer(pane_.getIFrameEx());
               
               // Then transfer content to main frame
               transferDocumentContent(
                  pane_.getBackgroundIFrameEx().getContentWindow(),
                  pane_.getIFrameEx().getContentWindow()
               );
               
               // Get the main window reference
               WindowEx mainWindow = pane_.getIFrameEx().getContentWindow();
               
               // Update the history and URL state in the main window
               updateBrowserHistory(mainWindow, bgWindow.getLocationHref());
               
               // Note: Scroll handling is now done by AiStreamingPanel
               
               // Show the main frame now that everything is ready
               showFrameAfterTransfer(pane_.getIFrameEx());
               
               // After all content operations, restore search container height
               restoreSearchContainerHeight(searchContainerHeight);
            }
            
            // Remove the background load handler
            if (bgHandlerRef[0] != null) {
               bgHandlerRef[0].removeHandler();
               bgHandlerRef[0] = null;
            }
         }
      });
      
      // Load the new URL into the background frame (which is always invisible)
      pane_.getBackgroundFrame().setUrl(targetUrl_);
   }
   
   /**
    * Temporarily hides the iframe to prevent content flash during transfer
    */
   public native void preventFlashBeforeTransfer(IFrameElementEx iframe) /*-{
      // Save the current visibility state
      if (!iframe._originalVisibility) {
         iframe._originalVisibility = iframe.style.visibility;
      }
      
      // Hide the iframe but keep it in the layout
      iframe.style.visibility = 'hidden';
   }-*/;
   
   /**
    * Shows the iframe after content transfer is complete
    */
   public native void showFrameAfterTransfer(IFrameElementEx iframe) /*-{
      // Restore original visibility
      if (iframe._originalVisibility) {
         iframe.style.visibility = iframe._originalVisibility;
      } else {
         iframe.style.visibility = 'visible';
      }
      
      // Clean up
      delete iframe._originalVisibility;
   }-*/;

   /**
    * Directly transfers the entire document content from sourceWindow to targetWindow
    * without causing a page reload or flicker.
    */
   public native void transferDocumentContent(WindowEx sourceWindow, WindowEx targetWindow) /*-{
      try {
         // Get the source document and content
         var sourceDoc = sourceWindow.document;
         var sourceHtml = sourceDoc.documentElement.outerHTML;
         
         // Get the scroll position from the source window - this is what we want to preserve
         var scrollX = sourceWindow.pageXOffset || sourceDoc.documentElement.scrollLeft;
         var scrollY = sourceWindow.pageYOffset || sourceDoc.documentElement.scrollTop;
                  
         // Save the URL from the source window - we'll need to update history with this
         var sourceUrl = sourceWindow.location.href;
         
         // Get the target document
         var targetDoc = targetWindow.document;
         
         // Open, write, and close the document
         targetDoc.open();
         targetDoc.write(sourceHtml);
         targetDoc.close();
         
         // Set scroll position immediately after content transfer, before the frame is shown
         targetWindow.scrollTo(scrollX, scrollY);         
      } catch (e) {
         console.error("Error transferring content between frames:", e);
      }
   }-*/;

   /**
    * Updates browser history and URL without causing a reload
    */
   private native void updateBrowserHistory(WindowEx window, String url) /*-{
      try {
         // Use history.replaceState to update URL without reload
         if (window.history && window.history.replaceState) {
            window.history.replaceState({}, window.document.title, url);
         }
      } catch (e) {
         console.error("Error updating browser history:", e);
      }
   }-*/;

   // Apply blue background to the background frame
   private native void applyBlueBackground(WindowEx window) /*-{
      try {
         if (window && window.document && window.document.body) {
            // Add a semi-transparent blue overlay container
            var overlay = window.document.createElement('div');
            overlay.style.position = 'fixed';
            overlay.style.top = '0';
            overlay.style.left = '0';
            overlay.style.width = '100%';
            overlay.style.height = '100%';
            overlay.style.backgroundColor = 'rgba(0, 100, 255, 0.05)';
            overlay.style.pointerEvents = 'none';
            overlay.style.zIndex = '999999';
            overlay.id = 'blue-background-overlay';
            
            // Remove any existing overlay first
            var existingOverlay = window.document.getElementById('blue-background-overlay');
            if (existingOverlay) {
               existingOverlay.parentNode.removeChild(existingOverlay);
            }
            
            // Add to body
            window.document.body.appendChild(overlay);
            
            // Also add a small indicator in the corner
            var indicator = window.document.createElement('div');
            indicator.innerText = 'Background Frame';
            indicator.style.position = 'fixed';
            indicator.style.top = '5px';
            indicator.style.right = '5px';
            indicator.style.padding = '3px 8px';
            indicator.style.backgroundColor = 'rgba(0, 100, 255, 0.8)';
            indicator.style.color = 'white';
            indicator.style.fontFamily = 'sans-serif';
            indicator.style.fontSize = '10px';
            indicator.style.borderRadius = '3px';
            indicator.style.zIndex = '1000000';
            indicator.style.pointerEvents = 'none';
            indicator.id = 'background-frame-indicator';
            
            // Remove any existing indicator
            var existingIndicator = window.document.getElementById('background-frame-indicator');
            if (existingIndicator) {
               existingIndicator.parentNode.removeChild(existingIndicator);
            }
            
            // Add to body
            window.document.body.appendChild(indicator);
            
            // Add slight blue border to visually indicate this is the background frame
            window.document.body.style.boxShadow = 'inset 0 0 0 3px rgba(0, 100, 255, 0.5)';
         }
      } catch (e) {
         console.error("Error applying blue background:", e);
      }
   }-*/;

   public void findInTopic(String term, CanFocus findInputSource)
   {
      // get content window
      WindowEx contentWindow = pane_.getContentWindow();
      if (contentWindow == null)
         return;

      if (!contentWindow.find(term, false, false, true, false))
      {
         globalDisplay_.showMessage(GlobalDisplay.MSG_INFO,
               pane_.getConstants().findInTopicLabel(),
               pane_.getConstants().noOccurrencesFoundMessage(),
               findInputSource);
      }
   }
   
   private final native void replaceFrameUrl(JavaScriptObject frame, String url) /*-{
      frame.contentWindow.setTimeout(function() {
         this.location.replace(url);
      }, 0);
   }-*/;
   
   /**
    * Called from JSNI when Enter is pressed in the search textarea
    */
   public void handleAiSearchFromJS(final String searchValue) {
      if (searchValue == null || searchValue.trim().isEmpty()) {
         return;
      }
      
      // Get the content window
      final WindowEx contentWindow = pane_.getContentWindow();
      if (contentWindow == null) {
         return;
      }
      
      // Check for special continue token
      boolean isContinueToken = "__INTERNAL_AI_CONTINUE__".equals(searchValue);
      
      // Only add user message to display if it's not the continue token
      if (!isContinueToken) {
         // Check if the search value actually has content
         String trimmedSearch = searchValue.trim();
         if (trimmedSearch.isEmpty()) {
            return; // Don't process empty messages
         }
         
         // Immediately add the user message to the display before sending to server
         pane_.lifecycle_.addUserMessageToDisplay(contentWindow, searchValue);
         
         // Force UI update - trigger a layout flush using this alternative approach
         contentWindow.getDocument().getBody().getClientHeight();
         
               // Note: User message scrolling is now handled by AiStreamingPanel.createUserMessageSynchronously()
      }
      
      // Add a small delay before sending to server to ensure UI updates first
      new Timer() {
         @Override
         public void run() {
            if (pane_.getSearchWidget() != null) {
               // Use the continue token when isContinueToken is true
               String valueToSend = isContinueToken ? "__INTERNAL_AI_CONTINUE__" : searchValue;
               org.rstudio.core.client.events.SelectionCommitEvent.fire(pane_.getSearchWidget(), valueToSend);
               
               // Clear the text box after triggering the search
               Scheduler.get().scheduleDeferred(() -> {
                  // Clear the search widget's text
                  pane_.getSearchWidget().setText("");
                  
                  // Also directly clear any textareas that might be in the search widget
                  NodeList<Element> textareas = ((Widget)pane_.getSearchWidget()).getElement().getElementsByTagName("textarea");
                  for (int i = 0; i < textareas.getLength(); i++) {
                     Element textarea = textareas.getItem(i);
                     textarea.setPropertyString("value", "");
                  }
                  
                  // No additional scrolling after clearing text
                  // We've already scrolled when the user submitted their request
               });
            }
         }
      }.schedule(50); // Small delay to ensure UI updates first
   }
   
   /**
    * Helper method to safely set an item in localStorage
    */
   private native void setLocalStorageItem(String key, String value) /*-{
      $wnd.localStorage.setItem(key, value);
   }-*/;

   /**
    * Adds a "Thinking..." message to the conversation display
    * that will be visible until the AI response is received
    * 
    * @param window The content window containing the conversation display
    */
   private native void addThinkingMessage(WindowEx window) /*-{
      // This method is no longer used - "Thinking..." messages are now managed by AiSearch.java
   }-*/;

   /**
    * Checks if the page is in a "waiting for response" state and adds a "Thinking..." message if needed
    * 
    * @param window The content window containing the conversation display
    */
   private native void checkAndAddThinkingAfterReload(WindowEx window) /*-{
      // This method is no longer used - "Thinking..." messages are now managed by AiSearch.java
      // Note: The actual implementation will be in AiSearch.java
      // Case #2: When "Thinking..." happens for the first time, AiSearch.java should scroll to bottom
   }-*/;

   // OLD SCROLLING METHODS REMOVED - scrolling now handled by AiStreamingPanel

   /**
    * Gets the current height of the search container
    */
   private int getSearchContainerHeight() {
      if (pane_.getSearchContainer() != null && 
          pane_.getMainPanel() != null && 
          pane_.getSearchContainer().isVisible()) {
         try {
            DockLayoutPanel mainPanel = pane_.getMainPanel();
            Widget searchContainer = pane_.getSearchContainer();
            // Get the current assigned height from the DockLayoutPanel and convert to int
            double size = mainPanel.getWidgetSize(searchContainer);
            return (int) Math.round(size);
         } catch (Exception e) {
            // Default to 100px if there's any error
            return 100;
         }
      }
      return 100; // Default height
   }
   
   /**
    * Restores the search container to a specific height
    */
   private void restoreSearchContainerHeight(final int height) {
      if (height <= 0) return;
      
      // Use a timer to ensure the DOM has stabilized after content changes
      Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
         @Override
         public void execute() {
            if (pane_.getSearchContainer() != null && 
                pane_.getMainPanel() != null && 
                pane_.getSearchContainer().isVisible()) {
               try {
                  // Set the widget size in the layout panel directly
                  DockLayoutPanel mainPanel = pane_.getMainPanel();
                  Widget searchContainer = pane_.getSearchContainer();
                  mainPanel.setWidgetSize(searchContainer, height);
                  mainPanel.forceLayout();
                  
                  // Also update inline styles for consistency
                  Element style = pane_.getSearchContainer().getElement();
                  style.getStyle().setProperty("minHeight", height + "px");
                  style.getStyle().setProperty("height", "auto");
               } catch (Exception e) {
                  // Ignore errors
               }
            }
         }
      });
   }
   
   /**
    * Handles the acceptance of AI-generated code
    * 
    * @param editedCode The code to accept
    * @param messageId The ID of the message to accept
    */
   public void handleAcceptEditFileCommand(String editedCode, String messageId)
   {
      server_.acceptEditFileCommand(editedCode, messageId, getCurrentRequestId(), new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject response) {
            // Use smooth refresh mechanism instead of direct reload to prevent flashing
            String currentUrl = pane_.getUrl();
            if (currentUrl != null && !currentUrl.isEmpty()) {
               WindowEx contentWindow = pane_.getContentWindow();
               Point currentScrollPos = Point.create(0, 0);
               if (contentWindow != null) {
                  currentScrollPos = contentWindow.getScrollPosition();
               }
               setLocation(currentUrl, currentScrollPos);
            } else {
               // Fallback to direct reload only if no URL available
               pane_.getIFrameEx().getContentWindow().reload();
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // Error handling could be added here
         }
      });
   }
   
   /**
    * Handles revert message
    * @param messageId The ID of the message to revert
    */
   public void handleRevertMessage(String messageId)
   {
      server_.revertAiMessage(Integer.parseInt(messageId), new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // For the new DOM-based system, refresh the conversation display properly
            org.rstudio.studio.client.workbench.views.ai.widgets.AiStreamingPanel streamingPanel = 
               pane_.getStreamingPanel();
            
            if (streamingPanel != null) {
               // Use the proper streaming panel refresh method
               // Get current conversation index and reload the conversation history
               server_.getCurrentConversationIndex(new ServerRequestCallback<Double>() {
                  @Override
                  public void onResponseReceived(Double conversationIndex) {
                     if (conversationIndex != null && conversationIndex.intValue() > 0) {
                        // Use the view manager to reload the conversation properly
                        org.rstudio.studio.client.workbench.views.ai.AiViewManager viewManager = 
                           pane_.getToolbars().getViewManager();
                        viewManager.loadConversationHistory(conversationIndex.intValue(), true);
                     }
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     // If we can't get conversation index, just clear and reinitialize
                     streamingPanel.clearMessages();
                  }
               });
            } else {
               // Fallback: if streaming panel is not available, use iframe refresh
               pane_.refreshIframe();
            }
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to revert message: " + error.getMessage());
         }
      });
   }
   
   /**
    * Shows a confirmation dialog for reverting AI messages
    * @param messageId The ID of the message to revert
    */
   public void handleAiRevertConfirmation(String messageId) {
      final String messageIdFinal = messageId;
      globalDisplay_.showYesNoMessage(
         GlobalDisplay.MSG_WARNING,
         "Revert Changes",
         "This will revert all code changes and delete all messages after this point in the conversation. This cannot be undone.",
         new Operation() {
            @Override
            public void execute() {
               // User confirmed, proceed with reversion
               handleRevertMessage(messageIdFinal);
            }
         },
         false  // Default is No
      );
   }
   
   /**
    * Extracts the pending function call ID from a message by looking at the conversation log
    * @param messageId The message ID to check
    * @return The pending function call ID if found, null otherwise
    */
   private String extractPendingFunctionCallId(int messageId) {
      // This will need to be implemented to check the conversation log
      // For now, we'll use a native JavaScript call to check the message
      return extractPendingFunctionCallIdNative(String.valueOf(messageId));
   }
   
   /**
    * Native method to extract pending function call ID from the conversation display
    */
   private native String extractPendingFunctionCallIdNative(String messageId) /*-{
      try {
         var window = this.@org.rstudio.studio.client.workbench.views.ai.AiPaneEventHandlers::pane_.@org.rstudio.studio.client.workbench.views.ai.AiPane::getFrameWindow()();
         if (window && window.document) {
            // Look for a message element with this ID
            var messageElement = window.document.querySelector('[data-msg-id="' + messageId + '"]');
            if (messageElement) {
               // Check if this message has a pending function call ID attribute
               var pendingId = messageElement.getAttribute('data-pending-function-call-id');
               return pendingId;
            }
         }
         return null;
      } catch (e) {
         console.error("Error extracting pending function call ID:", e);
         return null;
      }
   }-*/;
   
   /**
    * Gets the filename for a message ID
    * @param messageId The message ID
    * @param callback The callback to call with the filename
    */
   public void handleGetFileNameForMessageId(final String messageId, final JavaScriptObject callback)
   {
      server_.getFileNameForMessageId(messageId, new ServerRequestCallback<String>() {
         @Override
         public void onResponseReceived(String filename) {
            callCallback(callback, filename);
         }
         
         @Override
         public void onError(ServerError error) {
            // If there's an error, return an empty string
            callCallback(callback, "");
         }
      });
   }
   
   private native static void callCallback(JavaScriptObject callback, String filename) /*-{
      callback(filename);
   }-*/;
   
   /**
    * Handles saving an API key
    * @param provider The provider
    * @param key The API key
    */
   public void handleSaveApiKey(String provider, String key)
   {      
      server_.saveApiKey(provider, key, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Successful save - refresh the API key management page to show the new state
            pane_.refreshApiKeyManagement();
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to save API key: " + error.getMessage());
            
            // Still try to keep search container hidden
            pane_.hideSearchContainer();
         }
      });
   }
   
   /**
    * Handles deleting an API key
    * @param provider The provider
    */
   public void handleDeleteApiKey(String provider)
   {
      server_.deleteApiKey(provider, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Successful delete - refresh the API key management page to show the new state
            pane_.refreshApiKeyManagement();
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error Deleting API Key", error.getMessage());
            
            // Still try to keep search container hidden
            pane_.hideSearchContainer();
         }
      });
   }
   
   /**
    * Handles setting the active provider
    * @param provider The provider to set as active
    */
   public void handleSetActiveProvider(String provider)
   {
      server_.setActiveProvider(provider, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Successful provider set - no refresh needed since direct frame loading handles it
            // Ensure search container remains hidden
            pane_.hideSearchContainer();
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error Setting Active Provider", error.getMessage());
            
            // Still try to keep search container hidden
            pane_.hideSearchContainer();
         }
      });
   }
   
   /**
    * Handles setting the model
    * @param provider The provider
    * @param model The model to set
    */
   public void handleSetModel(String provider, String model)
   {
      server_.setModel(provider, model, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Successful model set - no refresh needed since direct frame loading handles it
            // Ensure search container remains hidden
            pane_.hideSearchContainer();
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error Setting Model", error.getMessage());
            
            // Still try to keep search container hidden
            pane_.hideSearchContainer();
         }
      });
   }
   
   /**
    * Handles setting the AI working directory
    * @param dir The directory to set
    */
   public void handleSetAiWorkingDirectory(String dir)
   {
      server_.setAiWorkingDirectory(dir, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Success case is handled in the JavaScript
         }
         
         @Override
         public void onError(ServerError error) {
            WindowEx window = pane_.getFrameWindow();
            if (window != null) {
               window.eval(
                  "document.getElementById('directory-error').innerHTML = 'Error: " + 
                  error.getMessage().replace("'", "\\'") + "';" +
                  "document.getElementById('directory-error').style.display = 'block';" +
                  "document.getElementById('directory-success').style.display = 'none';"
               );
            } else {
               globalDisplay_.showErrorMessage("Error Setting Working Directory", error.getMessage());
            }
         }
      });
   }
   
   // Removed duplicate refreshApiKeyManagement() method
   // All API key management refreshes should go through AiPane.refreshApiKeyManagement()
   
   /**
    * Handles marking a button as run
    * @param messageId The message ID
    * @param buttonType The button type
    */
   public void handleMarkButtonAsRun(String messageId, String buttonType)
   {      
      // First, preserve any client-generated messages before reload
      // Preserve client-generated messages before refreshing iframe
      
      // Call the server to mark the button as run
      server_.markButtonAsRun(messageId, buttonType, new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean response) 
         {
            // Use smooth refresh mechanism instead of direct reload to prevent flashing
            String currentUrl = pane_.getUrl();
            if (currentUrl != null && !currentUrl.isEmpty()) {
               WindowEx contentWindow = pane_.getContentWindow();
               Point currentScrollPos = Point.create(0, 0);
               if (contentWindow != null) {
                  currentScrollPos = contentWindow.getScrollPosition();
               }
               setLocation(currentUrl, currentScrollPos);
            } else {
               // Fallback to direct reload only if no URL available
               pane_.getIFrameEx().getContentWindow().reload();
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            // Even on error, use smooth refresh for consistency
            String currentUrl = pane_.getUrl();
            if (currentUrl != null && !currentUrl.isEmpty()) {
               WindowEx contentWindow = pane_.getContentWindow();
               Point currentScrollPos = Point.create(0, 0);
               if (contentWindow != null) {
                  currentScrollPos = contentWindow.getScrollPosition();
               }
               setLocation(currentUrl, currentScrollPos);
            } else {
               // Fallback to direct reload only if no URL available
               pane_.getIFrameEx().getContentWindow().reload();
            }
         }
      });
   }
   
   /**
    * Handles browsing for a directory
    */
   public void handleBrowseDirectory() {
      server_.browseDirectory(new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject result) {
            boolean success = getBooleanProperty(result, "success");
            if (success) {
               String directory = getStringProperty(result, "directory");
               if (directory != null && !directory.isEmpty()) {
                  // Update the directory input field in the iframe
                  WindowEx window = pane_.getFrameWindow();
                  if (window != null) {
                     window.eval(
                        "document.getElementById('working-directory').value = '" + 
                        directory.replace("'", "\\'") + "';" +
                        "document.getElementById('directory-success').style.display = 'block';" +
                        "document.getElementById('directory-error').style.display = 'none';" +
                        "setTimeout(function() { document.getElementById('directory-success').style.display = 'none'; }, 3000);"
                     );
                  }
               }
            } else if (hasProperty(result, "error")) {
               String errorMsg = getStringProperty(result, "error");
               WindowEx window = pane_.getFrameWindow();
               if (window != null) {
                  window.eval(
                     "document.getElementById('directory-error').innerHTML = 'Error: " + 
                     errorMsg.replace("'", "\\'") + "';" +
                     "document.getElementById('directory-error').style.display = 'block';" +
                     "document.getElementById('directory-success').style.display = 'none';"
                  );
               }
            }
         }

         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", error.getMessage());
         }
      });
   }
   
   private native boolean getBooleanProperty(JavaScriptObject obj, String property) /*-{
      if (obj && obj.hasOwnProperty(property))
         return obj[property];
      return false;
   }-*/;
   
   private native String getStringProperty(JavaScriptObject obj, String property) /*-{
      if (obj && obj.hasOwnProperty(property))
         return obj[property];
      return null;
   }-*/;
   
   private final native boolean hasProperty(JavaScriptObject obj, String property) /*-{
      return obj && obj.hasOwnProperty(property);
   }-*/;
   
   /**
    * Gets the current request ID from AiPane
    * @return The current request ID or null if not available
    */
   public String getCurrentRequestId() {
      AiPane aiPane = AiPane.getCurrentInstance();
      return aiPane != null ? aiPane.getActiveRequestId() : null;
   }

   /**
    * Generates a unique request ID and stores it for the current request
    * @return The generated request ID
    */
   public String generateAndStoreRequestId() {
      // Generate a unique ID using current timestamp and random number
      String requestId = generateUniqueRequestId();
      
      // Store it using AiPane's direct method
      AiPane aiPane = AiPane.getCurrentInstance();
      if (aiPane != null) {
         aiPane.storeActiveRequestId(requestId);
      }
      
      return requestId;
   }

   /**
    * Generates a unique request ID using timestamp and random number
    */
   private native String generateUniqueRequestId() /*-{
      var timestamp = new Date().getTime();
      var random = Math.floor(Math.random() * 10000);
      return "req_" + timestamp + "_" + random;
   }-*/;
   
   /**
    * Creates a conversation data object for AiLoadConversationEvent
    */
   private native org.rstudio.studio.client.workbench.views.ai.events.AiLoadConversationEvent.Data createConversationData(int conversationId) /*-{
      return {
         conversationId: conversationId,
         conversationName: ""
      };
   }-*/;

} 