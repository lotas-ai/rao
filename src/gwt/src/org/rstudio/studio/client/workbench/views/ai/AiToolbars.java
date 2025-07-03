/*
 * AiToolbars.java
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

import org.rstudio.core.client.ElementIds;
import org.rstudio.core.client.command.KeyboardShortcut;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.core.client.theme.res.ThemeStyles;
import org.rstudio.studio.client.common.AutoGlassPanel;
import org.rstudio.core.client.widget.FindTextBox;
import org.rstudio.core.client.widget.RStudioThemedFrame;
import org.rstudio.core.client.widget.SearchDisplay;
import org.rstudio.core.client.widget.SearchWidget;
import org.rstudio.core.client.widget.SmallButton;
import org.rstudio.core.client.widget.Toolbar;
import org.rstudio.core.client.widget.ToolbarButton;
import org.rstudio.core.client.events.SelectionCommitEvent;
import org.rstudio.core.client.Debug;
import org.rstudio.studio.client.workbench.commands.Commands;

import org.rstudio.studio.client.workbench.views.ai.search.AiSearch;
import org.rstudio.studio.client.workbench.views.ai.widgets.AiStreamingPanel;
import org.rstudio.studio.client.workbench.views.ai.AiViewManager;
import org.rstudio.studio.client.workbench.views.ai.model.Link;
import org.rstudio.core.client.DragDropReceiver;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.views.ai.model.TextMatchResult;
import com.google.gwt.core.client.JsArrayString;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.application.events.EventBus;
import com.google.gwt.dom.client.NativeEvent;
import elemental2.core.JsArray;
import elemental2.dom.DataTransfer;
import elemental2.dom.File;
import elemental2.dom.FileList;
import jsinterop.base.Js;
import java.util.ArrayList;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ClientBundle.Source;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Provider;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.core.client.JavaScriptObject;

/**
 * Provides the toolbars and main widget for the AI pane.
 */
public class AiToolbars
{
   public AiToolbars(AiPane pane, 
                     Provider<AiSearch> searchProvider,
                     Commands commands,
                     AiToolbarLinkMenu history,
                     RStudioThemedFrame frame,
                     RStudioThemedFrame backgroundFrame,
                     AiPaneScroll scrollHandler,
                     AiContext aiContext,
                     EventBus eventBus)
   {
      pane_ = pane;
      searchProvider_ = searchProvider;
      commands_ = commands;
      history_ = history;
      frame_ = frame;
      backgroundFrame_ = backgroundFrame;
      scrollHandler_ = scrollHandler;
      aiContext_ = aiContext;
      eventBus_ = eventBus;
      constants_ = GWT.create(AiConstants.class);
      
      // Initialize streaming panel
      streamingPanel_ = new AiStreamingPanel(eventBus_);
      
      // Create iframe container for API management
      iframeContainer_ = new SimplePanel();
      
      // Initialize view manager (will be set up fully in createMainWidget)
      viewManager_ = null;
   }
   
   public Widget createMainWidget()
   {
      if (searchProvider_ != null) {
         AiSearch aiSearch = searchProvider_.get();
         if (aiSearch != null) {
            SearchDisplay searchDisplay = aiSearch.getSearchWidget();
         }
      }
      
      // Create bottom panel for search widget
      searchWidget_ = searchProvider_.get().getSearchWidget();
      Widget searchWidgetWidget = (Widget)searchWidget_;
      
      // Create a simpler container with clean styling for the search content
      FlowPanel contentPanel = new FlowPanel();
      contentPanel.setStyleName("rstudio-AiSearchContentPanel");
      
      // Add image drag and drop support to the entire content panel
      // This allows users to drag images anywhere in the AI pane
      addPerCharacterTracking(contentPanel.getElement());
      
      // Add a placeholder label at the top with light gray rounded rectangle (top corners only)
      FlowPanel topPlaceholderPanel = new FlowPanel();
      topPlaceholderPanel.getElement().getStyle().setBackgroundColor("#f0f0f0");
      topPlaceholderPanel.getElement().getStyle().setProperty("borderTopLeftRadius", "4px");
      topPlaceholderPanel.getElement().getStyle().setProperty("borderTopRightRadius", "4px");
      topPlaceholderPanel.getElement().getStyle().setProperty("borderBottomLeftRadius", "0px");
      topPlaceholderPanel.getElement().getStyle().setProperty("borderBottomRightRadius", "0px");
      topPlaceholderPanel.getElement().getStyle().setProperty("borderTop", "1px solid #aaaaaa");
      topPlaceholderPanel.getElement().getStyle().setProperty("borderLeft", "1px solid #aaaaaa");
      topPlaceholderPanel.getElement().getStyle().setProperty("borderRight", "1px solid #aaaaaa");
      topPlaceholderPanel.getElement().getStyle().setProperty("borderBottom", "none");
      topPlaceholderPanel.getElement().getStyle().setPadding(0, Unit.PX);
      topPlaceholderPanel.getElement().getStyle().setPaddingTop(2, Unit.PX);
      topPlaceholderPanel.getElement().getStyle().setPaddingBottom(0, Unit.PX);
      topPlaceholderPanel.getElement().getStyle().setMarginBottom(0, Unit.PX);
      topPlaceholderPanel.getElement().getStyle().setProperty("minHeight", "22px");
      topPlaceholderPanel.getElement().getStyle().setProperty("height", "auto");
      topPlaceholderPanel.getElement().getStyle().setProperty("maxHeight", "50px");
      topPlaceholderPanel.getElement().getStyle().setProperty("overflowY", "hidden");
      topPlaceholderPanel.getElement().getStyle().setProperty("overflowX", "auto");
      topPlaceholderPanel.getElement().getStyle().setProperty("boxSizing", "border-box");
      topPlaceholderPanel.getElement().getStyle().setProperty("display", "flex");
      topPlaceholderPanel.getElement().getStyle().setProperty("flexWrap", "wrap");
      topPlaceholderPanel.getElement().getStyle().setProperty("alignItems", "center");
      topPlaceholderPanel.getElement().getStyle().setMarginLeft(20, Unit.PX);
      topPlaceholderPanel.getElement().getStyle().setMarginRight(20, Unit.PX);
      
      // Add drag and drop support to the context bar
      addDragDropSupport(topPlaceholderPanel);
      
      // Create the @ button for file attachment
      attachFileButton_ = new Label();
      attachFileButton_.getElement().setInnerHTML("<span style='position: relative; top: -1px;'>@</span>&nbsp;Add context");
      attachFileButton_.setStyleName("ai-attach-file-button");
      attachFileButton_.getElement().setAttribute("title", "Attach File or Directory for Context");
      Element attachButtonElement = attachFileButton_.getElement();
      attachButtonElement.getStyle().setProperty("display", "inline-flex");
      attachButtonElement.getStyle().setProperty("alignItems", "center");
      attachButtonElement.getStyle().setProperty("justifyContent", "center");
      attachButtonElement.getStyle().setProperty("backgroundColor", "white");
      attachButtonElement.getStyle().setProperty("border", "1px solid #cccccc");
      attachButtonElement.getStyle().setProperty("borderRadius", "3px");
      attachButtonElement.getStyle().setProperty("padding", "1px 5px");
      attachButtonElement.getStyle().setProperty("marginLeft", "3px");
      attachButtonElement.getStyle().setProperty("marginRight", "5px");
      attachButtonElement.getStyle().setProperty("cursor", "pointer");
      attachButtonElement.getStyle().setProperty("color", "#555555");
      attachButtonElement.getStyle().setProperty("fontSize", "11px");
      attachButtonElement.getStyle().setProperty("height", "18px");
      attachButtonElement.getStyle().setProperty("lineHeight", "14px");
      attachButtonElement.getStyle().setProperty("verticalAlign", "middle");
      attachButtonElement.getStyle().setProperty("position", "relative");
      attachButtonElement.getStyle().setProperty("top", "0px");
      
      // Create panel for selected files
      selectedFilesPanel_ = new FlowPanel();
      selectedFilesPanel_.setStyleName("ai-selected-files-panel");
      Element selectedFilesElement = selectedFilesPanel_.getElement();
      selectedFilesElement.getStyle().setProperty("display", "inline-flex");
      selectedFilesElement.getStyle().setProperty("flexWrap", "nowrap");
      selectedFilesElement.getStyle().setProperty("alignItems", "center");
      selectedFilesElement.getStyle().setProperty("overflowX", "auto");
      selectedFilesElement.getStyle().setProperty("overflowY", "hidden");
      selectedFilesElement.getStyle().setProperty("verticalAlign", "top");
      selectedFilesElement.getStyle().setProperty("minHeight", "18px");
      selectedFilesElement.getStyle().setProperty("marginTop", "0px");
      selectedFilesElement.getStyle().setProperty("marginBottom", "-3px");
      selectedFilesElement.getStyle().setProperty("maxWidth", "calc(100% - 30px)");
      selectedFilesElement.getStyle().setProperty("scrollbarWidth", "thin");
      selectedFilesElement.getStyle().setProperty("msOverflowStyle", "none");
      
      // Add scrollbar styling for WebKit browsers
      selectedFilesElement.getStyle().setProperty("webkitScrollbarHeight", "4px");
      selectedFilesElement.getStyle().setProperty("webkitScrollbarThumbColor", "#888888");
      selectedFilesElement.getStyle().setProperty("webkitScrollbarTrackColor", "#f0f0f0");
      
      // Add specific webkit scrollbar styles using direct JS
      pane_.addWebkitScrollbarStyles(selectedFilesElement);
      
      // Create a container for the @ button and selected files that ensures alignment
      FlowPanel attachmentContainer = new FlowPanel();
      attachmentContainer.getElement().getStyle().setProperty("display", "flex");
      attachmentContainer.getElement().getStyle().setProperty("alignItems", "flex-start");
      attachmentContainer.getElement().getStyle().setProperty("flexWrap", "nowrap");
      attachmentContainer.getElement().getStyle().setProperty("width", "100%");
      attachmentContainer.getElement().getStyle().setProperty("paddingTop", "0px");
      attachmentContainer.getElement().getStyle().setProperty("paddingBottom", "0px");
      attachmentContainer.getElement().getStyle().setProperty("overflowX", "auto");
      attachmentContainer.getElement().getStyle().setProperty("overflowY", "hidden");
      
      // Add the @ button to the attachment container
      attachmentContainer.add(attachFileButton_);
      
      // Add the selected files panel to the attachment container
      attachmentContainer.add(selectedFilesPanel_);
      
      // Add the attachment container to the gray bar
      topPlaceholderPanel.add(attachmentContainer);
      
      // Add the gray bar to the content panel
      contentPanel.add(topPlaceholderPanel);
      
      // Add click handler to the @ button to open file browser
      attachFileButton_.addClickHandler(new ClickHandler() {
         @Override
         public void onClick(ClickEvent event) {
            aiContext_.handleBrowseForFile(selectedFilesPanel_);
         }
      });
      
      // Create a container for both search input and toolbar with a shared border
      FlowPanel searchAndToolbarWrapper = new FlowPanel();
      searchAndToolbarWrapper.getElement().getStyle().setProperty("border", "1px solid #aaaaaa");
      searchAndToolbarWrapper.getElement().getStyle().setProperty("borderRadius", "8px");
      searchAndToolbarWrapper.getElement().getStyle().setProperty("margin", "0 10px");
      searchAndToolbarWrapper.getElement().getStyle().setProperty("overflow", "hidden");
      searchAndToolbarWrapper.getElement().getStyle().setProperty("backgroundColor", "#ffffff");
      searchAndToolbarWrapper.getElement().getStyle().setProperty("paddingBottom", "0px");
      
      // Style the search widget for a clean appearance
      searchWidgetWidget.getElement().getStyle().setWidth(100, Unit.PCT);
      searchWidgetWidget.getElement().getStyle().setBackgroundColor("#ffffff");
      searchWidgetWidget.getElement().getStyle().setPaddingLeft(5, Unit.PX);
      searchWidgetWidget.getElement().getStyle().setPaddingRight(5, Unit.PX);
      searchWidgetWidget.getElement().getStyle().setProperty("border", "none"); // Ensure no border on widget
      
      // Add the search widget to the wrapper
      searchAndToolbarWrapper.add(searchWidgetWidget);
      
      // Set placeholder text for the search input and apply proper styling
      NodeList<Element> inputs = searchWidgetWidget.getElement().getElementsByTagName("input");
      for (int i = 0; i < inputs.getLength(); i++) {
         Element input = inputs.getItem(i);
         input.setAttribute("placeholder", "Ask anything");
         
         // Completely remove all borders and outlines from the input
         input.getStyle().setProperty("border", "none !important");
         input.getStyle().setProperty("borderRadius", "0");
         input.getStyle().setProperty("boxShadow", "none !important");
         input.getStyle().setProperty("outlineStyle", "none !important");
         input.getStyle().setProperty("outline", "none !important");
         input.getStyle().setBackgroundColor("#ffffff");
         input.getStyle().setPadding(8, Unit.PX);
         input.getStyle().setWidth(100, Unit.PCT);
         input.getStyle().setProperty("boxSizing", "border-box");
         input.getStyle().setMarginLeft(0, Unit.PX);
         input.getStyle().setMarginRight(0, Unit.PX);
         
         // Configure input for multiline support
         pane_.configureInputForMultiline(input);
      }
      
      // Also apply focus styles to textarea elements that replace inputs
      NodeList<Element> textareas = searchWidgetWidget.getElement().getElementsByTagName("textarea");
      for (int i = 0; i < textareas.getLength(); i++) {
         Element textarea = textareas.getItem(i);
         
         // Remove focus borders from textareas
         textarea.getStyle().setProperty("border", "none !important");
         textarea.getStyle().setProperty("outline", "none !important");
         textarea.getStyle().setProperty("boxShadow", "none !important");
         textarea.getStyle().setProperty("outlineStyle", "none !important");
      }
      
      // Force all child elements to take full width and remove borders
      Element searchElement = searchWidgetWidget.getElement().getFirstChildElement();
      if (searchElement != null) {
         searchElement.getStyle().setWidth(100, Unit.PCT);
         searchElement.getStyle().setProperty("border", "none !important");
         searchElement.getStyle().setProperty("boxShadow", "none !important");
         searchElement.getStyle().setBackgroundColor("#ffffff");
         
         // Find all direct children of the search element and set them to 100% width
         NodeList<Element> children = searchElement.getChildNodes().cast();
         for (int i = 0; i < children.getLength(); i++) {
            if (Element.is(children.getItem(i))) {
               Element child = Element.as(children.getItem(i));
               child.getStyle().setWidth(100, Unit.PCT);
               child.getStyle().setProperty("border", "none !important");
               child.getStyle().setProperty("boxShadow", "none !important");
               child.getStyle().setBackgroundColor("#ffffff");
            }
         }
      }
      
      // Create a toolbar for the attachment button
      searchToolbar_ = new Toolbar("");
      title2_ = new Label();
      title2_.addStyleName(RES.styles().topicTitle());
      
      // Style the toolbar to blend in with the search container and raise bottom edge even more
      searchToolbar_.getElement().getStyle().setMarginTop(0, Unit.PX); // Move toolbar even higher up
      searchToolbar_.getElement().getStyle().setMarginBottom(0, Unit.PX);
      searchToolbar_.getElement().getStyle().setProperty("borderTop", "none");
      searchToolbar_.getElement().getStyle().setPaddingTop(2, Unit.PX); // Even less padding on top
      searchToolbar_.getElement().getStyle().setPaddingBottom(0, Unit.PX); // Remove bottom padding entirely
      searchToolbar_.getElement().getStyle().setBackgroundColor("#ffffff");
      searchToolbar_.getElement().getStyle().setWidth(100, Unit.PCT);
      searchToolbar_.getElement().getStyle().setBorderWidth(0, Unit.PX);
      searchToolbar_.getElement().getStyle().setProperty("zIndex", "102"); // Bring toolbar to front
      searchToolbar_.getElement().getStyle().setProperty("display", "flex");
      searchToolbar_.getElement().getStyle().setProperty("alignItems", "center");
      
      // Also reduce the wrapper's bottom margin/padding - move up 2px more
      searchAndToolbarWrapper.getElement().getStyle().setProperty("marginBottom", "0px");
      
      // Add the aiAttach button with better visibility but smaller size
      ToolbarButton attachButton = commands_.aiAttach().createToolbarButton();
      attachButton.getElement().getStyle().setMarginLeft(5, Unit.PX);
      attachButton.getElement().getStyle().setMarginRight(5, Unit.PX);
      attachButton.getElement().getStyle().setPaddingRight(5, Unit.PX);
      attachButton.getElement().getStyle().setPaddingLeft(5, Unit.PX);
      attachButton.getElement().getStyle().setPaddingTop(2, Unit.PX);
      attachButton.getElement().getStyle().setPaddingBottom(2, Unit.PX);
      searchToolbar_.getElement().getStyle().setProperty("zIndex", "1000");
      attachButton.getElement().getStyle().setProperty("transform", "scale(1.1)");
      attachButton.getElement().getStyle().setBackgroundColor("transparent");
      attachButton.getElement().getStyle().setMarginTop(0, Unit.PX);
      // searchToolbar_.addLeftWidget(attachButton); // DISABLED: Comment out to hide attachments icon
      
      // Container for attachment menu that will appear to the right of the button
      attachmentMenuContainer_ = new SimplePanel();
      // searchToolbar_.addLeftWidget(attachmentMenuContainer_); // DISABLED: Comment out to hide attachment menu container
      
      // Add the image attachment button in place of the old attachment button
      ToolbarButton imageButton = commands_.aiAttachImage().createToolbarButton();
      imageButton.getElement().getStyle().setMarginLeft(0, Unit.PX);
      imageButton.getElement().getStyle().setMarginRight(0, Unit.PX);
      imageButton.getElement().getStyle().setPaddingRight(0, Unit.PX);
      imageButton.getElement().getStyle().setPaddingLeft(0, Unit.PX);
      imageButton.getElement().getStyle().setPaddingTop(0, Unit.PX);
      imageButton.getElement().getStyle().setPaddingBottom(0, Unit.PX);
      imageButton.getElement().getStyle().setProperty("transform", "scale(1.1)");
      imageButton.getElement().getStyle().setBackgroundColor("transparent");
      imageButton.getElement().getStyle().setProperty("overflow", "visible"); // Allow content to show outside button bounds
      imageButton.getElement().getStyle().setMarginTop(-7, Unit.PX); // Move up slightly to match send button height
      searchToolbar_.addLeftWidget(imageButton);
      
      // Container for image menu that will appear to the right of the button
      imageMenuContainer_ = new SimplePanel();
      imageMenuContainer_.getElement().getStyle().setProperty("verticalAlign", "bottom");
      imageMenuContainer_.getElement().getStyle().setProperty("display", "inline-flex");
      imageMenuContainer_.getElement().getStyle().setProperty("alignItems", "center");
      searchToolbar_.addLeftWidget(imageMenuContainer_);
      
      // Create a circular button with a right-pointing triangle
      FlowPanel sendButton = new FlowPanel();
      sendButton.addStyleName("ai-send-button");
      Element sendButtonElement = sendButton.getElement();
      sendButtonElement.getStyle().setProperty("display", "inline-flex");
      sendButtonElement.getStyle().setProperty("alignItems", "center");
      sendButtonElement.getStyle().setProperty("justifyContent", "center");
      sendButtonElement.getStyle().setProperty("width", "19px");
      sendButtonElement.getStyle().setProperty("height", "19px");
      sendButtonElement.getStyle().setProperty("borderRadius", "50%");
      sendButtonElement.getStyle().setProperty("backgroundColor", "#000000");
      sendButtonElement.getStyle().setProperty("border", "1px solid #000000");
      sendButtonElement.getStyle().setProperty("cursor", "pointer");
      sendButtonElement.getStyle().setProperty("marginRight", "9px");
      
      // Store the button element for later transformation
      sendButtonElement_ = sendButtonElement;
      
      // Initialize in send mode (triangle)
      isInCancelMode_ = false;
      
      // Create a div for the triangle
      Element triangle = Document.get().createDivElement();
      
      // Style it as a right-pointing triangle with slightly rounded appearance
      triangle.getStyle().setProperty("width", "0");
      triangle.getStyle().setProperty("height", "0");
      triangle.getStyle().setProperty("borderTop", "6px solid transparent");
      triangle.getStyle().setProperty("borderBottom", "6px solid transparent");
      triangle.getStyle().setProperty("borderLeft", "9px solid white");
      triangle.getStyle().setProperty("marginLeft", "3px");
      
      // Add the triangle to the button
      sendButtonElement.appendChild(triangle);
      
      // Add direct GWT click handler to handle cancel functionality when in cancel mode
      sendButton.addDomHandler(new ClickHandler() {
         @Override
         public void onClick(ClickEvent event) {                        
            // Check if we're in cancel mode using the flag
            if (isInCancelMode_) {               
               // IMMEDIATELY remove the thinking message from DOM before any other processing
               // This gives immediate visual feedback to the user
               final AiPane aiPane = AiPane.getCurrentInstance();
               if (aiPane != null) {
                  WindowEx contentWindow = aiPane.getContentWindow();
                  if (contentWindow != null) {
                     removeThinkingMessageSync(contentWindow);
                  }
                  
                  // Also immediately hide the cancel button
                  aiPane.hideCancelButton();
               }
               
               // Get the AiSearch instance to cancel the request
               if (searchProvider_ != null) {
                  AiSearch aiSearch = searchProvider_.get();
                  if (aiSearch != null) {
                     aiSearch.cancelAiRequest();
                  }
               }
               
               // Prevent default handling
               event.preventDefault();
               event.stopPropagation();
            } else {
               // Find the input element and submit the query through AiSearch directly
               Element inputElement = findInputElement(searchWidgetWidget.getElement());
               if (inputElement != null) {
                  String query = getElementValue(inputElement);
                  if (query != null && !query.trim().isEmpty()) {
                     // Clear the search box BEFORE submitting the query
                     if (searchWidget_ != null) {
                        searchWidget_.setText("");
                     }
                     
                     // Also clear the input element directly
                     setElementValue(inputElement, "");
                     
                     // Use AiSearch public submitQuery to invoke search
                     if (searchProvider_ != null) {
                        AiSearch aiSearch = searchProvider_.get();
                        if (aiSearch != null) {
                           aiSearch.submitQuery(query.trim());
                        }
                     }
                  }
               }
               
               // Prevent default to handle it our way
               event.preventDefault();
               event.stopPropagation();
            }
         }
      }, ClickEvent.getType());
      
      // Add hover effects
      sendButton.addDomHandler(new MouseOverHandler() {
         @Override
         public void onMouseOver(MouseOverEvent event) {
            sendButtonElement.getStyle().setBackgroundColor("#333333"); // Darker hover state
         }
      }, MouseOverEvent.getType());
      
      sendButton.addDomHandler(new MouseOutHandler() {
         @Override
         public void onMouseOut(MouseOutEvent event) {
            sendButtonElement.getStyle().setBackgroundColor("#000000"); // Back to black
         }
      }, MouseOutEvent.getType());
      
      // Add the send button to the right side of the toolbar
      searchToolbar_.addRightWidget(sendButton);
      
      // Add the toolbar to the wrapper
      searchAndToolbarWrapper.add(searchToolbar_);
      
      // Add the wrapper to the content panel
      contentPanel.add(searchAndToolbarWrapper);
      
      // Wrap the content panel in the search container with clean styling
      searchContainer = new SimplePanel();
      searchContainer.setWidget(contentPanel);
      searchContainer.setStyleName("rstudio-AiSearchContainer");
      
      // Create a clean white container with NO border
      Element containerElement = searchContainer.getElement();
      containerElement.getStyle().setProperty("minHeight", "100px");
      containerElement.getStyle().setProperty("height", "auto");
      containerElement.getStyle().setProperty("display", "block");
      containerElement.getStyle().setProperty("position", "relative");
      containerElement.getStyle().setProperty("zIndex", "100");
      containerElement.getStyle().setProperty("backgroundColor", "#ffffff");
      containerElement.getStyle().setProperty("border", "none"); // No border on container
      containerElement.getStyle().setProperty("margin", "10px 15px");
      containerElement.getStyle().setProperty("padding", "0");
      containerElement.getStyle().setProperty("transition", "none");
      
      // Create a resize handler to adjust the layout when the content changes size
      contentPanel.addHandler(new ResizeHandler() {
         @Override
         public void onResize(ResizeEvent event) {

            // Update container height to match content
            int contentHeight = contentPanel.getOffsetHeight();
            if (contentHeight > 0) {

               // Sets the height of the search container to the content height, but at least 100px
               int newHeight = Math.max(100, contentHeight);

               // First, apply consistent styling directly to ensure no jumping occurs
               Element containerElement = searchContainer.getElement();
               containerElement.getStyle().setProperty("minHeight", newHeight + "px");
               containerElement.getStyle().setProperty("height", "auto");
               containerElement.getStyle().setProperty("position", "relative");
               containerElement.getStyle().setProperty("zIndex", "100");
               containerElement.getStyle().setProperty("backgroundColor", "#ffffff"); // Keep white
               containerElement.getStyle().setProperty("border", "none"); // No border on container
               containerElement.getStyle().setProperty("margin", "10px 15px"); // Maintain margin

               // Note: Layout management now handled by view manager
               
               // After layout is forced, ensure the search box elements maintain consistent positioning
               if (scrollHandler_ != null) {
                  scrollHandler_.fixSearchBoxPositioning(containerElement);
               }
            }

         }
      }, ResizeEvent.getType());
      
      // Style the search widget for bottom placement
      searchWidgetWidget.addStyleName(RES.styles().bottomSearchWidget());
      searchWidgetWidget.getElement().getStyle().setMarginTop(0, Unit.PX);
      searchWidgetWidget.getElement().getStyle().setMarginBottom(0, Unit.PX);
      
      // Style the streaming panel but DON'T add it yet - view manager will control this
      
      streamingPanel_.setSize("100%", "100%");
      streamingPanel_.getElement().getStyle().setProperty("border", "1px solid #ddd");
      streamingPanel_.getElement().getStyle().setProperty("borderRadius", "4px");
      streamingPanel_.getElement().getStyle().setProperty("backgroundColor", "#ffffff");
      streamingPanel_.getElement().getStyle().setProperty("overflow", "auto");

      
      // Schedule a deferred task to ensure proper rendering of the search widget
      Scheduler.get().scheduleDeferred(new ScheduledCommand() {
         @Override
         public void execute() {
            // Check if there are attachments to display
            pane_.refreshAttachmentsList();
            // Check if there are images to display
            pane_.refreshImagesList();
         }
      });
      
      // Ensure the button starts in send mode
      Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
         @Override
         public void execute() {
            // Double-check button state
            isInCancelMode_ = false;
         }
      });

      // Initialize view manager and return its widget instead of mainPanel
      viewManager_ = new AiViewManager(
         streamingPanel_,
         iframeContainer_,
         searchContainer,
         frame_,
         pane_.getAiServerOperations(),
         pane_
      );
      
      // Start in API management mode as requested
      viewManager_.showApiManagement();
      
      return viewManager_.getMainWidget();
   }

   public Toolbar createMainToolbar()
   {
      Toolbar toolbar = new Toolbar(constants_.aiTabLabel());

      toolbar.addLeftWidget(commands_.aiHome().createToolbarButton());
      toolbar.addLeftSeparator();

      // Addes back, and forward buttons to the toolbar
      ToolbarButton refreshButton = commands_.refreshAi().createToolbarButton();
      refreshButton.addStyleName(ThemeStyles.INSTANCE.refreshToolbarButton());
      toolbar.addLeftWidget(refreshButton);
      // Create back button with direct click handler instead of command system
      ToolbarButton backButton = new ToolbarButton(
         ToolbarButton.NoText,
         "Previous conversation",
         commands_.aiBack().getImageResource(),
         new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
               // Get current conversation index from server
               pane_.getAiServerOperations().getCurrentConversationIndex(new ServerRequestCallback<Double>() {
                  @Override
                  public void onResponseReceived(Double currentIndex) {
                     if (currentIndex == null) return;
                     
                     // Get list of available conversations
                     pane_.getAiServerOperations().listConversations(new ServerRequestCallback<JsArrayString>() {
                        @Override
                        public void onResponseReceived(JsArrayString conversations) {
                           // Convert to array of integers and find current position
                           int[] conversationIds = new int[conversations.length()];
                           int currentPosition = -1;
                           
                           for (int i = 0; i < conversations.length(); i++) {
                              conversationIds[i] = Integer.parseInt(conversations.get(i));
                              if (conversationIds[i] == currentIndex.intValue()) {
                                 currentPosition = i;
                              }
                           }
                           
                           if (currentPosition <= 0) {
                              return; // Already at first conversation or current not found
                           }
                           
                           // Navigate to previous conversation in the sorted list
                           int targetIndex = conversationIds[currentPosition - 1];
                           AiPaneConversations conversationsManager = pane_.getConversationsManager();
                           if (conversationsManager != null) {
                              conversationsManager.switchToConversation(targetIndex, false);
                           }
                        }
                        
                        @Override
                        public void onError(ServerError error) {
                           // Handle error silently
                        }
                     });
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     // Handle error silently
                  }
               });
            }
         }
      );
      toolbar.addLeftWidget(backButton);
      
      // Create forward button with direct click handler instead of command system  
      ToolbarButton forwardButton = new ToolbarButton(
         ToolbarButton.NoText,
         "Next conversation", 
         commands_.aiForward().getImageResource(),
         new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
               // Get current conversation index from server
               pane_.getAiServerOperations().getCurrentConversationIndex(new ServerRequestCallback<Double>() {
                  @Override
                  public void onResponseReceived(Double currentIndex) {
                     if (currentIndex == null) return;
                     
                     // Get list of available conversations
                     pane_.getAiServerOperations().listConversations(new ServerRequestCallback<JsArrayString>() {
                        @Override
                        public void onResponseReceived(JsArrayString conversations) {
                           // Convert to array of integers and find current position
                           int[] conversationIds = new int[conversations.length()];
                           int currentPosition = -1;
                           
                           for (int i = 0; i < conversations.length(); i++) {
                              conversationIds[i] = Integer.parseInt(conversations.get(i));
                              if (conversationIds[i] == currentIndex.intValue()) {
                                 currentPosition = i;
                              }
                           }
                           
                           if (currentPosition >= conversations.length() - 1 || currentPosition == -1) {
                              return; // Already at last conversation or current not found
                           }
                           
                           // Navigate to next conversation in the sorted list
                           int targetIndex = conversationIds[currentPosition + 1];
                           AiPaneConversations conversationsManager = pane_.getConversationsManager();
                           if (conversationsManager != null) {
                              conversationsManager.switchToConversation(targetIndex, false);
                           }
                        }
                        
                        @Override
                        public void onError(ServerError error) {
                           // Handle error silently
                        }
                     });
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     // Handle error silently
                  }
               });
            }
         }
      );
      toolbar.addLeftWidget(forwardButton);
      toolbar.addLeftSeparator();
      
      // Create a stable title element
      overlayTitle_ = new Label("New conversation");
      overlayTitle_.addStyleName(RES.styles().topicTitle());
      
      // Apply additional styling to ensure the title is always visible with arrow right after text
      Element titleElement = overlayTitle_.getElement();
      titleElement.getStyle().setDisplay(Style.Display.INLINE_BLOCK);
      titleElement.getStyle().setProperty("minWidth", "150px");
      titleElement.getStyle().setProperty("maxWidth", "300px");
      titleElement.getStyle().setProperty("whiteSpace", "nowrap");
      titleElement.getStyle().setProperty("textOverflow", "ellipsis");
      titleElement.getStyle().setProperty("overflow", "hidden");
      titleElement.getStyle().setBackgroundColor("transparent");
      titleElement.getStyle().setPaddingRight(0, Unit.PX);
      titleElement.getStyle().setMarginRight(0, Unit.PX);
      titleElement.getStyle().setCursor(Style.Cursor.POINTER);
      titleElement.getStyle().setFontSize(13, Unit.PX);
      
      // Add a custom dropdown arrow using an inline-block element
      Element arrowSpan = Document.get().createSpanElement();
      arrowSpan.setClassName("ai-dropdown-arrow");
      arrowSpan.getStyle().setDisplay(Style.Display.INLINE_BLOCK);
      arrowSpan.getStyle().setMarginLeft(3, Unit.PX);
      arrowSpan.getStyle().setPosition(Style.Position.RELATIVE);
      arrowSpan.getStyle().setTop(0, Unit.PX);
      arrowSpan.setInnerHTML("&#9662;"); // Unicode down triangle
      titleElement.appendChild(arrowSpan);
      
      // Add the title to the toolbar and make it trigger the menu
      toolbar.addLeftWidget(overlayTitle_);
      
      // Create a custom menu handler
      overlayTitle_.addClickHandler(new ClickHandler() {
         @Override
         public void onClick(ClickEvent event) {
            // Position the menu below the title element
            history_.getMenu().setPopupPositionAndShow(new PopupPanel.PositionCallback() {
               @Override
               public void setPosition(int offsetWidth, int offsetHeight) {
                  Element titleEl = overlayTitle_.getElement();
                  int left = titleEl.getAbsoluteLeft();
                  int top = titleEl.getAbsoluteTop() + titleEl.getOffsetHeight();
                  history_.getMenu().setPopupPosition(left, top);
               }
            });
         }
      });
      
      // Keep a reference to the original title (necessary for some parts of the code)
      // but don't add it to the DOM
      title_ = new Label();
      title_.addStyleName(RES.styles().topicTitle());
      title_.getElement().getStyle().setDisplay(Display.NONE);

      ThemeStyles styles = ThemeStyles.INSTANCE;
      toolbar.getWrapper().addStyleName(styles.tallerToolbarWrapper());

      final SmallButton btnNext = new SmallButton("&gt;", true);
      btnNext.getElement().setAttribute("aria-label", constants_.findNextLabel());
      btnNext.setTitle(constants_.findNextLabel());
      btnNext.addStyleName(RES.styles().topicNavigationButton());
      btnNext.setVisible(false);
      btnNext.addClickHandler(new ClickHandler() {
         @Override
         public void onClick(ClickEvent event)
         {
            pane_.findNext();
         }
      });

      toolbar.addLeftSeparator();

      final SmallButton btnPrev = new SmallButton("&lt;", true);
      btnPrev.getElement().setAttribute("aria-label", constants_.findPreviousLabel());
      btnPrev.setTitle(constants_.findPreviousLabel());
      btnPrev.addStyleName(RES.styles().topicNavigationButton());
      btnPrev.setVisible(false);
      btnPrev.addClickHandler(new ClickHandler() {
         @Override
         public void onClick(ClickEvent event)
         {
            pane_.findPrev();
         }
      });

      // toolbar.addRightSeparator();

      // // Creates the "find in chat" textbox on the right side of the toolbar
      // findTextBox_ = new FindTextBox(constants_.findInTopicLabel());
      // findTextBox_.addStyleName(RES.styles().findTopicTextbox());
      // findTextBox_.setOverrideWidth(90);
      // ElementIds.assignElementId(findTextBox_, ElementIds.SW_AI_FIND_IN_TOPIC);
      // toolbar.addRightWidget(findTextBox_);
      
      // // Move the find next/prev buttons to the right of search box
      // if (pane_.isIncrementalFindSupported())
      // {
      //    btnPrev.getElement().getStyle().setMarginRight(3, Unit.PX);
      //    toolbar.addRightWidget(btnPrev);
      //    toolbar.addRightWidget(btnNext);
      // }
      
      // findTextBox_.addKeyUpHandler(new KeyUpHandler() {

      //    @Override
      //    public void onKeyUp(KeyUpEvent event)
      //    {
      //       // ignore modifier key release
      //       if (event.getNativeKeyCode() == KeyCodes.KEY_CTRL ||
      //           event.getNativeKeyCode() == KeyCodes.KEY_ALT ||
      //           event.getNativeKeyCode() == KeyCodes.KEY_SHIFT)
      //       {
      //          return;
      //       }

      //       WindowEx contentWindow = pane_.getContentWindow();
      //       if (contentWindow != null)
      //       {
      //          // escape means exit find mode and put focus
      //          // into the main content window
      //          if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE)
      //          {
      //             event.preventDefault();
      //             event.stopPropagation();
      //             clearTerm();
      //             contentWindow.focus();
      //          }
      //          else
      //          {
      //             // prevent two enter keys in rapid succession from
      //             // minimizing or maximizing the ai pane
      //             if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER)
      //             {
      //                event.preventDefault();
      //                event.stopPropagation();
      //             }

      //             // check for term
      //             String term = findTextBox_.getValue().trim();

      //             int modifier = KeyboardShortcut.getModifierValue(event.getNativeEvent());
      //             boolean isShift = modifier == KeyboardShortcut.SHIFT;

      //             // if there is a term then search for it
      //             if (term.length() > 0)
      //             {
      //                // make buttons visible
      //                setButtonVisibility(true);

      //                // perform the find (check for incremental)
      //                if (pane_.isIncrementalFindSupported())
      //                {
      //                   boolean incremental =
      //                    !event.isAnyModifierKeyDown() &&
      //                    (event.getNativeKeyCode() != KeyCodes.KEY_ENTER);

      //                   pane_.performFind(term, !isShift, incremental);
      //                }
      //                else
      //                {
      //                   if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER)
      //                      pane_.performFind(term, !isShift, false);
      //                }
      //             }

      //             // no term means clear term and remove selection
      //             else
      //             {
      //                if (pane_.isIncrementalFindSupported())
      //                {
      //                   clearTerm();
      //                   contentWindow.removeSelection();
      //                }
      //             }
      //          }
      //       }
      //    }

      //    private void clearTerm()
      //    {
      //       findTextBox_.setValue("");
      //       setButtonVisibility(false);
      //    }

      //    private void setButtonVisibility(final boolean visible)
      //    {
      //       Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      //          @Override
      //          public void execute()
      //          {
      //             btnNext.setVisible(visible);
      //             btnPrev.setVisible(visible);
      //          }
      //       });
      //    }
      // });

      // findTextBox_.addKeyDownHandler(new KeyDownHandler() {

      //    @Override
      //    public void onKeyDown(KeyDownEvent event)
      //    {
      //       // we handle these directly so prevent the browser
      //       // from handling them
      //       if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE ||
      //           event.getNativeKeyCode() == KeyCodes.KEY_ENTER)
      //       {
      //          event.preventDefault();
      //          event.stopPropagation();
      //       }
      //    }
      // });

      return toolbar;
   }

   // Getters for the components
   public DockLayoutPanel getMainPanel()
   {
      return viewManager_ != null ? viewManager_.getDockLayoutPanel() : null;
   }
   
   public SimplePanel getSearchContainer()
   {
      return searchContainer;
   }
   
   public FindTextBox getFindTextBox()
   {
      return findTextBox_;
   }
   
   public Toolbar getSearchToolbar()
   {
      return searchToolbar_;
   }
   
   public SimplePanel getAttachmentMenuContainer()
   {
      return attachmentMenuContainer_;
   }
   
   public SimplePanel getImageMenuContainer()
   {
      return imageMenuContainer_;
   }
   
   public SearchDisplay getSearchWidget()
   {
      return searchWidget_;
   }
   
   public Label getTitle()
   {
      return title_;
   }
   
   public Label getOverlayTitle()
   {
      return overlayTitle_;
   }
   
   public Label getTitle2()
   {
      return title2_;
   }
   
   public FlowPanel getSelectedFilesPanel()
   {
      return selectedFilesPanel_;
   }
   
   public AiStreamingPanel getStreamingPanel()
   {
      return streamingPanel_;
   }
   
   public AiViewManager getViewManager()
   {
      return viewManager_;
   }
   
   /**
    * Updates the attach button text based on whether there are context items
    */
   public void updateAttachButtonText() {
      if (attachFileButton_ != null && selectedFilesPanel_ != null) {
         boolean hasContextItems = selectedFilesPanel_.getWidgetCount() > 0;
         String buttonHtml;
         if (hasContextItems) {
            // Just the @ symbol, moved up 1px
            buttonHtml = "<span style='position: relative; top: -1px;'>@</span>";
         } else {
            // @ symbol moved up 1px, followed by normal text with non-breaking space
            buttonHtml = "<span style='position: relative; top: -1px;'>@</span>&nbsp;Add context";
         }
         attachFileButton_.getElement().setInnerHTML(buttonHtml);
      }
   }
   
   // References to resources and styles
   public interface Styles extends CssResource
   {
      String findTopicTextbox();
      String topicNavigationButton();
      String topicTitle();
      String bottomSearchWidget();
   }
   
   public static interface Resources extends ClientBundle
   {
      @Source("AiPane.css")
      Styles styles();
   }
   
   private static final Resources RES = GWT.create(Resources.class);
   static { RES.styles().ensureInjected(); }
   
   // Members
   private final AiPane pane_;
   private final Provider<AiSearch> searchProvider_;
   private final Commands commands_;
   private final AiToolbarLinkMenu history_;
   private final RStudioThemedFrame frame_;
   private final RStudioThemedFrame backgroundFrame_;
   private final AiPaneScroll scrollHandler_;
   private final AiContext aiContext_;
   private final EventBus eventBus_;
   private final AiConstants constants_;
   
   private SimplePanel searchContainer;
   private FindTextBox findTextBox_;
   private Toolbar searchToolbar_;
   private SimplePanel attachmentMenuContainer_;
   private SimplePanel imageMenuContainer_;
   private SearchDisplay searchWidget_;
   private Label title_;
   private Label overlayTitle_;
   private Label title2_;
   private Element sendButtonElement_; // Reference to the send/cancel button
   private Label attachFileButton_; // Reference to the @ button
   private FlowPanel selectedFilesPanel_; // Reference to the selected files panel
   private AiStreamingPanel streamingPanel_; // Streaming conversation panel
   private AiViewManager viewManager_; // Manages API vs conversation views
   private SimplePanel iframeContainer_; // Container for iframe
   
   // Add a flag to track button mode - true for cancel mode, false for send mode
   private boolean isInCancelMode_ = false;
   
   /**
    * Recursively searches for an input or textarea element within the given element's children
    */
   private Element findInputElement(Element parent) {
      if (parent == null) {
         return null;
      }
      
      // Check if the current element is an input or textarea
      String tagName = parent.getTagName().toLowerCase();
      if ("input".equals(tagName) || "textarea".equals(tagName)) {
         return parent;
      }
      
      // Check each child element
      NodeList<Element> children = parent.getElementsByTagName("*");
      for (int i = 0; i < children.getLength(); i++) {
         Element child = children.getItem(i);
         tagName = child.getTagName().toLowerCase();
         
         if ("input".equals(tagName) || "textarea".equals(tagName)) {
            // Make sure this input isn't part of another widget (like the search in the toolbar)
            if (child.getAttribute("placeholder") != null && 
                child.getAttribute("placeholder").contains("Ask anything")) {
               return child;
            }
         }
      }
      
      return null;
   }
   
   /**
    * Gets the value from an input or textarea element
    */
   private String getElementValue(Element element) {
      if (element == null) {
         return null;
      }
      
      String tagName = element.getTagName().toLowerCase();
      if ("input".equals(tagName)) {
         return element.getPropertyString("value");
      } else if ("textarea".equals(tagName)) {
         return element.getPropertyString("value");
      }
      
      return null;
   }
   
   /**
    * Sets the value for an input or textarea element
    */
   private void setElementValue(Element element, String value) {
      if (element == null) {
         return;
      }
      
      String tagName = element.getTagName().toLowerCase();
      if ("input".equals(tagName) || "textarea".equals(tagName)) {
         element.setPropertyString("value", value);
         
         // Trigger input event to ensure UI is updated properly
         triggerInputEvent(element);
      }
   }
   
   private native void triggerInputEvent(Element element) /*-{
      if (element) {
         var event = new Event('input', {
            bubbles: true,
            cancelable: true
         });
         element.dispatchEvent(event);
      }
   }-*/;

   /**
    * Transforms the send button (triangle) into a cancel button (square)
    */
   public void setButtonToCancelMode() {
      if (sendButtonElement_ != null) {
         // Remove all child elements (the triangle)
         while (sendButtonElement_.getFirstChild() != null) {
            sendButtonElement_.removeChild(sendButtonElement_.getFirstChild());
         }
         
         // Create a square element with rounded corners
         Element square = Document.get().createDivElement();
         square.getStyle().setProperty("width", "9px");
         square.getStyle().setProperty("height", "9px");
         square.getStyle().setProperty("backgroundColor", "white");
         square.getStyle().setProperty("borderRadius", "2px");
         square.getStyle().setProperty("display", "block");
         square.getStyle().setProperty("flexShrink", "0");
         square.getStyle().setProperty("position", "relative");
         
         // Add the square to the button
         sendButtonElement_.appendChild(square);
         
         // Update mode flag
         isInCancelMode_ = true;
      }
   }
   
   /**
    * Transforms the cancel button (square) back to a send button (triangle)
    */
   public void setButtonToSendMode() {
      if (sendButtonElement_ != null) {
         // Remove all child elements (the square)
         while (sendButtonElement_.getFirstChild() != null) {
            sendButtonElement_.removeChild(sendButtonElement_.getFirstChild());
         }
         
         // Create a div for the triangle
         Element triangle = Document.get().createDivElement();
         
         // Style it as a right-pointing triangle with slightly rounded appearance
         triangle.getStyle().setProperty("width", "0");
         triangle.getStyle().setProperty("height", "0");
         triangle.getStyle().setProperty("borderTop", "6px solid transparent");
         triangle.getStyle().setProperty("borderBottom", "6px solid transparent");
         triangle.getStyle().setProperty("borderLeft", "9px solid white");
         triangle.getStyle().setProperty("marginLeft", "3px");
         
         // Add the triangle to the button
         sendButtonElement_.appendChild(triangle);
         
         // Update mode flag
         isInCancelMode_ = false;
      }
   }
   
   /**
    * Force the button into send mode regardless of current state
    * This ensures we don't get stuck in cancel mode
    */
   public void forceButtonToSendMode() {
      setButtonToSendMode();
      
      // Ensure flag is reset
      isInCancelMode_ = false;
   }

   // Synchronously remove thinking message without any animation
   private native void removeThinkingMessageSync(WindowEx window) /*-{
      if (!window || !window.document) return;
      
      try {
         // Immediate remove by ID - fastest possible method
         var thinkingMessage = window.document.getElementById('ai-thinking-message');
         if (thinkingMessage && thinkingMessage.parentNode) {
            thinkingMessage.parentNode.removeChild(thinkingMessage);
         }
         
         // Also remove any elements with the thinking attribute
         var thinkingElements = window.document.querySelectorAll('[data-thinking="true"]');
         if (thinkingElements && thinkingElements.length > 0) {
            for (var i = 0; i < thinkingElements.length; i++) {
               var element = thinkingElements[i];
               if (element && element.parentNode) {
                  element.parentNode.removeChild(element);
               }
            }
         }
      } catch(e) {
         // Ignore any errors - we're just trying to be as fast as possible
         console.error("Error removing thinking message:", e);
      }
   }-*/;

   /**
    * Adds drag and drop support to the context bar for accepting files from the Files pane
    * 
    * This method enables users to drag files from the RStudio Files pane directly into the
    * AI context bar, automatically adding them as context for AI conversations. The implementation
    * handles both:
    * 
    * 1. Internal file drags from the RStudio Files pane (using custom data transfer)
    * 2. External file drops from the operating system file manager (using standard Files API)
    * 
    * Visual feedback is provided during drag operations:
    * - Blue highlight when dragging over the context bar
    * - Green flash when files are successfully dropped
    * - Border changes to indicate drop zones
    * 
    * @param contextPanel The FlowPanel that serves as the drop zone (context bar)
    */
   private void addDragDropSupport(FlowPanel contextPanel) {
      // Create a DragDropReceiver for the context panel
      new DragDropReceiver(contextPanel) {
         @Override
         public void onDrop(NativeEvent event) {
            // Handle dropped files by adding them to context
            handleDroppedFiles(event);
         }
         
         @Override
         public void onDragOver(NativeEvent event) {
            // Add visual feedback during drag over
            contextPanel.getElement().getStyle().setProperty("backgroundColor", "#e8f4fd");
            contextPanel.getElement().getStyle().setProperty("borderColor", "#0078d4");
            contextPanel.getElement().getStyle().setProperty("borderWidth", "2px");
         }
         
         @Override
         public void onDragLeave(NativeEvent event) {
            // Remove visual feedback when drag leaves
            contextPanel.getElement().getStyle().setProperty("backgroundColor", "#f0f0f0");
            contextPanel.getElement().getStyle().setProperty("borderColor", "#aaaaaa");
            contextPanel.getElement().getStyle().setProperty("borderWidth", "1px");
         }
         
         /**
          * Handle dropped files by adding them to the AI context
          */
         public boolean handleDroppedFiles(NativeEvent event) {
            // Reset visual feedback
            contextPanel.getElement().getStyle().setProperty("backgroundColor", "#f0f0f0");
            contextPanel.getElement().getStyle().setProperty("borderColor", "#aaaaaa");
            contextPanel.getElement().getStyle().setProperty("borderWidth", "1px");
            
            // Extract dropped files - handle both internal RStudio files and external files
            DataTransfer data = Js.cast(event.getDataTransfer());
            JsArray<String> types = data.types;
            
            event.stopPropagation();
            event.preventDefault();
            
            int filesAdded = 0;
            
            // Check if this is an internal RStudio file drag
            String rstudioFilePath = data.getData("application/x-rstudio-file");
            
            // Case 1: RStudio file path
            if (rstudioFilePath != null && !rstudioFilePath.isEmpty()) {
               // Handle internal RStudio file drag
               if (aiContext_ != null && selectedFilesPanel_ != null) {
                  aiContext_.handleDroppedFile(rstudioFilePath, selectedFilesPanel_);
                  filesAdded = 1;
               }
               
               // Provide brief visual feedback that the drop was successful
               if (filesAdded > 0) {
                  showDropSuccess(contextPanel);
               }
               return true;
            }
            
            // Case 2: RStudio tab
            String rstudioTab = data.getData("application/rstudio-tab");
            
            if (rstudioTab != null && !rstudioTab.isEmpty()) {
               // Extract the document ID
               String docId = rstudioTab.split("\\|")[0];
               
               // Use a direct call to the RPC endpoint instead of trying to access server operations
               pane_.getAiServerOperations().getTabFilePath(docId, 
                  new ServerRequestCallback<String>() {
                     @Override
                     public void onResponseReceived(String filePath) {
                        // Only process if we have a valid file path
                        if (filePath != null && !filePath.isEmpty()) {
                           if (aiContext_ != null && selectedFilesPanel_ != null) {
                              aiContext_.handleDroppedFile(filePath, selectedFilesPanel_);
                              showDropSuccess(contextPanel);
                           }
                        }
                     }
                     
                     @Override
                     public void onError(ServerError error) {
                        // Silently ignore errors without showing messages to user
                     }
                  });
               
               return true;
            }
            
            // No supported file types found
            return false;
         }
         
         /**
          * Shows a brief success indicator when files are dropped
          */
         private void showDropSuccess(FlowPanel contextPanel) {
            // Reset to normal immediately instead of showing green feedback
            contextPanel.getElement().getStyle().setProperty("backgroundColor", "#f0f0f0");
            contextPanel.getElement().getStyle().setProperty("borderColor", "#aaaaaa");
            contextPanel.getElement().getStyle().setProperty("borderWidth", "1px");
         }
      };
   }

   // Add paste detection to the given element
   /**
    * Extract conversation ID from a conversation identifier
    */
   private int extractConversationIdFromIdentifier(String identifier)
   {
      try {
         // Identifier is now just the conversation ID as a string
         return Integer.parseInt(identifier);
      } catch (Exception e) {
         return -1;
      }
   }

   private native void addPerCharacterTracking(Element element) /*-{
      if (element) {
         var self = this;
         
         // Paste event - fires when user pastes text or images
         element.addEventListener("paste", function(event) {
            // Check if clipboardData is available
            if (event.clipboardData) {
               // Handle clipboard images
               var items = event.clipboardData.items;
               if (items && items.length > 0) {
                  var hasImage = false;
                  for (var i = 0; i < items.length; i++) {
                     var item = items[i];
                     
                     if (item.kind === 'file' && item.type.startsWith('image/')) {
                        hasImage = true;
                        
                        var file = item.getAsFile();
                        if (file) {
                           // Call the image handler method
                           self.@org.rstudio.studio.client.workbench.views.ai.AiToolbars::handlePastedImage(*)(file);
                        }
                     }
                  }
                  
                  if (hasImage) {
                     // Prevent default paste behavior for images
                     event.preventDefault();
                     return;
                  }
               }
               
               // Handle text paste (existing functionality)
               var pastedText = event.clipboardData.getData('text/plain');
               
               if (pastedText && pastedText.trim().length > 0) {
                  this.@org.rstudio.studio.client.workbench.views.ai.AiToolbars::searchPastedTextInOpenFiles(Ljava/lang/String;)(pastedText);
               }
            }
         }.bind(this));
         
         // Drag and drop events for images
         element.addEventListener("dragover", function(event) {
            // Check if any of the dragged items might be files
            var mightHaveFiles = false;
            if (event.dataTransfer.items) {
               for (var i = 0; i < event.dataTransfer.items.length; i++) {
                  var item = event.dataTransfer.items[i];
                  
                  // During dragover, files might be represented as:
                  // - kind: 'file' (direct file drag)
                  // - kind: 'string' with type: 'text/uri-list' (file path)
                  if (item.kind === 'file' || 
                      (item.kind === 'string' && item.type === 'text/uri-list')) {
                     mightHaveFiles = true;
                     break;
                  }
               }
            }
            
            // Also check if dataTransfer.files length > 0 (sometimes available during dragover)
            if (!mightHaveFiles && event.dataTransfer.files && event.dataTransfer.files.length > 0) {
               mightHaveFiles = true;
            }
            
            if (mightHaveFiles) {
               event.preventDefault();
               event.stopPropagation();
               
               // Add visual feedback
               element.style.backgroundColor = '#e8f4fd';
               element.style.borderColor = '#0078d4';
            }
         });
         
         element.addEventListener("dragleave", function(event) {
            // Remove visual feedback
            element.style.backgroundColor = '';
            element.style.borderColor = '';
         });
         
         element.addEventListener("drop", function(event) {
            // Remove visual feedback
            element.style.backgroundColor = '';
            element.style.borderColor = '';
            
            var foundImage = false;
            
            // First check for dropped files (direct file objects)
            if (event.dataTransfer.files && event.dataTransfer.files.length > 0) {
               for (var i = 0; i < event.dataTransfer.files.length; i++) {
                  var file = event.dataTransfer.files[i];
                  
                  if (file.type.startsWith('image/')) {
                     foundImage = true;
                     
                     // Call the image handler method
                     self.@org.rstudio.studio.client.workbench.views.ai.AiToolbars::handleDroppedImage(*)(file);
                  }
               }
            } else {
               // If no direct files, try to get file paths from text/uri-list
               var uriList = event.dataTransfer.getData('text/uri-list');
               if (uriList && uriList.trim().length > 0) {
                  // Split by lines and process each URI
                  var uris = uriList.split('\n');
                  for (var i = 0; i < uris.length; i++) {
                     var uri = uris[i].trim();
                     if (uri && !uri.startsWith('#')) { // Skip comments
                        // Check if this URI looks like an image file
                        var imageExtensions = ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'svg', 'webp'];
                        var isImage = false;
                        for (var j = 0; j < imageExtensions.length; j++) {
                           if (uri.toLowerCase().endsWith('.' + imageExtensions[j])) {
                              isImage = true;
                              break;
                           }
                        }
                        
                        if (isImage) {
                           foundImage = true;
                           
                           // Try to load the file from the URI
                           var filePath = uri;
                           if (filePath.startsWith('file://')) {
                              filePath = decodeURIComponent(filePath.substring(7));
                           }
                           
                           // Try to create a File object from the path using fetch
                           var promise = fetch(uri);
                           promise.then(function(response) { 
                              return response.blob(); 
                           }).then(function(blob) {
                              // Create a File object from the blob
                              var fileName = filePath.split('/').pop() || 'dropped_image.png';
                              var file = new File([blob], fileName, { type: blob.type || 'image/png' });
                              
                              // Call the image handler method
                              self.@org.rstudio.studio.client.workbench.views.ai.AiToolbars::handleDroppedImage(*)(file);
                           });
                           
                           // Handle errors separately
                           promise['catch'](function(error) {
                              console.log("Could not load image file from:", uri);
                           });
                        }
                     }
                  }
               }
            }
            
            if (foundImage) {
               event.preventDefault();
               event.stopPropagation();
               return;
            }
         });
      }
   }-*/;
   
   // Search for pasted text in open files and add as context if found
   private void searchPastedTextInOpenFiles(String pastedText) {
      searchProvider_.get().getSearchWidget();

      // Use the AiSearch server operations directly
      AiSearch aiSearch = searchProvider_.get();
      aiSearch.getSearchWidget(); // This ensures aiSearch is properly initialized
      
      // Get server operations from the search provider - this is the same server used by AiSearch
      pane_.getAiServerOperations().matchTextInOpenDocuments(pastedText, 
         new ServerRequestCallback<TextMatchResult>() {
            @Override
            public void onResponseReceived(TextMatchResult result) {
               if (result.hasMatch()) {
                  String filePath = result.getFilePath();
                  int startLine = result.getStartLine();
                  int endLine = result.getEndLine();
                  
                  // Pass the pastedText to the handleAddLinesContext method so it can be removed from the search box
                  aiContext_.handleAddLinesContext(filePath, startLine, endLine, selectedFilesPanel_, pastedText);
               }
            }
            
            @Override
            public void onError(ServerError error) {
            }
         });
   }
   
   /**
    * Handle pasted image from clipboard
    * @param file The image file from clipboard
    */
   private void handlePastedImage(JavaScriptObject file) {
      // Convert JavaScriptObject to File
      if (file == null) {
         return;
      }
      
      // Get file details
      String fileName = getFileProperty(file, "name");
      String fileType = getFileProperty(file, "type");
      
      // Check if it's actually an image
      if (fileType == null || !fileType.startsWith("image/")) {
         return;
      }
      
      // Convert the file to a data URL and save it
      processImageFile(file, "pasted_image_" + System.currentTimeMillis() + getFileExtension(fileType));
   }
   
   /**
    * Handle dropped image from file system
    * @param file The image file dropped from the file system
    */
   private void handleDroppedImage(JavaScriptObject file) {
      // Convert JavaScriptObject to File
      if (file == null) {
         return;
      }
      
      // Get file details
      String fileName = getFileProperty(file, "name");
      String fileType = getFileProperty(file, "type");
      
      // Check if it's actually an image
      if (fileType == null || !fileType.startsWith("image/")) {
         return;
      }
      
      // Use the original filename if available, otherwise generate one
      String outputFileName = fileName;
      if (outputFileName == null || outputFileName.isEmpty()) {
         outputFileName = "dropped_image_" + System.currentTimeMillis() + getFileExtension(fileType);
      }
      
      // Convert the file to a data URL and save it
      processImageFile(file, outputFileName);
   }
   
   /**
    * Process an image file by converting it to a data URL and saving it via server
    * @param file The image file (JavaScriptObject)
    * @param fileName The filename to use for the saved image
    */
   private void processImageFile(JavaScriptObject file, String fileName) {
      // Convert file to data URL using FileReader
      readFileAsDataURL(file, new FileReadCallback() {
         @Override
         public void onSuccess(String dataUrl) {
            // Save the data URL via server
            saveImageDataUrl(dataUrl, fileName);
         }
         
         @Override
         public void onError(String error) {
            RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage("Error", "Failed to read image file: " + error);
         }
      });
   }
   
   /**
    * Save an image data URL with proper limit checking and duplicate detection
    * @param dataUrl The data URL of the image
    * @param fileName The filename to use
    */
   private void saveImageDataUrl(String dataUrl, String fileName) {
      // Step 1: Check current image count for the 3-image limit before doing anything
      AiPaneImages imagesManager = pane_.getImagesManager();
      if (imagesManager != null) {
         imagesManager.getCurrentImageCount(new ServerRequestCallback<Integer>() {
            @Override
            public void onResponseReceived(Integer currentCount) {
               if (currentCount >= 3) {
                  RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage("Image Limit Reached", 
                     "Only 3 images can be attached per message. Please remove an existing image before adding a new one.");
                  return;
               }
               
               // Step 2: Create temporary file to check for duplicates
               createTempImageForDuplicateCheck(dataUrl, fileName);
            }
            
            @Override
            public void onError(ServerError error) {
               String errorMessage = (error != null) ? error.getMessage() : "Unknown error occurred";
               RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage("Error", "Failed to check current image count: " + errorMessage);
            }
         });
      } else {
         // If no images manager, fail immediately
         RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage("Error", "Images manager not available");
         return;
      }
   }
   
   /**
    * Create a temporary image file and check for duplicates before saving permanently
    */
   private void createTempImageForDuplicateCheck(String dataUrl, String fileName) {
      // Create temp file using a modified server call that doesn't add to conversation CSV
      pane_.getAiServerOperations().createTempImageFile(dataUrl, fileName, new ServerRequestCallback<String>() {
         @Override
         public void onResponseReceived(String tempFilePath) {
            // Step 3: Check for content duplicates using the temp file
            pane_.getAiServerOperations().checkImageContentDuplicate(tempFilePath, new ServerRequestCallback<Boolean>() {
               @Override
               public void onResponseReceived(Boolean isDuplicate) {
                  if (isDuplicate) {
                     // Delete temp file and show error
                     deleteTempFile(tempFilePath);
                     RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage("Duplicate Image", 
                        "This image content is already attached to the conversation.");
                     return;
                  }
                  
                  // Step 4: No duplicate found, use attachment system to save properly
                  AiPaneImages imagesManager = pane_.getImagesManager();
                  if (imagesManager == null) {
                     deleteTempFile(tempFilePath);
                     RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage("Error", "Images manager not available");
                     return;
                  }
                  
                  // Copy from temp to final location and add to CSV
                  imagesManager.attachImage(tempFilePath);
               }
               
               @Override
               public void onError(ServerError error) {
                  // If duplicate check fails, assume it's not a duplicate and proceed
                  AiPaneImages imagesManager = pane_.getImagesManager();
                  if (imagesManager == null) {
                     deleteTempFile(tempFilePath);
                     RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage("Error", "Images manager not available");
                     return;
                  }
                  
                  imagesManager.attachImage(tempFilePath);
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            String errorMessage = (error != null) ? error.getMessage() : "Unknown error";
            RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage("Error", "Failed to create temporary image file: " + errorMessage);
         }
      });
   }
   
   /**
    * Clean up temporary files
    */
   private void deleteTempFile(String tempFilePath) {
      // Clean up temp file - don't show errors if this fails
      pane_.getAiServerOperations().deleteImage(tempFilePath, new ServerRequestCallback<Void>() {
         @Override
         public void onResponseReceived(Void v) {
            // Temp file deleted successfully
         }
         
         @Override
         public void onError(ServerError error) {
            // Ignore temp file deletion errors
         }
      });
   }
   
   /**
    * Get file extension for a given MIME type
    * @param mimeType The MIME type
    * @return The file extension including the dot
    */
   private String getFileExtension(String mimeType) {
      if (mimeType == null) return ".png";
      
      switch (mimeType.toLowerCase()) {
         case "image/jpeg":
         case "image/jpg":
            return ".jpg";
         case "image/png":
            return ".png";
         case "image/gif":
            return ".gif";
         case "image/bmp":
            return ".bmp";
         case "image/svg+xml":
            return ".svg";
         case "image/webp":
            return ".webp";
         default:
            return ".png"; // Default fallback
      }
   }
   
   /**
    * Get a string property from a JavaScript File object
    * @param file The file object
    * @param property The property name
    * @return The property value
    */
   private native String getFileProperty(JavaScriptObject file, String property) /*-{
      return file && file[property] ? file[property] : null;
   }-*/;
   
   /**
    * Get a double property from a JavaScript File object
    * @param file The file object
    * @param property The property name
    * @return The property value
    */
   private native double getFilePropertyDouble(JavaScriptObject file, String property) /*-{
      return file && file[property] ? file[property] : 0;
   }-*/;
   
   /**
    * Interface for file read callbacks
    */
   private interface FileReadCallback {
      void onSuccess(String dataUrl);
      void onError(String error);
   }
   
   /**
    * Read a file as data URL using FileReader
    * @param file The file to read
    * @param callback The callback to invoke when done
    */
   private native void readFileAsDataURL(JavaScriptObject file, FileReadCallback callback) /*-{
      if (!file) {
         callback.@org.rstudio.studio.client.workbench.views.ai.AiToolbars.FileReadCallback::onError(Ljava/lang/String;)("File is null");
         return;
      }
      
      var reader = new FileReader();
      
      reader.onload = function(e) {
         callback.@org.rstudio.studio.client.workbench.views.ai.AiToolbars.FileReadCallback::onSuccess(Ljava/lang/String;)(e.target.result);
      };
      
      reader.onerror = function(e) {
         var errorMsg = e.target.error ? e.target.error.message : "Unknown FileReader error";
         callback.@org.rstudio.studio.client.workbench.views.ai.AiToolbars.FileReadCallback::onError(Ljava/lang/String;)(errorMsg);
      };
      
      reader.readAsDataURL(file);
   }-*/;
}