/*
 * AiPaneLifecycle.java
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

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.FlowPanel;

import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.dom.ElementEx;
import org.rstudio.core.client.dom.IFrameElementEx;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.studio.client.workbench.views.ai.events.AiNavigateEvent;

/**
 * Handles lifecycle and UI management methods for AiPane
 */
public class AiPaneLifecycle {
   
   private final AiPane pane_;
   private boolean initialized_ = false;
   private boolean selected_ = true;
   
   public AiPaneLifecycle(AiPane pane) {
      pane_ = pane;
   }
   
   public void onBeforeUnselected() {
      selected_ = false;
   }

   public void onSelected() {
      selected_ = true;

      if (pane_.getScrollPos() == null)
         return;

      IFrameElementEx iframeEl = pane_.getIFrameEx();
      if (iframeEl == null)
         return;

      WindowEx windowEl = iframeEl.getContentWindow();
      if (windowEl == null)
         return;

      windowEl.setScrollPosition(pane_.getScrollPos());
   }

   public void setFocus() {
      pane_.focus();
   }

   public void onResize() {
      manageTitleLabelMaxSize();
      
      // Ensure search widget takes up full width when resized
      managePanelWidth();
   }

   private void manageTitleLabelMaxSize() {
      if (pane_.getTitleLabel() != null) {
         int offsetWidth = pane_.getOffsetWidth();
         if (offsetWidth > 0) {
            int newWidth = offsetWidth - 25;
            if (newWidth > 0) {
               pane_.getTitleLabel().getElement().getStyle().setPropertyPx("maxWidth", newWidth);
               
               // Also apply to the overlay title if it exists
               if (pane_.overlayTitle_ != null) {
                  pane_.overlayTitle_.getElement().getStyle().setPropertyPx("maxWidth", newWidth);
               }
            }
         }
      }
   }

   private void managePanelWidth() {
      if (pane_.getSearchWidget() != null) {
         final Widget searchWidgetWidget = (Widget)pane_.getSearchWidget();
         
         // Style all elements inside search widget to take full width
         Scheduler.get().scheduleDeferred(new ScheduledCommand() {
            @Override
            public void execute() {
               // Get all input elements and ensure they're full width
               NodeList<Element> inputs = searchWidgetWidget.getElement().getElementsByTagName("input");
               for (int i = 0; i < inputs.getLength(); i++) {
                  Element input = inputs.getItem(i);
                  input.getStyle().setWidth(100, Unit.PCT);
                  input.setAttribute("placeholder", pane_.getConstants().searchAiLabel());
                  
                  // Configure input for multiline support
                  pane_.configureInputForMultiline(input);
               }
               
               // Set full width for all table elements
               NodeList<Element> tables = searchWidgetWidget.getElement().getElementsByTagName("table");
               for (int i = 0; i < tables.getLength(); i++) {
                  Element table = tables.getItem(i);
                  table.getStyle().setWidth(100, Unit.PCT);
               }
               
               // Find and style the suggest box elements
               NodeList<Element> divs = searchWidgetWidget.getElement().getElementsByTagName("div");
               for (int i = 0; i < divs.getLength(); i++) {
                  Element div = divs.getItem(i);
                  if (div.getClassName() != null && 
                      div.getClassName().contains("gwt-SuggestBox")) {
                     div.getStyle().setWidth(100, Unit.PCT);
                  }
                  else if (div.getClassName() != null && 
                          div.getClassName().contains("search")) {
                     div.getStyle().setWidth(100, Unit.PCT);
                     div.getStyle().setMarginRight(0, Unit.PX);
                     // Remove padding that creates the slots/bars
                     div.getStyle().setPaddingLeft(0, Unit.PX);
                     div.getStyle().setPaddingRight(0, Unit.PX);
                  }
                  else if (div.getClassName() != null && 
                          div.getClassName().contains("searchBoxContainer")) {
                     div.getStyle().setWidth(100, Unit.PCT);
                     // Remove any border or padding that might create slots
                     div.getStyle().setBorderStyle(Style.BorderStyle.NONE);
                     div.getStyle().setPaddingLeft(0, Unit.PX);
                     div.getStyle().setPaddingRight(0, Unit.PX);
                  }
               }
               
               // Remove any icon containers or slots
               NodeList<Element> spans = searchWidgetWidget.getElement().getElementsByTagName("span");
               for (int i = 0; i < spans.getLength(); i++) {
                  Element span = spans.getItem(i);
                  if (span.getClassName() != null && 
                      (span.getClassName().contains("icon") || 
                       span.getClassName().contains("slot"))) {
                     span.getStyle().setDisplay(Style.Display.NONE);
                  }
               }
            }
         });
      }
   }

   public void onLoad() {
      if (!initialized_) {
         initialized_ = true;

         initAiCallbacks();

         Scheduler.get().scheduleDeferred(new ScheduledCommand() {
            public void execute() {
               manageTitleLabelMaxSize();
            }
         });
      }

      // Load context items
      NodeList<Element> panels = Document.get().getElementsByTagName("div");
      if (panels != null) {
         for (int i = 0; i < panels.getLength(); i++) {
            Element panel = panels.getItem(i);
            if (panel.getClassName() != null && 
                panel.getClassName().contains("ai-selected-files-panel")) {
               // Found the panel
               for (int j = 0; j < RootPanel.get().getWidgetCount(); j++) {
                  Widget widget = RootPanel.get().getWidget(j);
                  if (widget instanceof FlowPanel && 
                      widget.getElement().equals(panel)) {
                     pane_.getAiContext().loadContextItems((FlowPanel)widget);
                     return;
                  }
               }
               break;
            }
         }
      }
   }
   
   public void focusFindTextBox() {
      pane_.findTextBox_.focus();
      pane_.findTextBox_.selectAll();
   }
   
   public final native void initAiCallbacks() /*-{
      function addEventHandler(subject, eventName, handler) {
         if (subject.addEventListener) {
            subject.addEventListener(eventName, handler, false);
         }
         else {
            subject.attachEvent(eventName, handler);
         }
      }

      var thiz = this;
      var pane = this.@org.rstudio.studio.client.workbench.views.ai.AiPaneLifecycle::pane_;
      
      $wnd.aiNavigated = function(document, win) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPaneLifecycle::aiNavigated(Lcom/google/gwt/dom/client/Document;)(document);
         addEventHandler(win, "unload", function () {
            thiz.@org.rstudio.studio.client.workbench.views.ai.AiPaneLifecycle::unload()();
         });
      };
      
      $wnd.aiNavigate = function(url) {
         if (url.length)
            pane.@org.rstudio.studio.client.workbench.views.ai.AiPane::showAi(Ljava/lang/String;)(url);
      };
      
      $wnd.aiKeydown = function(e) {
         pane.@org.rstudio.studio.client.workbench.views.ai.AiPane::eventHandlers_
             .@org.rstudio.studio.client.workbench.views.ai.AiPaneEventHandlers::handleKeyDown(Lcom/google/gwt/dom/client/NativeEvent;)(e);
      };      

   }-*/;
   
   private void aiNavigated(Document doc)
   {
      NodeList<Element> elements = doc.getElementsByTagName("a");
      for (int i = 0; i < elements.getLength(); i++)
      {
         ElementEx a = (ElementEx) elements.getItem(i);
         String href = a.getAttribute("href", 2);
         if (href == null)
            continue;

         if (href.contains(":") || href.endsWith(".pdf"))
         {
            // external links
            AnchorElement aElement = a.cast();
            aElement.setTarget("_blank");
         }
         else
         {
            // Internal links need to be handled in JavaScript so that
            // they can participate in virtual session history. This
            // won't have any effect for right-click > Show in New Window
            // but that's a good thing.
            a.setAttribute(
                  "onclick",
                  "window.parent.aiNavigate(this.href); return false");
         }
      }

      String effectiveTitle = getDocTitle(doc);
      pane_.title_.setText(effectiveTitle);
      pane_.fireEvent(new AiNavigateEvent(doc.getURL(), effectiveTitle));
   }

   private String getDocTitle(Document doc)
   {
      String docUrl = StringUtil.notNull(doc.getURL());
      String docTitle = doc.getTitle();

      String previewPrefix = new String("/ai/preview?file=");
      int previewLoc = docUrl.indexOf(previewPrefix);
      if (previewLoc != -1)
      {
         String file = StringUtil.substring(docUrl, previewLoc + previewPrefix.length());
         file = com.google.gwt.http.client.URL.decodeQueryString(file);
         FileSystemItem fsi = FileSystemItem.createFile(file);
         docTitle = fsi.getName();
      }
      else if (StringUtil.isNullOrEmpty(docTitle))
      {
         String url = new String(docUrl);
         url = url.split("\\?")[0];
         url = url.split("#")[0];
         String[] chunks = url.split("/");
         docTitle = chunks[chunks.length - 1];
      }

      return docTitle;
   }

   private void unload()
   {
      pane_.title_.setText("");
   }
   
   public boolean isSelected() {
      return selected_;
   }

   /**
    * Adds a user message to the conversation display immediately.
    * This ensures the user sees their message right away before waiting for the server response.
    * Uses inline styles to ensure consistent display even before CSS is fully loaded.
    * 
    * @param window The content window containing the conversation display
    * @param message The user's message to display
    */
   public native void addUserMessageToDisplay(WindowEx window, String message) /*-{
      if (!window || !window.document) return;
      
      // Escape HTML special characters to prevent XSS
      message = message
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;")
         .replace(/"/g, "&quot;")
         .replace(/'/g, "&#039;");
      
      // Explicitly convert newlines to <br> tags to preserve line breaks in HTML
      message = message.replace(/\n/g, "<br>");
      
      // Convert URLs to clickable links
      message = message.replace(
         /(https?:\/\/[^\s]+)/g, 
         '<a href="$1" target="_blank">$1</a>'
      );
      
      // Add CSS styles to head if they don't exist yet
      if (!window.document.getElementById('ai-conversation-styles')) {
         var styleEl = window.document.createElement('style');
         styleEl.id = 'ai-conversation-styles';
         styleEl.textContent = 
            'body { font-family: sans-serif; margin: 12px; }' +
            '.message { margin-bottom: 12px; padding: 8px; font-family: sans-serif; font-size: 14px; }' +
            '.user { background-color: #e6e6e6; border-radius: 5px; display: inline-block; float: right; max-width: 100%; word-wrap: break-word; }' +
            '.assistant { background-color: transparent; text-align: left; word-wrap: break-word; max-width: 100%; }' +
            '.user-container { width: 100%; overflow: hidden; text-align: right; }' +
            '.text { font-family: sans-serif; font-size: 14px; line-height: 1.4; white-space: pre-wrap; }' +
            '.user { text-align: right; }' +
            '.assistant { text-align: left; }' +
            '.user .text { text-align: left; max-width: 100%; }' +
            '.code-block { background-color: #f5f5f5; border-radius: 5px; padding: 4px; margin: 2px 0; font-family: monospace; white-space: pre; overflow-x: auto; }' +
            '.code-block pre { margin: 0; padding: 0; }' +
            '.editable-pre { min-height: 1em; outline: none; }' +
            '.editable-pre:focus { background-color: #f5f5f5; border: 1px solid #ccc; }' +
            'br { display: block; content: ""; margin-top: 0.5em; }';
         
         window.document.head.appendChild(styleEl);
      }
      
      // Create the new message element with proper classes only (no inline styles)
      var userContainer = window.document.createElement("div");
      userContainer.className = "user-container";
      // Add a special attribute to mark this as client-generated
      userContainer.setAttribute("data-client-generated", "true");
      
      var messageDiv = window.document.createElement("div");
      messageDiv.className = "message user";
      
      var textDiv = window.document.createElement("div");
      textDiv.className = "text";
      textDiv.innerHTML = message;
      
      messageDiv.appendChild(textDiv);
      userContainer.appendChild(messageDiv);
      
      // Find the best location to insert the message - just before assistant response if any
      var assistantContainers = window.document.querySelectorAll('.assistant-container');
      var lastAssistantContainer = assistantContainers.length > 0 ? 
         assistantContainers[assistantContainers.length - 1] : null;
      
      if (lastAssistantContainer && lastAssistantContainer.nextSibling) {
         // Insert before any pending/current assistant message
         window.document.body.insertBefore(userContainer, lastAssistantContainer.nextSibling);
      } else {
         // Add the message to the end of the document
         window.document.body.appendChild(userContainer);
      }
      
      // Force a reflow/repaint to ensure the message is shown immediately
      void userContainer.offsetWidth;
      
      // Note: Scrolling is now handled by AiStreamingPanel.createUserMessageSynchronously()
      
      // Store the last user message in a window variable for debugging
      window._lastUserMessage = {
         text: message,
         timestamp: new Date().getTime()
      };
   }-*/;
} 