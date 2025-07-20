/*
 * AiTerminalWidget.java
 *
 * Copyright (C) 2025 by William Nickols
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
package org.rstudio.studio.client.workbench.views.ai.widgets;

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

import org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.core.client.widget.FontSizer;

public class AiTerminalWidget extends AiWidgetBase
{
   public interface TerminalCommandHandler
   {
      void onRunCommand(String messageId, String command);
      void onCancelCommand(String messageId);
   }
   
   private final String initialCommand_;
   private final String explanation_;
   private final TerminalCommandHandler handler_;
   
   private Label promptLabel_;
   private AceEditor terminalInput_;
   private Button runButton_;
   private Button cancelButton_;
   
   public AiTerminalWidget(String messageId, String command, String explanation, String requestId, TerminalCommandHandler handler)
   {
      super(messageId, requestId);
      initialCommand_ = command;
      explanation_ = explanation;
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
      promptLabel_.getElement().getStyle().setColor("#666");
      promptLabel_.getElement().getStyle().setPaddingLeft(3, Unit.PX);
      promptLabel_.getElement().getStyle().setProperty("whiteSpace", "nowrap");
      editorContainer.add(promptLabel_);
      
      // Create ACE editor for command input (to match console styling)
      terminalInput_ = new AceEditor();
      terminalInput_.setFileType(FileTypeRegistry.SH, true);
      terminalInput_.setShowLineNumbers(false);
      terminalInput_.setShowPrintMargin(false);
      terminalInput_.getWidget().getElement().getStyle().setProperty("flexGrow", "1");
      terminalInput_.getWidget().getElement().getStyle().setProperty("minWidth", "0");
      terminalInput_.getWidget().getElement().getStyle().setBorderWidth(0, Unit.PX);
      terminalInput_.setUseWrapMode(true);
      terminalInput_.autoHeight();
      
      // Apply font sizing for proper integration
      FontSizer.applyNormalFontSize(terminalInput_.getWidget());
      
      editorContainer.add(terminalInput_.getWidget());
      editorContainer.setCellWidth(terminalInput_.getWidget(), "100%");
      
      // Add editorContainer to wrapper
      terminalWrapper.setWidget(editorContainer);
      container.add(terminalWrapper);
      
      // Create a container for buttons
      SimplePanel buttonContainer = new SimplePanel();
      buttonContainer.addStyleName("aiTerminalButtons");
      buttonContainer.setWidth("100%");
      buttonContainer.getElement().getStyle().setProperty("position", "relative");
      buttonContainer.getElement().getStyle().setHeight(0, Unit.PX); // No height so it doesn't take space
      buttonContainer.getElement().getStyle().setProperty("zIndex", "10");
      
      // Create button wrapper
      HorizontalPanel buttonWrapper = new HorizontalPanel();
      buttonWrapper.setSpacing(0);
      buttonWrapper.getElement().getStyle().setProperty("position", "absolute");
      buttonWrapper.getElement().getStyle().setProperty("top", "-9px");
      buttonWrapper.getElement().getStyle().setProperty("right", "8px");
      buttonWrapper.getElement().getStyle().setProperty("zIndex", "999");
      
      // Create buttons using base class method
      runButton_ = createStandardButton("Run", "aiTerminalRunButton", "run");
      cancelButton_ = createStandardButton("Cancel", "aiTerminalCancelButton", "cancel");
      
      buttonWrapper.add(runButton_);
      buttonWrapper.add(cancelButton_);
      
      buttonContainer.setWidget(buttonWrapper);
      container.add(buttonContainer);
      
      return container;
   }
   

   
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
   
   // Implement abstract methods from AiWidgetBase
   
   @Override
   protected Button[] getStandardButtons() {
      return new Button[] { runButton_, cancelButton_ };
   }
   
   @Override
   protected void onRunClicked()
   {
      if (handler_ != null)
      {
         // Get the command from the terminal input
         String command = terminalInput_.getCode();
         handler_.onRunCommand(getMessageId(), command);
         
         // Disable buttons during execution
         setButtonsEnabled(false);
      }
   }
   
   @Override
   protected void onCancelClicked()
   {
      if (handler_ != null)
      {
         handler_.onCancelCommand(getMessageId());
         
         // Disable buttons during execution to prevent double-clicks
         setButtonsEnabled(false);
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
   
   public void focus()
   {
      terminalInput_.focus();
   }
} 