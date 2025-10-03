/*
 * AiWidgetBase.java
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
package org.rstudio.studio.client.workbench.views.ai.widgets;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.dom.client.Style.Unit;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.theme.ThemeHelper;

/**
 * Abstract base class for AI widgets that provides common functionality
 * like button creation, styling, and event handling.
 */
public abstract class AiWidgetBase extends Composite
{
   // Common fields that all AI widgets have
   protected final String messageId_;
   protected final String requestId_;
   protected final String functionCallType_;
   
   // Collapsible state
   protected boolean isExpanded_ = true;
   protected com.google.gwt.dom.client.Element chevronButton_;
   protected Widget contentContainer_;
   protected Panel headerPanel_;
   
   // Constructor for common fields
   protected AiWidgetBase(String messageId, String requestId)
   {
      messageId_ = messageId;
      requestId_ = requestId;
      functionCallType_ = null; // Default for widgets that don't specify
   }
   
   // Constructor with function call type
   protected AiWidgetBase(String messageId, String requestId, String functionCallType)
   {
      messageId_ = messageId;
      requestId_ = requestId;
      functionCallType_ = functionCallType;
   }
   
   /**
    * Creates a vertical stack of buttons with new design: Run/Accept, Cancel, divider, and Add to Allow List
    * @param functionCallType The type of function call (run_console_cmd, run_terminal_cmd, run_file)
    * @param extractedItems The commands/files to potentially add to allow list
    * @return VerticalPanel containing all buttons in vertical stack
    */
   protected VerticalPanel createVerticalButtonStack(String functionCallType, String extractedItems)
   {
      VerticalPanel buttonStack = new VerticalPanel();
      buttonStack.addStyleName("aiVerticalButtonStack");
      // Do NOT force 100% width; let it size to container to avoid right overflow
      // buttonStack.setWidth("100%");
      
      // Background color is handled by CSS theme classes
      // Match console header/wrapper border color
      buttonStack.getElement().getStyle().setProperty("border", "1px solid " + ThemeHelper.getVisibleBorder());
      buttonStack.getElement().getStyle().setProperty("borderTop", "none");
      buttonStack.getElement().getStyle().setProperty("borderRadius", "0 0 4px 4px");
      // Pull up to visually connect with the widget, indent both sides equally
      buttonStack.getElement().getStyle().setProperty("margin", "0px 8px 4px 8px");
      buttonStack.getElement().getStyle().setProperty("boxShadow", "0 1px 3px " + ThemeHelper.getShadowColor());
      buttonStack.getElement().setAttribute("style", 
         buttonStack.getElement().getAttribute("style") + " table-layout: auto !important; width: auto !important;");
      
      // Determine primary action text based on function type
      String primaryActionText = functionCallType.equals("search_replace") ? 
         "Accept" : "Run";
      
      // Create primary action button (Run/Accept) with dark green text (correct color)
      HTML primaryButton = createVerticalStackButton(primaryActionText, "darkGreen", "primary");
      buttonStack.add(primaryButton);
      
      // Create cancel button with dark red text (correct color)
      HTML cancelButton = createVerticalStackButton("Cancel", "darkRed", "cancel");
      buttonStack.add(cancelButton);
      
      // Add horizontal divider
      HTML divider = new HTML("<hr style='margin: 0; border: none; border-top: 1px solid " + ThemeHelper.getVisibleBorder() + ";'>");
      buttonStack.add(divider);
      
      // Create "Add to allow list" button with dark gray text
      String allowListText = determineAllowListText(functionCallType, extractedItems);
      HTML allowListButton = createVerticalStackButton(allowListText, "darkGray", "allowList");
      buttonStack.add(allowListButton);
      
      return buttonStack;
   }

   /**
    * Creates individual button items for the vertical stack as hover-highlight rows (no borders)
    */
   private HTML createVerticalStackButton(String text, String textColor, String buttonAction)
   {
      HTML button = new HTML("<div>" + text + "</div>");
      
      // Style as a hover-highlight row, not a traditional button - minimal height
      button.getElement().getStyle().setProperty("cursor", "pointer");
      button.getElement().getStyle().setProperty("padding", "2px 6px"); // Minimal padding - just text height
      button.getElement().getStyle().setMargin(0, Unit.PX);
      button.getElement().getStyle().setFontSize(12, Unit.PX);
      button.getElement().getStyle().setProperty("lineHeight", "1.1"); // Tight line spacing
      button.getElement().getStyle().setProperty("transition", "background-color 0.2s");
      button.getElement().getStyle().setProperty("userSelect", "none");
      button.getElement().getStyle().setProperty("boxSizing", "border-box");
      button.getElement().getStyle().setProperty("textAlign", "left");
      button.getElement().getStyle().setProperty("fontFamily", "sans-serif");
      
      // Apply semantic CSS classes based on button type
      switch (textColor) {
         case "darkRed":
            button.addStyleName("ai-cancel-button");
            break;
         case "darkGreen":
            button.addStyleName("ai-accept-button");
            break;
         case "darkGray":
            button.addStyleName("ai-edit-button");
            break;
      }
      
      // Add hover class for CSS hover effects
      button.addStyleName("ai-hover-target");
      
      // Add click handler
      addVerticalStackClickHandler(button.getElement(), buttonAction);
      
      return button;
   }

   // Hover effects now handled by CSS via ai-hover-target class

   /**
    * Determines the text for the "Add to allow list" button based on function type and extracted items
    */
   private String determineAllowListText(String functionCallType, String extractedItems)
   {
      if (functionCallType.equals("search_replace")) {
         return "Accept all edits automatically";
      }
      
      if (extractedItems == null || extractedItems.trim().isEmpty()) {
         if (functionCallType.equals("run_console_cmd")) {
            return "Add commands to allow list";
         } else if (functionCallType.equals("run_terminal_cmd")) {
            return "Add commands to allow list";
         } else if (functionCallType.equals("run_file")) {
            return "Add file to allow list";
         }
      } else {
         String cleaned = extractedItems.trim();
         String prefix = "Add ";
         if (functionCallType.equals("run_file")) {
            prefix += cleaned + " to allow list";
         } else {
            prefix += cleaned + " to allow list";
         }
         return prefix;
      }
      
      return "Add to allow list";
   }

   /**
    * Adds click handler for vertical stack buttons using JSNI
    */
   private native void addVerticalStackClickHandler(com.google.gwt.dom.client.Element element, String buttonAction) /*-{
      var self = this;
      var messageId = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::messageId_;
      
      var clickHandler = function(event) {
         if (buttonAction === 'primary') {
            // This is the Run/Accept button
            var functionCallType = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::functionCallType_;
            if (functionCallType === 'search_replace') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::onAcceptClicked()();
            } else {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::onRunClicked()();
            }
         } else if (buttonAction === 'cancel') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::onCancelClicked()();
         } else if (buttonAction === 'allowList') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::onAllowListClicked()();
         }
         
         event.preventDefault();
         event.stopPropagation();
      };
      
      element.addEventListener('click', clickHandler, false);
   }-*/;
   
   /**
    * Standard method to hide all buttons in the widget
    */
   public void hideButtons() {
      hideVerticalStack();
   }
   
   /**
    * Hide the vertical button stack
    */
   protected void hideVerticalStack() {
      // This method will be overridden by widgets that use the vertical stack
   }
   
   /**
    * Standard method to enable/disable all buttons in the widget
    */
   public void setButtonsEnabled(boolean enabled) {
      setVerticalStackEnabled(enabled);
   }
   
   /**
    * Enable/disable the vertical button stack
    */
   protected void setVerticalStackEnabled(boolean enabled) {
      // This method will be overridden by widgets that use the vertical stack
   }
   
   // Common getters that all AI widgets should have
   public final String getMessageId() {
      return messageId_;
   }
   
   public final String getRequestId() {
      return requestId_;
   }
   
   // Abstract methods that subclasses must implement
   
   /**
    * Handle run button clicks - implemented by console/terminal widgets
    * Default implementation logs an error
    */
   protected void onRunClicked() {
      Debug.log("DEBUG: onRunClicked not implemented for " + getClass().getSimpleName());
   }
   
   /**
    * Handle accept button clicks - implemented by search_replace widgets  
    * Default implementation logs an error
    */
   protected void onAcceptClicked() {
      Debug.log("DEBUG: onAcceptClicked not implemented for " + getClass().getSimpleName());
   }
   
   /**
    * Handle cancel button clicks - implemented by all widgets
    * Default implementation logs an error
    */
   protected void onCancelClicked() {
      Debug.log("DEBUG: onCancelClicked not implemented for " + getClass().getSimpleName());
   }
   
   /**
    * Handle allow list button clicks - enables auto-run and adds items to allow list
    * Default implementation logs an error
    */
   protected void onAllowListClicked() {
      // onAllowListClicked not implemented for this widget type
   }
   
   /**
    * Create a copy button for copying widget content to clipboard
    * Returns an HTML widget with two rounded squares SVG icon
    */
   protected HTML createCopyButton()
   {
      HTML copyContainer = new HTML();
      copyContainer.addStyleName("ai-widget-copy-button");
      
      // Style as standalone copy button with no box
      copyContainer.getElement().getStyle().setProperty("cursor", "pointer");
      copyContainer.getElement().getStyle().setProperty("display", "inline-flex");
      copyContainer.getElement().getStyle().setProperty("alignItems", "center");
      copyContainer.getElement().getStyle().setProperty("justifyContent", "center");
      copyContainer.getElement().getStyle().setProperty("padding", "0");
      copyContainer.getElement().getStyle().setProperty("background", "none");
      copyContainer.getElement().getStyle().setProperty("border", "none");
      copyContainer.getElement().getStyle().setOpacity(0.7);
      copyContainer.getElement().getStyle().setProperty("marginRight", "4px");
      
      // Create SVG with two rounded squares (bottom-left and top-right)
      // Using thicker stroke for better visibility
      String copySvg = "<svg width='14' height='14' viewBox='0 0 16 16' fill='none' xmlns='http://www.w3.org/2000/svg'>" +
                       "<rect x='2' y='5' width='9' height='9' rx='1.5' stroke='currentColor' stroke-width='1.25' fill='none'/>" +
                       "<rect x='5' y='2' width='9' height='9' rx='1.5' stroke='currentColor' stroke-width='1.25' fill='none'/>" +
                       "</svg>";
      copyContainer.setHTML(copySvg);
      
      // Add hover effect using JSNI
      addCopyHoverEffect(copyContainer.getElement());
      
      return copyContainer;
   }
   
   /**
    * Add hover effect to copy button
    */
   private native void addCopyHoverEffect(com.google.gwt.dom.client.Element element) /*-{
      element.addEventListener('mouseenter', function() {
         element.style.opacity = '1';
      }, false);
      
      element.addEventListener('mouseleave', function() {
         element.style.opacity = '0.7';
      }, false);
   }-*/;
   
   /**
    * Show check icon briefly when copy succeeds, then restore copy icon
    */
   protected native void showCopySuccessAnimation(com.google.gwt.dom.client.Element element) /*-{
      // Save original HTML
      var originalHTML = element.innerHTML;
      
      // Show check icon with thicker stroke
      var checkSvg = "<svg width='14' height='14' viewBox='0 0 16 16' fill='none' xmlns='http://www.w3.org/2000/svg'>" +
                     "<path d='M14 3L6 13L2 9' stroke='currentColor' stroke-width='1.25' stroke-linecap='round' stroke-linejoin='round'/>" +
                     "</svg>";
      element.innerHTML = checkSvg;
      element.style.opacity = '1';
      
      // Restore original icon after 1 second
      setTimeout(function() {
         element.innerHTML = originalHTML;
         element.style.opacity = '0.7';
      }, 1000);
   }-*/;
   
   /**
    * Create a chevron button for collapsing/expanding the widget
    * Returns an HTML widget with the chevron SVG
    */
   protected HTML createChevronButton()
   {
      HTML chevronContainer = new HTML();
      chevronContainer.addStyleName("ai-widget-chevron-button");
      
      // Style as standalone chevron with no box
      chevronContainer.getElement().getStyle().setProperty("cursor", "pointer");
      chevronContainer.getElement().getStyle().setProperty("display", "inline-flex");
      chevronContainer.getElement().getStyle().setProperty("alignItems", "center");
      chevronContainer.getElement().getStyle().setProperty("justifyContent", "center");
      chevronContainer.getElement().getStyle().setProperty("padding", "0");
      chevronContainer.getElement().getStyle().setProperty("background", "none");
      chevronContainer.getElement().getStyle().setProperty("border", "none");
      chevronContainer.getElement().getStyle().setOpacity(0.7);
      chevronContainer.getElement().getStyle().setProperty("transform", "translateY(-2px)"); // Move up 2px
      
      // Create chevron SVG (starts in down/expanded state)
      String chevronSvg = createChevronSvg("down");
      chevronContainer.setHTML(chevronSvg);
      
      // Store the element for later updates
      chevronButton_ = chevronContainer.getElement();
      
      // Add click handler
      addChevronClickHandler(chevronButton_);
      
      // Add hover effect using JSNI
      addChevronHoverEffect(chevronButton_);
      
      return chevronContainer;
   }
   
   /**
    * Add hover effect to chevron button
    */
   private native void addChevronHoverEffect(com.google.gwt.dom.client.Element element) /*-{
      element.addEventListener('mouseenter', function() {
         element.style.opacity = '1';
      }, false);
      
      element.addEventListener('mouseleave', function() {
         element.style.opacity = '0.7';
      }, false);
   }-*/;
   
   /**
    * Create SVG for chevron in specified direction
    */
   protected String createChevronSvg(String direction)
   {
      String path;
      if ("down".equals(direction)) {
         // Chevron pointing down (expanded state)
         path = "M10 16L16 10L18 12L10 20L2 12L4 10Z";
      } else {
         // Chevron pointing right (collapsed state)
         path = "M16 10L10 16L12 18L20 10L12 2L10 4Z";
      }
      
      return "<svg width='16' height='16' viewBox='0 0 24 24' class='ai-widget-chevron-svg'>" +
             "<path d='" + path + "' fill='currentColor' stroke='currentColor' stroke-width='0.35'/>" +
             "</svg>";
   }
   
   /**
    * Add click handler to chevron button using JSNI
    */
   private native void addChevronClickHandler(com.google.gwt.dom.client.Element element) /*-{
      var self = this;
      
      var clickHandler = function(event) {
         self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::toggleCollapsed()();
         event.preventDefault();
         event.stopPropagation();
      };
      
      element.addEventListener('click', clickHandler, false);
   }-*/;
   
   /**
    * Toggle the collapsed/expanded state of the widget
    */
   protected void toggleCollapsed()
   {
      isExpanded_ = !isExpanded_;
      
      // Update chevron direction: down when expanded, right when collapsed
      if (chevronButton_ != null) {
         String direction = isExpanded_ ? "down" : "right";
         chevronButton_.setInnerHTML(createChevronSvg(direction));
         
         // Adjust vertical position: up 2px when down, normal position when right
         String transform = isExpanded_ ? "translateY(-2px)" : "translateY(1px)";
         chevronButton_.getStyle().setProperty("transform", transform);
      }
      
      // Toggle content visibility
      if (contentContainer_ != null) {
         contentContainer_.setVisible(isExpanded_);
      }
      
      // Update header styling based on collapsed state
      if (headerPanel_ != null) {
         if (isExpanded_) {
            // Expanded: rounded top corners only, no bottom border
            headerPanel_.getElement().getStyle().setProperty("borderRadius", "4px 4px 0 0");
            headerPanel_.getElement().getStyle().setProperty("borderBottom", "none");
         } else {
            // Collapsed: all corners rounded, show bottom border
            headerPanel_.getElement().getStyle().setProperty("borderRadius", "4px");
            headerPanel_.getElement().getStyle().setProperty("borderBottom", "1px solid " + ThemeHelper.getVisibleBorder());
         }
      }
   }
   
   /**
    * Set the content container that should be collapsed/expanded
    */
   protected void setContentContainer(Widget container)
   {
      contentContainer_ = container;
   }
} 