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
   
   // Constructor for common fields
   protected AiWidgetBase(String messageId, String requestId)
   {
      messageId_ = messageId;
      requestId_ = requestId;
   }
   
   /**
    * Creates a standardized button with consistent styling across all AI widgets
    * @param text The button text
    * @param styleClass The CSS class for widget-specific styling  
    * @param buttonType The type of button for color theming ("run", "accept", "cancel")
    * @return Styled button ready for use
    */
   protected Button createStandardButton(String text, String styleClass, String buttonType)
   {
      Button button = new Button(text);
      button.addStyleName(styleClass);
      
      // Apply color styling based on button type
      switch (buttonType.toLowerCase()) {
         case "run":
         case "accept":
            // Light green styling for positive actions
            button.getElement().getStyle().setBackgroundColor("#e6ffe6");
            button.getElement().getStyle().setColor("#006400");
            button.getElement().getStyle().setBorderColor("#006400");
            break;
         case "cancel":
            // Light red styling for cancel actions
            button.getElement().getStyle().setBackgroundColor("#ffe6e6");
            button.getElement().getStyle().setColor("#8b0000");
            button.getElement().getStyle().setBorderColor("#8b0000");
            break;
         default:
            // Default neutral styling
            button.getElement().getStyle().setBackgroundColor("#f5f5f5");
            button.getElement().getStyle().setColor("#333");
            button.getElement().getStyle().setBorderColor("#ccc");
            break;
      }
      
      // Common styling for all buttons
      applyCommonButtonStyling(button);
      
      // Add native DOM click event listener
      addStandardClickHandler(button.getElement(), text);
      
      return button;
   }
   
   /**
    * Applies consistent styling to all AI widget buttons
    */
   private void applyCommonButtonStyling(Button button)
   {
      button.getElement().getStyle().setBorderWidth(1, Unit.PX);
      button.getElement().getStyle().setBorderStyle(com.google.gwt.dom.client.Style.BorderStyle.SOLID);
      button.getElement().getStyle().setPadding(2, Unit.PX);
      button.getElement().getStyle().setPaddingLeft(6, Unit.PX);
      button.getElement().getStyle().setPaddingRight(6, Unit.PX);
      button.getElement().getStyle().setProperty("borderRadius", "3px");
      button.getElement().getStyle().setProperty("cursor", "pointer");
      button.getElement().getStyle().setProperty("pointerEvents", "auto");
      button.getElement().getStyle().setFontSize(11, Unit.PX);
      button.getElement().getStyle().setMarginLeft(0, Unit.PX);
      button.getElement().getStyle().setMarginRight(0, Unit.PX);
      button.getElement().getStyle().setProperty("transition", "background-color 0.2s");
   }
   
   /**
    * Adds standardized native DOM click event handler to buttons
    * Uses JSNI for reliable cross-browser event handling
    */
   private native void addStandardClickHandler(com.google.gwt.dom.client.Element element, String buttonText) /*-{
      var self = this;
      var messageId = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::messageId_;
      
      // Standard click handler that delegates to widget-specific methods
      var clickHandler = function(event) {
         // Delegate to subclass-specific handler based on button text
         if (buttonText === 'Run') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::onRunClicked()();
         } else if (buttonText === 'Accept') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::onAcceptClicked()();
         } else if (buttonText === 'Cancel') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiWidgetBase::onCancelClicked()();
         }
         
         event.preventDefault();
         event.stopPropagation();
      };
      
      // Add event listeners for both capture and bubble phases for reliability
      element.addEventListener('click', clickHandler, true);  // Capture phase
      element.addEventListener('click', clickHandler, false); // Bubble phase
      
      // Also add mousedown event as backup
      element.addEventListener('mousedown', clickHandler, true);
   }-*/;
   
   /**
    * Standard method to hide all buttons in the widget
    * Subclasses should override if they have additional buttons or special handling
    */
   public void hideButtons() {
      hideButtonsInternal();
   }
   
   /**
    * Internal method to hide the standard buttons
    * Subclasses can call this and add their own button hiding logic
    */
   protected final void hideButtonsInternal() {
      Button[] buttons = getStandardButtons();
      for (Button button : buttons) {
         if (button != null) {
            // Remove focus before hiding to avoid accessibility issues
            button.getElement().blur();
            button.setVisible(false);
         }
      }
   }
   
   /**
    * Standard method to enable/disable all buttons in the widget
    * Subclasses should override if they have additional buttons
    */
   public void setButtonsEnabled(boolean enabled) {
      Button[] buttons = getStandardButtons();
      for (Button button : buttons) {
         if (button != null) {
            button.setEnabled(enabled);
         }
      }
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
    * Returns array of standard buttons (run/accept, cancel) for the widget
    * Used by hideButtons() and setButtonsEnabled() methods
    */
   protected abstract Button[] getStandardButtons();
   
   /**
    * Handle run button clicks - implemented by console/terminal widgets
    * Default implementation logs an error
    */
   protected void onRunClicked() {
      Debug.log("DEBUG: onRunClicked not implemented for " + getClass().getSimpleName());
   }
   
   /**
    * Handle accept button clicks - implemented by edit_file/search_replace widgets  
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
} 