/*
 * AiPaneScroll.java
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
import org.rstudio.core.client.dom.ElementEx;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.ServerError;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * Handles scrolling and input resizing functionality for the AI Pane
 */
public class AiPaneScroll
{
   public AiPaneScroll(AiPane pane)
   {
      pane_ = pane;
   }
   
   /**
    * Sets the scroll handler for the specified window.
    * Public access for AiPaneEventHandlers with different name to avoid recursion
    */
   public void publicSetWindowScrollHandler(WindowEx window)
   {
      setWindowScrollHandler(window);
   }
   
   /**
    * Sets a native scroll handler on the window
    */
   private final native void setWindowScrollHandler(WindowEx window) /*-{
      var self = this;
      window.onscroll = $entry(function() {
         self.@org.rstudio.studio.client.workbench.views.ai.AiPaneScroll::onScroll()();
      });
   }-*/;

   /**
    * Handles the scroll event
    */
   private void onScroll()
   {
      // Don't save scroll position for conversation display to prevent interfering 
      // with conversation-specific scroll restoration
      String url = pane_.getUrl();
      if (url != null && url.contains("conversation_display")) {
         // Skip scroll handling entirely for conversation displays
         // We now store conversation-specific scroll positions when navigating
         return;
      }
      
      // Normal scroll position saving for other content
      getScrollTimer().schedule(50);
   }

   /**
    * Configures an input element to support multiline text with auto-expanding height
    * by replacing it with a textarea that has similar attributes
    */
   public native void configureInputForMultiline(Element input) /*-{
      // Only convert to textarea if not already converted
      if (!input.getAttribute("converted-to-textarea")) {
         input.setAttribute("converted-to-textarea", "true");
         
         // Create a textarea to replace the input
         var textarea = $doc.createElement("textarea");
         
         // Copy relevant attributes from input to textarea
         textarea.className = input.className;
         textarea.placeholder = input.placeholder;
         textarea.value = input.value;
         textarea.id = input.id;
         textarea.name = input.name;
         
         // Apply specific styles for textarea behavior
         textarea.style.width = "100%";
         textarea.style.height = "auto";
         textarea.style.minHeight = "22px";
         textarea.style.maxHeight = "120px";
         textarea.style.boxSizing = "border-box";
         textarea.style.resize = "none";
         textarea.style.overflow = "auto";
         textarea.style.borderRadius = "4px"; // All corners rounded
         textarea.style.outline = "none";
         textarea.style.border = "none"; // Remove all borders
         textarea.style.boxShadow = "none"; // Remove box shadow
         textarea.style.padding = "8px"; // Match input padding
         textarea.style.display = "block";
         textarea.style.marginBottom = "0px";
         textarea.style.marginTop = "0px";
         textarea.style.position = "relative";
         textarea.style.bottom = "0";
         textarea.style.zIndex = "101";
         textarea.style.backgroundColor = "#ffffff";
         textarea.style.marginLeft = "0px";
         textarea.style.marginRight = "0px";
         
         // Match font styling from the original input
         var computedStyle = window.getComputedStyle(input);
         var fontFamily = computedStyle.fontFamily;
         var fontSize = computedStyle.fontSize || "14px";
         var fontWeight = computedStyle.fontWeight || "normal";
         
         if (!fontFamily || fontFamily === "" || fontFamily === "inherit" || fontFamily === "monospace") {
            var parent = input.parentElement;
            while (parent) {
               var parentStyle = window.getComputedStyle(parent);
               if (parentStyle.fontFamily && parentStyle.fontFamily !== "" && 
                  parentStyle.fontFamily !== "inherit" && parentStyle.fontFamily !== "monospace") {
                  fontFamily = parentStyle.fontFamily;
                  break;
               }
               parent = parent.parentElement;
            }
         }
         if (!fontFamily || fontFamily === "" || fontFamily === "inherit" || fontFamily === "monospace") {
            fontFamily = "sans-serif";
         }
         
         textarea.style.fontFamily = fontFamily;
         textarea.style.fontSize = fontSize;
         textarea.style.fontWeight = fontWeight;
         textarea.style.fontStyle = computedStyle.fontStyle || "normal";
         textarea.style.letterSpacing = computedStyle.letterSpacing || "normal";
         textarea.style.lineHeight = computedStyle.lineHeight || "1.4";
         textarea.style.color = computedStyle.color;
         textarea.style.backgroundColor = "transparent";
         
         // Replace the input with the textarea
         input.parentNode.replaceChild(textarea, input);
         if (document.activeElement === input) {
            textarea.focus();
         }
         
         // Add keydown listener for Enter key to submit query
         textarea.addEventListener('keydown', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
               e.preventDefault();
               var queryText = textarea.value ? textarea.value.trim() : '';
               if (queryText.length > 0) {
                  // Clear the textarea immediately for instant visual feedback
                  textarea.value = '';
                  
                  // Call through to AiPane to submit the query
                  @org.rstudio.studio.client.workbench.views.ai.AiPane::submitQueryStatic(Ljava/lang/String;)(queryText);
               }
            }
         });
         
         // Auto-resize function with debouncing
         var resizeTimeout = null;
         var resizeTextarea = function() {
            // Cancel any pending resize
            if (resizeTimeout) {
               clearTimeout(resizeTimeout);
            }
            
            // Schedule a new resize with a 50ms delay
            resizeTimeout = setTimeout(function() {
               resizeTimeout = null;
               
               textarea.style.height = "auto"; // Reset height to recalc

               // Calculate the number of lines based on scrollHeight and computed line height
               var lineHeight = parseFloat(window.getComputedStyle(textarea).lineHeight) + 0.2;
               var numberOfLines = textarea.scrollHeight / lineHeight;
               var ceilLines = Math.ceil(numberOfLines) - 1;

               // Cap the effective number of lines to a maximum of 6
               var effectiveLines = Math.min(ceilLines, 6);

               // Check if we're on the API key management page
               var isApiKeyPage = false;
               try {
                  // Check current URL - be more specific to avoid false positives
                  var currentUrl = window.location.href;
                  if (currentUrl && 
                      (currentUrl.indexOf("/api_key_management.html") !== -1 || 
                       currentUrl.indexOf("ai/doc/html/api_key_management") !== -1)) {
                     isApiKeyPage = true;
                  }
                  
                  // Also check if we're in an iframe
                  try {
                     if (window.frameElement && window.parent) {
                        var parentUrl = window.parent.location.href;
                        if (parentUrl && 
                            (parentUrl.indexOf("/api_key_management.html") !== -1 || 
                             parentUrl.indexOf("ai/doc/html/api_key_management") !== -1)) {
                           isApiKeyPage = true;
                        }
                     }
                  } catch(e) {
                     // Ignore cross-origin frame errors
                  }
                  
                  // Ask the AiPane directly if we're on the API key management page
                  var pane = @org.rstudio.studio.client.workbench.views.ai.AiPane::getCurrentInstance()();
                  if (pane) {
                     var url = pane.@org.rstudio.studio.client.workbench.views.ai.AiPane::getUrl()();
                     if (url && 
                         (url.indexOf("/api_key_management.html") !== -1 || 
                          url.indexOf("ai/doc/html/api_key_management") !== -1)) {
                        isApiKeyPage = true;
                        
                        // Also check if any frame is loading API key management
                        var isLoading = pane.@org.rstudio.studio.client.workbench.views.ai.AiPane::isLoadingApiKeyManagement()();
                        if (isLoading) {
                           isApiKeyPage = true;
                        }
                     }
                  }
               } catch(e) {
                  // Ignore errors
                  console.error("Error checking URL:", e);
               }

               // Calculate the new container height:
               var newContainerHeight = 0;
               if (!isApiKeyPage) {
                  // More consistent calculation for the container height
                  // Use Math.round to ensure consistent integer values across frames
                  var baseHeight = Math.round(4.25 * lineHeight);
                  var additionalHeight = Math.round((effectiveLines - 1) * lineHeight);
                  newContainerHeight = baseHeight + additionalHeight;
                  
                  // Consistent scrollbar adjustment
                  if (ceilLines >= 6) {
                      newContainerHeight -= 12; // Add room for the scrollbar that appears
                  }
                  
                  // Ensure minimum height consistency
                  newContainerHeight = Math.max(100, newContainerHeight);
               }

               // Find the search container element (by class name "rstudio-AiSearchContainer")
               var searchContainer = null;
               var parentEl = textarea.parentElement;
               while (parentEl && parentEl.classList) {
                  if (parentEl.classList.contains("rstudio-AiSearchContainer")) {
                     searchContainer = parentEl;
                     break;
                  }
                  parentEl = parentEl.parentElement;
               }

               // Update the container's CSS if found
               if (searchContainer) {
                  if (isApiKeyPage) {
                     // If on API key management page, hide the container
                     searchContainer.style.minHeight = "0px";
                     searchContainer.style.height = "0px";
                     searchContainer.style.maxHeight = "0px";
                     searchContainer.style.paddingTop = "0px";
                     searchContainer.style.paddingBottom = "0px";
                     searchContainer.style.marginTop = "0px";
                     searchContainer.style.marginBottom = "0px";
                     searchContainer.style.opacity = "0";
                     searchContainer.style.visibility = "hidden";
                     searchContainer.style.overflow = "hidden";
                     searchContainer.style.position = "absolute";
                     searchContainer.style.zIndex = "-1000";
                     searchContainer.style.pointerEvents = "none";
                     searchContainer.style.display = "none";
                  } else {
                     // Normal behavior for main container (rstudio-AiSearchContainer)
                     searchContainer.style.minHeight = Math.max(100, newContainerHeight) + "px";
                     searchContainer.style.height = "auto";
                     searchContainer.style.maxHeight = "";
                     searchContainer.style.paddingTop = (effectiveLines > 1 ? Math.max(6, (effectiveLines - 1) * 0) : 6) + "px";
                     searchContainer.style.paddingBottom = "0px";
                     searchContainer.style.marginTop = "0px";
                     searchContainer.style.marginBottom = "0px";
                     searchContainer.style.zIndex = "100";
                     searchContainer.style.opacity = "1";
                     searchContainer.style.visibility = "visible";
                     searchContainer.style.overflow = "visible";
                     searchContainer.style.position = "relative";
                     searchContainer.style.pointerEvents = "auto";
                     searchContainer.style.display = "block";
                     searchContainer.style.backgroundColor = "#ffffff";
                     searchContainer.style.transition = "opacity 0.1s ease";
                     searchContainer.style.borderTop = "none";
                     searchContainer.style.borderBottom = "none";
                  }
               }

               // Call back into Java to update the DockLayoutPanel's south widget size
               var pane = @org.rstudio.studio.client.workbench.views.ai.AiPane::getCurrentInstance()();
               if (pane) {
                  try {
                     if (isApiKeyPage) {
                        // On API key management page: set size to 0 and hide container
                        pane.@org.rstudio.studio.client.workbench.views.ai.AiPane::updateSouthPanelSize(I)(0);
                        // Call the Java method to fully hide the search container - no setTimeout
                        pane.@org.rstudio.studio.client.workbench.views.ai.AiPane::hideSearchContainer()();
                     } else {
                        // On normal pages: restore container with proper size
                        var finalHeight = Math.max(100, newContainerHeight);
                        pane.@org.rstudio.studio.client.workbench.views.ai.AiPane::updateSouthPanelSize(I)(finalHeight);
                        // Restore search container visibility if needed - no setTimeout
                        pane.@org.rstudio.studio.client.workbench.views.ai.AiPane::restoreSearchContainer()();
                     }
                  } catch(e) {
                     console.error("Error updating search container:", e);
                  }
               }

               // Adjust the textarea's height and overflow behavior
               if (isApiKeyPage) {
                  // Hide textarea on API key management page
                  textarea.style.height = "0px";
                  textarea.style.minHeight = "0px";
                  textarea.style.maxHeight = "0px";
                  textarea.style.overflow = "hidden";
                  textarea.style.opacity = "0";
                  textarea.style.visibility = "hidden";
                  textarea.style.position = "absolute";
                  textarea.style.display = "none";
                  textarea.style.marginTop = "0px";
                  textarea.style.marginBottom = "0px";
                  textarea.style.paddingTop = "0px";
                  textarea.style.paddingBottom = "0px";
               } else if (ceilLines > 6) {
                  var newHeight = Math.round(lineHeight * 6);
                  textarea.style.height = newHeight + "px";
                  textarea.style.minHeight = "22px";
                  textarea.style.maxHeight = "120px";
                  textarea.style.overflowY = "auto";
                  textarea.style.opacity = "1";
                  textarea.style.visibility = "visible";
                  textarea.style.position = "relative";
                  textarea.style.display = "block";
               } else {
                  textarea.style.height = textarea.scrollHeight + "px";
                  textarea.style.minHeight = "22px";
                  textarea.style.maxHeight = "120px";
                  textarea.style.overflowY = "hidden";
                  textarea.style.opacity = "1";
                  textarea.style.visibility = "visible";
                  textarea.style.position = "relative";
                  textarea.style.display = "block";
               }
               
               if (!isApiKeyPage) {
                  // Only apply these styles when not on API key page
                  textarea.style.backgroundColor = "#ffffff";
                  textarea.style.borderRadius = "4px"; // All corners rounded
                  textarea.style.padding = "8px"; // Maintain consistent padding
               }

               // Update parent containers for a consistent layout
               var tempParent = textarea.parentElement;
               while (tempParent && tempParent.classList) {
                  if (tempParent.classList.contains("search") || 
                     tempParent.classList.contains("rstheme_center") || 
                     tempParent.classList.contains("searchBoxContainer") || 
                     tempParent.classList.contains("searchBoxContainer2")) {
                     
                     if (isApiKeyPage) {
                        // Hide elements on API key page
                        tempParent.style.height = "0px";
                        tempParent.style.minHeight = "0px";
                        tempParent.style.overflow = "hidden";
                        tempParent.style.opacity = "0";
                        tempParent.style.visibility = "hidden";
                        tempParent.style.position = "absolute";
                     } else {
                        // Normal behavior
                        tempParent.style.height = "auto";
                        tempParent.style.minHeight = "22px";
                        tempParent.style.marginBottom = "0px";
                        tempParent.style.marginTop = "0px";
                        tempParent.style.display = "flex";
                        tempParent.style.flexDirection = "column";
                        tempParent.style.justifyContent = "flex-end";
                        tempParent.style.backgroundColor = "#ffffff"; // White background
                        tempParent.style.zIndex = "100";
                        tempParent.style.position = "relative";
                        tempParent.style.border = "none"; // No inner border
                        tempParent.style.borderRadius = "0px"; // No inner rounding
                        tempParent.style.opacity = "1";
                        tempParent.style.visibility = "visible";
                        tempParent.style.overflow = "visible";
                     }
                  }
                  if (tempParent.classList.contains("rstudio-AiSearchContainer")) {
                     if (isApiKeyPage) {
                        // Hide on API key page
                        tempParent.style.height = "0px";
                        tempParent.style.minHeight = "0px";
                        tempParent.style.maxHeight = "0px";
                        tempParent.style.overflow = "hidden";
                        tempParent.style.opacity = "0";
                        tempParent.style.visibility = "hidden";
                        tempParent.style.position = "absolute";
                        tempParent.style.paddingTop = "0px";
                        tempParent.style.paddingBottom = "0px";
                        tempParent.style.marginTop = "0px";
                        tempParent.style.marginBottom = "0px";
                        tempParent.style.display = "none";
                     } else {
                        // Normal behavior for search elements
                        tempParent.style.height = "auto";
                        tempParent.style.minHeight = "100px";
                        tempParent.style.maxHeight = "";
                        tempParent.style.paddingBottom = "0px";
                        tempParent.style.paddingTop = "0px";
                        tempParent.style.marginTop = "0px";
                        tempParent.style.marginBottom = "0px";
                        tempParent.style.backgroundColor = "#ffffff"; // White background
                        tempParent.style.border = "none"; // No container border
                        tempParent.style.borderRadius = "0px"; // No container rounding
                        tempParent.style.opacity = "1";
                        tempParent.style.visibility = "visible";
                        tempParent.style.overflow = "visible";
                        tempParent.style.position = "relative";
                        tempParent.style.display = "block";
                        tempParent.style.zIndex = "100";
                     }
                  }
                  
                  tempParent = tempParent.parentElement;
               }

               // Dispatch a resize event to prompt any additional layout updates
               if (typeof $wnd.CustomEvent === 'function') {
                  var event = new $wnd.CustomEvent('resize', {bubbles: true});
                  textarea.dispatchEvent(event);
               }
            }, 50); // 50ms delay for debouncing
         };
         
         // Add event listeners for automatic resizing
         textarea.addEventListener("input", resizeTextarea);
         textarea.addEventListener("change", resizeTextarea);
         textarea.addEventListener("focus", resizeTextarea);
         
         // Add focus event listener to remove any borders that might appear
         textarea.addEventListener("focus", function(e) {
            e.target.style.outline = "none";
            e.target.style.border = "none";
            e.target.style.boxShadow = "none";
            e.target.style.outlineStyle = "none";
         });
         
         // Add blur event listener to ensure borders stay removed
         textarea.addEventListener("blur", function(e) {
            e.target.style.outline = "none";
            e.target.style.border = "none";
            e.target.style.boxShadow = "none";
            e.target.style.outlineStyle = "none";
         });
         
         // Set up a global handler for AI search (if not already defined)
         if (!$wnd.aiSearchHandler) {
            $wnd.aiSearchHandler = $entry(function(value) {
               var thiz = @org.rstudio.studio.client.workbench.views.ai.AiPane::getCurrentInstance()();
               if (thiz && value !== null && value !== undefined) {
                  var handlers = thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::eventHandlers_;
                  handlers.@org.rstudio.studio.client.workbench.views.ai.AiPaneEventHandlers::handleAiSearchFromJS(Ljava/lang/String;)(value);
               }
            });
         }
         
         // Handle key events so that Enter triggers search (unless combined with Ctrl/Cmd for newline)
         textarea.addEventListener("keydown", function(e) {
            if (e.keyCode === 13 && !e.ctrlKey && !e.altKey && !e.shiftKey && !e.metaKey) {
               e.preventDefault();
               if ($wnd.aiSearchHandler && textarea && textarea.value !== null && textarea.value !== undefined) {
                  $wnd.aiSearchHandler(textarea.value);
               }
            } else if (e.keyCode === 13 && (e.ctrlKey || e.metaKey)) {
               // Allow Ctrl+Enter or Cmd+Enter to insert a newline
            }
         });
         
         // Perform an initial resize after a brief timeout
         setTimeout(resizeTextarea, 0);
         
         // Recalculate size on window resize with debouncing
         var windowResizeTimeout = null;
         $wnd.addEventListener("resize", function() {
            // Cancel any pending resize
            if (windowResizeTimeout) {
               clearTimeout(windowResizeTimeout);
            }
            
            // Schedule a new resize with a delay
            windowResizeTimeout = setTimeout(function() {
               windowResizeTimeout = null;
               resizeTextarea();
            }, 100); // 100ms delay for window resize debouncing
         });
         
         return textarea;
      }
      
      return input;
   }-*/;

   /**
    * Updates the size of the South panel in the DockLayoutPanel
    */
   public void updateSouthPanelSize(int newHeight) {
      // Get the main panel and search container from the AiPane
      DockLayoutPanel mainPanel = pane_.getMainPanel();
      SimplePanel searchContainer = pane_.getSearchContainer();
      
      // Only update if container is visible
      if (mainPanel != null && searchContainer != null && searchContainer.isVisible()) {
         try {
            mainPanel.setWidgetSize(searchContainer, newHeight + 5);
            mainPanel.forceLayout();
         } catch (Exception e) {
            // Ignore errors
         }
      }
   }
   
   /**
    * Ensures all search box elements have consistent positioning
    * (Added back for compatibility with AiPane.java)
    */
   public native void fixSearchBoxPositioning(Element containerElement) /*-{
      try {
         // Find all search boxes in the container
         var searchBoxes = containerElement.querySelectorAll(".searchBox");
         for (var i = 0; i < searchBoxes.length; i++) {
            var box = searchBoxes[i];
            box.style.width = "100%";
         }
         
         // Fix positioning of searchBoxContainer elements
         var containers = containerElement.querySelectorAll(".searchBoxContainer");
         for (var i = 0; i < containers.length; i++) {
            var container = containers[i];
            container.style.position = "absolute";
            container.style.top = "0";
            container.style.bottom = "1px";
            container.style.left = "18px";
            container.style.right = "18px";
         }
         
         // Fix positioning of searchBoxContainer2 elements
         var containers2 = containerElement.querySelectorAll(".searchBoxContainer2");
         for (var i = 0; i < containers2.length; i++) {
            var container = containers2[i];
            container.style.position = "absolute";
            container.style.top = "0";
            container.style.bottom = "1px";
            container.style.left = "0";
            container.style.right = "2px";
         }
         
         // Fix positioning of search elements
         var searchElems = containerElement.querySelectorAll(".search");
         for (var i = 0; i < searchElems.length; i++) {
            var elem = searchElems[i];
            elem.style.position = "relative";
            elem.style.top = "-1px";
            elem.style.marginRight = "8px";
            elem.style.width = "100%";
         }
         
         // Fix all textareas in the container
         var textareas = containerElement.querySelectorAll("textarea");
         for (var i = 0; i < textareas.length; i++) {
            var textarea = textareas[i];
            textarea.style.boxSizing = "border-box";
         }
      } catch (e) {
         console.error("Error in fixSearchBoxPositioning: " + e);
      }
   }-*/;
   
   /**
    * Immediately restores scroll position without any checks or animations
    * Used during initial load to prevent flashing at bottom position
    */
   public native void restoreScrollPositionImmediately(WindowEx window) /*-{
      try {
         // Try to get the saved scroll position from sessionStorage
         var savedPosition = $wnd.sessionStorage.getItem('ai_preserve_scroll');
         var thiz = this;
         
         if (savedPosition) {
            // Remove the item from sessionStorage after reading it
            $wnd.sessionStorage.removeItem('ai_preserve_scroll');
            
            // Immediately apply the scroll position without any checks or animations
            if (savedPosition === 'bottom') {
               // Scroll to bottom immediately
               window.scrollTo(0, window.document.body.scrollHeight);
            } else {
               // Parse the saved position as a number and scroll to it
               var position = parseInt(savedPosition, 10);
               if (!isNaN(position)) {
                  window.scrollTo(0, position);
               }
            }
         } else {
            // Check for conversation-specific position asynchronously
            thiz.@org.rstudio.studio.client.workbench.views.ai.AiPaneScroll::getConversationIdAsync(*)(function(conversationId) {
               if (conversationId > 0) {
                  var storedData = $wnd.localStorage.getItem('aiConversationScrollPositions');
                  if (storedData) {
                     try {
                        var scrollPositions = JSON.parse(storedData);
                        var position = scrollPositions["conv" + conversationId];
                        
                        if (position !== undefined) {
                           if (position === "bottom") {
                              // Scroll to bottom immediately
                              window.scrollTo(0, window.document.body.scrollHeight);
                           } else {
                              var scrollPos = parseInt(position, 10);
                              if (!isNaN(scrollPos)) {
                                 window.scrollTo(0, scrollPos);
                              }
                           }
                        }
                     } catch (e) {
                        // Ignore JSON parsing errors
                     }
                  }
               }
            });
         }
      } catch (e) {
         // Ignore errors
         console.error("Error in restoreScrollPositionImmediately:", e);
      }
   }-*/;

   /**
    * Checks if there's a preserved scroll position in localStorage for the current conversation
    * and restores it if found
    */
   public native void restoreScrollPositionIfNeeded(WindowEx window) /*-{
      try {
         var thiz = this;
         
         // Check for the respect user scroll flag first - this overrides everything
         var respectUserScroll = window.localStorage.getItem('ai_respect_user_scroll');
         if (respectUserScroll === 'true') {
            // Clear the flag immediately
            window.localStorage.removeItem('ai_respect_user_scroll');
            console.log("Respecting user's scroll position - not modifying scroll");
            
            // Check for saved user scroll position from manual scrolling
            var userScrollPos = window.sessionStorage.getItem('user_scroll_position');
            if (userScrollPos) {
               // We have a saved position from user scrolling
               // Convert to number and restore exactly
               var scrollPos = parseInt(userScrollPos, 10);
               if (!isNaN(scrollPos)) {
                  console.log("Restoring user's manual scroll position: " + scrollPos);
                  // Restore the exact scroll position the user had
                  window.scrollTo(0, scrollPos);
               }
               // Clear the stored position to avoid affecting future loads
               window.sessionStorage.removeItem('user_scroll_position');
            }
            
            // Clear any other scroll position flags to avoid interference
            window.localStorage.removeItem('ai_force_scroll_bottom');
            window.localStorage.removeItem('ai_scroll_after_reload');
            window.sessionStorage.removeItem('ai_preserve_scroll');
            $wnd._pendingScrollTarget = null;
            
            // Exit early without changing scroll position at all
            return;
         }
         
         // NEW: First check for the force scroll bottom flag that overrides all other scroll logic
         var forceScrollBottom = window.localStorage.getItem('ai_force_scroll_bottom');
         if (forceScrollBottom === 'true') {
            // Clear the flag immediately
            window.localStorage.removeItem('ai_force_scroll_bottom');
            
            // Scroll to bottom regardless of other settings
            window.scrollTo(0, window.document.body.scrollHeight);
            
            // Exit early - override all other scroll restoration
            return;
         }
         
         // Next check if there's a global preserve_scroll value from a page refresh
         var preservedScrollPos = window.sessionStorage.getItem('ai_preserve_scroll');
         if (preservedScrollPos) {
            // Clear the stored value immediately to prevent it affecting future loads
            window.sessionStorage.removeItem('ai_preserve_scroll');
            
            // Check if we have a special instruction to scroll to bottom
            if (preservedScrollPos === 'bottom') {
               // Before scrolling to bottom, check if user is already near bottom
               var scrollPosition = window.pageYOffset || window.document.documentElement.scrollTop;
               var totalHeight = window.document.body.scrollHeight;
               var windowHeight = window.innerHeight;
               
               // Only scroll if user is already near the bottom (within 200px or at >80% of scroll distance)
               if ((totalHeight - scrollPosition - windowHeight) < 200 || 
                   (totalHeight > windowHeight && scrollPosition / (totalHeight - windowHeight) > 0.8)) {
                  // Scroll to bottom immediately without animation
                  window.scrollTo(0, window.document.body.scrollHeight);
               }
            } else {
               // It's a numeric position, convert to number and restore exactly
               var scrollPos = parseInt(preservedScrollPos, 10);
               if (!isNaN(scrollPos)) {
                  // Restore the specified scroll position immediately
                  window.scrollTo(0, scrollPos);
               }
            }
            return; // Exit early if we restored from global position
         }
         
         // Check for the scroll_after_reload flag
         var scrollAfterReload = window.localStorage.getItem('ai_scroll_after_reload');
         if (scrollAfterReload) {
            // Clear the flag immediately
            window.localStorage.removeItem('ai_scroll_after_reload');
            
            if (scrollAfterReload === 'bottom') {
               // Scroll to bottom immediately without any conditions
               window.scrollTo(0, window.document.body.scrollHeight);
               return;
            }
         }
         
         // Check for user scroll position 
         var userScrollPos = window.sessionStorage.getItem('user_scroll_position');
         if (userScrollPos) {
            // We have a saved position from user scrolling
            // Convert to number and restore exactly
            var scrollPos = parseInt(userScrollPos, 10);
            if (!isNaN(scrollPos)) {
               console.log("Restoring user's manual scroll position: " + scrollPos);
               // Restore the exact scroll position the user had
               window.scrollTo(0, scrollPos);
               // Clear the stored position to avoid affecting future loads
               window.sessionStorage.removeItem('user_scroll_position');
               return;
            }
         }
         
         // If we get here, check for conversation-specific scroll position
         
         // Get the current conversation ID asynchronously
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPaneScroll::getConversationIdAsync(*)(function(conversationId) {
            if (conversationId > 0) {
               // Get stored scroll positions
               var storedData = $wnd.localStorage.getItem('aiConversationScrollPositions');
               if (storedData) {
                  try {
                     // Parse the stored data
                     var scrollPositions = JSON.parse(storedData);
                     
                     // Look for this conversation's scroll position
                     var position = scrollPositions["conv" + conversationId];
                     
                     if (position !== undefined) {
                        if (position === "bottom") {
                           // Scroll to bottom behavior - immediately
                           window.scrollTo(0, window.document.body.scrollHeight);
                        } else {
                           // It's a specific position
                           var scrollPos = parseInt(position, 10);
                           if (!isNaN(scrollPos)) {
                              window.scrollTo(0, scrollPos);
                              console.log("Restored scroll position " + scrollPos + " for conversation " + conversationId);
                           }
                        }
                     } else {
                        // No position for this conversation, scroll to bottom by default
                        window.scrollTo(0, window.document.body.scrollHeight);
                     }
                  } catch (e) {
                     // If parsing fails, scroll to bottom
                     window.scrollTo(0, window.document.body.scrollHeight);
                     console.error("Error parsing stored scroll positions:", e);
                  }
               } else {
                  // No stored positions, scroll to bottom
                  window.scrollTo(0, window.document.body.scrollHeight);
                  console.log("No stored scroll positions, scrolling to bottom");
               }
            } else {
               // No valid conversation ID, scroll to bottom by default
               window.scrollTo(0, window.document.body.scrollHeight);
            }
         });
      } catch (e) {
         // Ignore errors accessing storage
         console.error("Error accessing storage:", e);
      }
   }-*/;
   
   /**
    * Gets the conversation ID from the AiPane asynchronously
    */
   private void getConversationId(final ConversationIdCallback callback) {
      pane_.getCurrentConversationId(new ServerRequestCallback<Double>() {
         @Override
         public void onResponseReceived(Double conversationId) {
            callback.onConversationId(conversationId != null ? conversationId.intValue() : 0);
         }
         
         @Override
         public void onError(ServerError error) {
            // Default to 0 on error
            callback.onConversationId(0);
         }
      });
   }
   
   /**
    * Callback interface for conversation ID retrieval
    */
   private interface ConversationIdCallback {
      void onConversationId(int conversationId);
   }
   
   /**
    * Async wrapper for JSNI to get conversation ID with JavaScript callback
    */
   private void getConversationIdAsync(final JavaScriptObject callback) {
      getConversationId(new ConversationIdCallback() {
         @Override
         public void onConversationId(int conversationId) {
            callJavaScriptCallback(callback, conversationId);
         }
      });
   }
   
   /**
    * Native method to call JavaScript callback with conversation ID
    */
   private native void callJavaScriptCallback(JavaScriptObject callback, int conversationId) /*-{
      callback(conversationId);
   }-*/;
   
   /**
    * Helper method to check if a widget is a child of a panel
    */
   private boolean isWidgetChild(DockLayoutPanel panel, Widget widget) {
      int count = panel.getWidgetCount();
      for (int i = 0; i < count; i++) {
         if (panel.getWidget(i) == widget) {
            return true;
         }
      }
      return false;
   }
   
   /**
    * Log errors to console for debugging
    */
   private native void consoleError(String message) /*-{
      console.error(message);
   }-*/;
   
   /**
    * Gets the scroll timer from AiPane
    */
   private Timer getScrollTimer() {
      return pane_.getScrollTimer();
   }
   
   private final AiPane pane_;
} 