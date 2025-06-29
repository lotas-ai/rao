/*
 * AiConsoleWidget.java
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.user.client.ui.*;

import org.rstudio.core.client.widget.FontSizer;
import org.rstudio.core.client.widget.ThemedButton;
import org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.core.client.Debug;

public class AiConsoleWidget extends Composite
{
   public interface ConsoleCommandHandler
   {
      void onRun(String messageId, String command);
      void onCancel(String messageId);
   }
   
   public AiConsoleWidget(String messageId, 
                          String initialCommand, 
                          String explanation,
                          String requestId,
                          boolean isEditable,
                          ConsoleCommandHandler handler)
   {
      messageId_ = messageId;
      explanation_ = explanation;
      requestId_ = requestId;
      handler_ = handler;
      isEditable_ = isEditable;
      
      initWidget(createWidget(initialCommand));
      addStyleName("aiConsoleWidget");
   }
   
   private Widget createWidget(String initialCommand)
   {
      VerticalPanel container = new VerticalPanel();
      container.setWidth("100%");
      
      // Add header (determine based on command type)
      String headerText = determineHeaderText();
      Label headerLabel = new Label(headerText);
      headerLabel.addStyleName("aiConsoleHeader");
      headerLabel.getElement().getStyle().setBackgroundColor("#666");
      headerLabel.getElement().getStyle().setColor("white");
      headerLabel.getElement().getStyle().setFontSize(12, Unit.PX);
      headerLabel.getElement().getStyle().setFontWeight(com.google.gwt.dom.client.Style.FontWeight.BOLD);
      headerLabel.getElement().getStyle().setPadding(3, Unit.PX);
      headerLabel.getElement().getStyle().setProperty("borderRadius", "4px 4px 0 0");
      headerLabel.getElement().getStyle().setMargin(0, Unit.PX);
      headerLabel.getElement().getStyle().setProperty("width", "100%");
      headerLabel.getElement().getStyle().setProperty("boxSizing", "border-box");
      container.add(headerLabel);
      
      // Create console editor container
      HorizontalPanel editorContainer = new HorizontalPanel();
      editorContainer.setWidth("100%");
      editorContainer.addStyleName("aiConsoleEditorContainer");
      editorContainer.getElement().getStyle().setProperty("maxWidth", "100%");
      editorContainer.getElement().getStyle().setProperty("boxSizing", "border-box");
      
      // Create a wrapper around the entire editor container (prompt + editor) with the border
      SimplePanel consoleWrapper = new SimplePanel();
      consoleWrapper.setWidth("100%");
      consoleWrapper.addStyleName("aiConsoleWrapper");
      consoleWrapper.getElement().getStyle().setBorderWidth(1, Unit.PX);
      consoleWrapper.getElement().getStyle().setBorderStyle(com.google.gwt.dom.client.Style.BorderStyle.SOLID);
      consoleWrapper.getElement().getStyle().setBorderColor("#666");
      consoleWrapper.getElement().getStyle().setProperty("borderRadius", "0 0 4px 4px");
      consoleWrapper.getElement().getStyle().setBackgroundColor("white");
      consoleWrapper.getElement().getStyle().setPadding(0, Unit.PX);
      consoleWrapper.getElement().getStyle().setProperty("display", "block");
      consoleWrapper.getElement().getStyle().setProperty("boxSizing", "border-box");
      consoleWrapper.getElement().getStyle().setProperty("maxWidth", "100%");
      consoleWrapper.getElement().getStyle().setProperty("overflow", "hidden");
      
      // Create console prompt
      Label promptLabel = new Label(">");
      promptLabel.addStyleName("aiConsolePrompt");
      promptLabel.getElement().getStyle().setProperty("fontFamily", "monospace");
      promptLabel.getElement().getStyle().setFontWeight(com.google.gwt.dom.client.Style.FontWeight.BOLD);
      promptLabel.getElement().getStyle().setColor("#000");
      promptLabel.getElement().getStyle().setMarginRight(8, Unit.PX);
      promptLabel.getElement().getStyle().setPaddingTop(2, Unit.PX);
      // Apply same font sizing as the ACE editor
      FontSizer.applyNormalFontSize(promptLabel);
      editorContainer.add(promptLabel);
      
      // Create the ace editor for command input
      editor_ = createConsoleEditor();
      editor_.getWidget().setWidth("100%");
      editor_.getWidget().setHeight("auto");
      editor_.getWidget().getElement().getStyle().setProperty("minHeight", "24px");
      // Don't set border styling on the editor since ACE overrides it anyway
      editor_.getWidget().getElement().getStyle().setPadding(4, Unit.PX);
      editor_.getWidget().getElement().getStyle().setProperty("maxWidth", "100%");
      editor_.getWidget().getElement().getStyle().setProperty("boxSizing", "border-box");
      
      // Set initial command
      if (initialCommand != null && !initialCommand.trim().isEmpty())
      {
         editor_.setCode(initialCommand, false);
      }
      
      // Make read-only if not editable
      if (!isEditable_)
      {
         editor_.setReadOnly(true);
         editor_.getWidget().getElement().getStyle().setBackgroundColor("#f5f5f5");
         consoleWrapper.getElement().getStyle().setBackgroundColor("#f5f5f5");
      }
      
      // Add editor directly to container, then container to wrapper
      editorContainer.add(editor_.getWidget());
      editorContainer.setCellWidth(editor_.getWidget(), "100%");
      consoleWrapper.setWidget(editorContainer);
      
      container.add(consoleWrapper);
      
      // Create button container with absolute positioning for precise control
      SimplePanel buttonContainer = new SimplePanel();
      buttonContainer.addStyleName("aiConsoleButtons");
      buttonContainer.setWidth("100%");
      buttonContainer.getElement().getStyle().setProperty("position", "relative");
      buttonContainer.getElement().getStyle().setHeight(0, Unit.PX); // No height so it doesn't take space
      buttonContainer.getElement().getStyle().setProperty("zIndex", "10");
      
      // Create a wrapper for both buttons with absolute positioning
      HorizontalPanel buttonWrapper = new HorizontalPanel();
      buttonWrapper.setSpacing(0);
      buttonWrapper.getElement().getStyle().setProperty("position", "absolute");
      buttonWrapper.getElement().getStyle().setProperty("top", "-9px"); // Move up 8px to touch console border
      buttonWrapper.getElement().getStyle().setProperty("right", "8px"); // 8px from right edge
      
      // Create native HTML button instead of GWT Button
      runButton_ = createNativeButton("Run", "aiConsoleRunButton");
      
      // Create native HTML button instead of GWT Button
      cancelButton_ = createNativeButton("Cancel", "aiConsoleCancelButton");
      
      buttonWrapper.add(runButton_);
      buttonWrapper.add(cancelButton_);
      
      buttonContainer.setWidget(buttonWrapper);
      container.add(buttonContainer);
      
      return container;
   }
   
   private AceEditor createConsoleEditor()
   {
      AceEditor editor = new AceEditor();
      
      // Configure for R syntax
      try {
         editor.setFileType(FileTypeRegistry.R, true);
      } catch (Exception e) {
         // Fallback if FileTypeRegistry is not available
      }
      editor.setShowLineNumbers(false);
      editor.setShowPrintMargin(false);
      editor.setUseWrapMode(true);
      editor.setPadding(0);
      editor.autoHeight();
      
      // Hide the gutter (green line on the left)
      editor.getWidget().getEditor().getRenderer().setShowGutter(false);
      
      // Set console-like styling
      editor.getWidget().addStyleName("aiConsoleEditor");
      
      // Apply proper font sizing using FontSizer system (same as edit_file widgets)
      FontSizer.applyNormalFontSize(editor.getWidget());
      
      return editor;
   }
   
   // Create native HTML button with native DOM events
   private Button createNativeButton(String text, String styleClass)
   {
      Button button = new Button(text);
      button.addStyleName(styleClass);
      
      // Apply styling based on button type
      if ("aiConsoleRunButton".equals(styleClass))
      {
         // Light green styling for run button
         button.getElement().getStyle().setBackgroundColor("#e6ffe6");
         button.getElement().getStyle().setColor("#006400");
         button.getElement().getStyle().setBorderColor("#006400");
      }
      else if ("aiConsoleCancelButton".equals(styleClass))
      {
         // Light red styling for cancel button  
         button.getElement().getStyle().setBackgroundColor("#ffe6e6");
         button.getElement().getStyle().setColor("#8b0000");
         button.getElement().getStyle().setBorderColor("#8b0000");
      }
      
      // Common styling
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
      
      // Add native DOM click event listener
      addNativeClickHandler(button.getElement(), text);
      
      return button;
   }
   
   // Add native DOM event handler using JSNI
   private native void addNativeClickHandler(com.google.gwt.dom.client.Element element, String buttonText) /*-{
      var self = this;
      var messageId = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiConsoleWidget::messageId_;
      
      element.addEventListener('click', function(event) {
         if (buttonText === 'Run') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiConsoleWidget::onRunClicked()();
         } else if (buttonText === 'Cancel') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiConsoleWidget::onCancelClicked()();
         }
         
         event.preventDefault();
         event.stopPropagation();
      }, true); // Use capture phase
   }-*/;
   
   private void onRunClicked()
   {
      if (handler_ != null)
      {
         // Get the command from the editor
         String command = editor_.getCode();
         handler_.onRun(messageId_, command);
         
         // Disable buttons during execution
         setButtonsEnabled(false);
      }
      else
      {
         Debug.log("DEBUG: Handler is null! This is the problem.");
      }
   }
   
   private void onCancelClicked()
   {
      if (handler_ != null)
      {
         handler_.onCancel(messageId_);
         
         // Disable buttons during execution to prevent double-clicks
         setButtonsEnabled(false);
      }
      else
      {
         Debug.log("DEBUG: Handler is null! This is the problem.");
      }
   }
   
   public void setButtonsEnabled(boolean enabled)
   {
      runButton_.setEnabled(enabled);
      cancelButton_.setEnabled(enabled);
   }
   
   /**
    * Permanently hides the buttons (called when buttons are clicked)
    */
   public void hideButtons() {
      if (runButton_ != null) {
         // Remove focus before hiding to avoid aria-hidden accessibility issues
         runButton_.getElement().blur();
         runButton_.setVisible(false);
      }
      if (cancelButton_ != null) {
         // Remove focus before hiding to avoid aria-hidden accessibility issues
         cancelButton_.getElement().blur();
         cancelButton_.setVisible(false);
      }
   }
   
   public String getCommand()
   {
      return editor_.getCode();
   }
   
   public void setCommand(String command)
   {
      editor_.setCode(command, false);
   }
   
   public String getMessageId()
   {
      return messageId_;
   }
   
   public String getRequestId()
   {
      return requestId_;
   }
   
   /**
    * Determine the appropriate header text based on the command type
    */
   private String determineHeaderText()
   {
      if (explanation_ != null && explanation_.startsWith("Running:"))
      {
         return explanation_;
      }
      else
      {
         return "Console";
      }
   }
   
   private final String messageId_;
   private final String explanation_;
   private final String requestId_;
   private final ConsoleCommandHandler handler_;
   private final boolean isEditable_;
   private AceEditor editor_;
   private Button runButton_;
   private Button cancelButton_;
} 