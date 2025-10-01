/*
 * AiWidgetBase.java
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
package org.rstudio.studio.client.workbench.views.ai.widgets;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.dom.client.Style.Unit;
import org.rstudio.core.client.Debug;

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
      
      // Add border styling directly to ensure it shows and matches the widget
      buttonStack.getElement().getStyle().setProperty("backgroundColor", "#f5f5f5");
      // Match console header/wrapper border color (#666666)
      buttonStack.getElement().getStyle().setProperty("border", "1px solid #666666");
      buttonStack.getElement().getStyle().setProperty("borderTop", "none");
      buttonStack.getElement().getStyle().setProperty("borderRadius", "0 0 4px 4px");
      // Pull up to visually connect with the widget, indent both sides equally
      buttonStack.getElement().getStyle().setProperty("margin", "0px 8px 4px 8px");
      buttonStack.getElement().getStyle().setProperty("boxShadow", "0 1px 3px rgba(0,0,0,0.1)");
      
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
      HTML divider = new HTML("<hr style='margin: 0; border: none; border-top: 1px solid #d0d0d0;'>");
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
      button.getElement().getStyle().setWidth(100, Unit.PCT);
      button.getElement().getStyle().setProperty("boxSizing", "border-box");
      
      // Apply text color based on button type
      switch (textColor) {
         case "darkRed":
            button.getElement().getStyle().setColor("#8b0000"); // Same as error text
            break;
         case "darkGreen":
            button.getElement().getStyle().setColor("#006400");
            break;
         case "darkGray":
            button.getElement().getStyle().setColor("#666666");
            break;
      }
      
      // Add hover effects using DOM events
      addVerticalStackHoverEffects(button.getElement());
      
      // Add click handler
      addVerticalStackClickHandler(button.getElement(), buttonAction);
      
      return button;
   }

   /**
    * Adds hover effects to button elements
    */
   private native void addVerticalStackHoverEffects(com.google.gwt.dom.client.Element element) /*-{
      element.addEventListener('mouseenter', function() {
         element.style.backgroundColor = '#e6e6e6';
      });
      element.addEventListener('mouseleave', function() {
         element.style.backgroundColor = 'transparent';
      });
   }-*/;

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
} 