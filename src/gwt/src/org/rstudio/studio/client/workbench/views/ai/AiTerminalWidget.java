/*
 * AiTerminalWidget.java
 *
 * Copyright (C) 2022 by Posit Software, PBC
 *
 * Unless you have received this program directly from Posit Software pursuant
 * to the terms of a commercial license agreement with Posit Software, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.ai;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.dom.client.Style;

import org.rstudio.core.client.Debug;
import org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.core.client.widget.FontSizer;

public class AiTerminalWidget extends Composite
{
   public interface TerminalCommandHandler
   {
      void onRunCommand(String messageId, String command);
      void onCancelCommand(String messageId);
   }
   
   private final String messageId_;
   private final String initialCommand_;
   private final String explanation_;
   private final String requestId_;
   private final TerminalCommandHandler handler_;
   
   private Label promptLabel_;
   private AceEditor terminalInput_;
   private Button runButton_;
   private Button cancelButton_;
   
   public AiTerminalWidget(String messageId, String command, String explanation, String requestId, TerminalCommandHandler handler)
   {
      messageId_ = messageId;
      initialCommand_ = command;
      explanation_ = explanation;
      requestId_ = requestId;
      handler_ = handler;
      
      initWidget(createWidget(command));
      setupEditor();
   }
   
   private Widget createWidget(String initialCommand)
   {
      VerticalPanel container = new VerticalPanel();
      container.setWidth("100%");
      
      // Add Terminal header (always show for terminal widgets)
      Label headerLabel = new Label("Terminal");
      headerLabel.addStyleName("aiTerminalHeader");
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
      
      // Create terminal editor container
      HorizontalPanel editorContainer = new HorizontalPanel();
      editorContainer.setWidth("100%");
      editorContainer.addStyleName("aiTerminalEditorContainer");
      editorContainer.getElement().getStyle().setProperty("maxWidth", "100%");
      editorContainer.getElement().getStyle().setProperty("boxSizing", "border-box");
      
      // Create a wrapper around the entire editor container (prompt + editor) with the border
      SimplePanel terminalWrapper = new SimplePanel();
      terminalWrapper.setWidth("100%");
      terminalWrapper.addStyleName("aiTerminalWrapper");
      terminalWrapper.getElement().getStyle().setBorderWidth(1, Unit.PX);
      terminalWrapper.getElement().getStyle().setBorderStyle(com.google.gwt.dom.client.Style.BorderStyle.SOLID);
      terminalWrapper.getElement().getStyle().setBorderColor("#666");
      terminalWrapper.getElement().getStyle().setProperty("borderRadius", "0 0 4px 4px");
      terminalWrapper.getElement().getStyle().setBackgroundColor("white");
      terminalWrapper.getElement().getStyle().setPadding(0, Unit.PX);
      terminalWrapper.getElement().getStyle().setProperty("display", "block");
      terminalWrapper.getElement().getStyle().setProperty("boxSizing", "border-box");
      terminalWrapper.getElement().getStyle().setProperty("maxWidth", "100%");
      terminalWrapper.getElement().getStyle().setProperty("overflow", "hidden");
      
      // Create terminal prompt
      promptLabel_ = new Label("$");
      promptLabel_.addStyleName("aiTerminalPrompt");
      promptLabel_.getElement().getStyle().setProperty("fontFamily", "monospace");
      promptLabel_.getElement().getStyle().setFontWeight(com.google.gwt.dom.client.Style.FontWeight.BOLD);
      promptLabel_.getElement().getStyle().setColor("#000");
      promptLabel_.getElement().getStyle().setMarginRight(8, Unit.PX);
      promptLabel_.getElement().getStyle().setPaddingTop(2, Unit.PX);
      // Apply same font sizing as the ACE editor
      FontSizer.applyNormalFontSize(promptLabel_);
      editorContainer.add(promptLabel_);
      
      // Create the terminal input area using ACE editor
      terminalInput_ = createTerminalEditor();
      terminalInput_.getWidget().setWidth("100%");
      terminalInput_.getWidget().setHeight("auto");
      terminalInput_.getWidget().getElement().getStyle().setProperty("minHeight", "24px");
      // Don't set border styling on the editor since ACE overrides it anyway
      terminalInput_.getWidget().getElement().getStyle().setPadding(4, Unit.PX);
      terminalInput_.getWidget().getElement().getStyle().setProperty("maxWidth", "100%");
      terminalInput_.getWidget().getElement().getStyle().setProperty("boxSizing", "border-box");
      
      // Set initial command
      if (initialCommand != null && !initialCommand.trim().isEmpty())
      {
         terminalInput_.setCode(initialCommand, false);
      }
      
      // Add input directly to container, then container to wrapper
      editorContainer.add(terminalInput_.getWidget());
      editorContainer.setCellWidth(terminalInput_.getWidget(), "100%");
      terminalWrapper.setWidget(editorContainer);
      
      container.add(terminalWrapper);
      
      // Create button container with absolute positioning for precise control
      SimplePanel buttonContainer = new SimplePanel();
      buttonContainer.addStyleName("aiTerminalButtons");
      buttonContainer.setWidth("100%");
      buttonContainer.getElement().getStyle().setProperty("position", "relative");
      buttonContainer.getElement().getStyle().setHeight(0, Unit.PX); // No height so it doesn't take space
      buttonContainer.getElement().getStyle().setProperty("zIndex", "10");
      
      // Create a wrapper for both buttons with absolute positioning
      HorizontalPanel buttonWrapper = new HorizontalPanel();
      buttonWrapper.setSpacing(0);
      buttonWrapper.getElement().getStyle().setProperty("position", "absolute");
      buttonWrapper.getElement().getStyle().setProperty("top", "-9px"); // Move up 8px to touch terminal border
      buttonWrapper.getElement().getStyle().setProperty("right", "8px"); // 8px from right edge
      
      // Create native HTML button instead of GWT Button
      runButton_ = createNativeButton("Run", "aiTerminalRunButton");
      
      // Create native HTML button instead of GWT Button
      cancelButton_ = createNativeButton("Cancel", "aiTerminalCancelButton");
      
      buttonWrapper.add(runButton_);
      buttonWrapper.add(cancelButton_);
      
      buttonContainer.setWidget(buttonWrapper);
      container.add(buttonContainer);
      
      return container;
   }
   
   private AceEditor createTerminalEditor()
   {
      AceEditor editor = new AceEditor();
      
      // Configure for shell/bash syntax (instead of R like console)
      try {
         editor.setFileType(FileTypeRegistry.SH, true);
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
      
      // Set terminal-like styling
      editor.getWidget().addStyleName("aiTerminalEditor");
      
      // Apply proper font sizing using FontSizer system (same as edit_file widgets)
      FontSizer.applyNormalFontSize(editor.getWidget());
      
      return editor;
   }
   
   // Create native HTML button with native DOM events (copied from console widget)
   private Button createNativeButton(String text, String styleClass)
   {
      Button button = new Button(text);
      button.addStyleName(styleClass);
      
      // Apply styling based on button type
      if ("aiTerminalRunButton".equals(styleClass))
      {
         // Light green styling for run button
         button.getElement().getStyle().setBackgroundColor("#e6ffe6");
         button.getElement().getStyle().setColor("#006400");
         button.getElement().getStyle().setBorderColor("#006400");
      }
      else if ("aiTerminalCancelButton".equals(styleClass))
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
   
   // Add native DOM event handler using JSNI (copied from console widget)
   private native void addNativeClickHandler(com.google.gwt.dom.client.Element element, String buttonText) /*-{
      var self = this;
      var messageId = this.@org.rstudio.studio.client.workbench.views.ai.AiTerminalWidget::messageId_;
      
      element.addEventListener('click', function(event) {
         if (buttonText === 'Run') {
            self.@org.rstudio.studio.client.workbench.views.ai.AiTerminalWidget::onRunClicked()();
         } else if (buttonText === 'Cancel') {
            self.@org.rstudio.studio.client.workbench.views.ai.AiTerminalWidget::onCancelClicked()();
         }
         
         event.preventDefault();
         event.stopPropagation();
      }, true); // Use capture phase
   }-*/;
   
   private void setupEditor()
   {
      // Set initial command
      if (initialCommand_ != null && !initialCommand_.isEmpty())
      {
         terminalInput_.setCode(initialCommand_, false);
      }
      
      // Focus the input
      terminalInput_.focus();
   }
   
   private void onRunClicked()
   {
      if (handler_ != null)
      {
         // Get the command from the terminal input
         String command = terminalInput_.getCode();
         handler_.onRunCommand(messageId_, command);
         
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
         handler_.onCancelCommand(messageId_);
         
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
      return terminalInput_.getCode();
   }
   
   public void setCommand(String command)
   {
      terminalInput_.setCode(command, false);
   }
   
   public String getMessageId()
   {
      return messageId_;
   }
   
   public String getRequestId()
   {
      return requestId_;
   }
   
   public void focus()
   {
      terminalInput_.focus();
   }
} 