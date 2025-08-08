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
import com.google.gwt.user.client.ui.HTML;
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
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;

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
   private VerticalPanel verticalButtonStack_;
   
   public AiTerminalWidget(String messageId, String command, String explanation, String requestId, TerminalCommandHandler handler)
   {
      super(messageId, requestId, "run_terminal_cmd");
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
      editorContainer.getElement().getStyle().setMargin(0, Unit.PX);
      editorContainer.getElement().getStyle().setPadding(0, Unit.PX);
      editorContainer.getElement().getStyle().setProperty("borderCollapse", "collapse");
      
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
      terminalWrapper.getElement().getStyle().setMargin(0, Unit.PX);
      terminalWrapper.getElement().getStyle().setProperty("lineHeight", "0");
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
      editorContainer.setCellVerticalAlignment(promptLabel_, HorizontalPanel.ALIGN_MIDDLE);
      
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
      
      // Don't create buttons during widget creation - they will be created when streaming completes
      
      return container;
   }
   
   /**
    * Create buttons when streaming completes
    */
   public void createButtons() {
      if (verticalButtonStack_ != null) {
         return; // Buttons already created
      }
      
      // Find the main container to add buttons to
      Widget widget = this.getWidget();
      if (!(widget instanceof VerticalPanel)) {
         return;
      }
      VerticalPanel container = (VerticalPanel) widget;
      
      // Create the new vertical button stack using commands from R
      verticalButtonStack_ = createVerticalButtonStack(functionCallType_, extractedCommands_);
      
      // Create a horizontal panel to hold the button stack on the right
      HorizontalPanel buttonRow = new HorizontalPanel();
      buttonRow.setWidth("100%");
      buttonRow.setHorizontalAlignment(HorizontalPanel.ALIGN_RIGHT);
      buttonRow.getElement().getStyle().setMargin(0, Unit.PX); // Remove any margin
      buttonRow.getElement().getStyle().setPadding(0, Unit.PX); // Remove any padding
      
      // Add the button stack to the right side
      buttonRow.add(verticalButtonStack_);
      buttonRow.setCellHorizontalAlignment(verticalButtonStack_, HorizontalPanel.ALIGN_RIGHT);
      
      // Add the button row to the main container with no spacing
      container.add(buttonRow);
      container.setCellHeight(buttonRow, "0px"); // Minimize height
   }

   // Field to store extracted commands passed from R
   private String extractedCommands_ = "";
   
   /**
    * Set the extracted commands list from R
    */
   public void setExtractedCommands(String extractedCommands) {
      extractedCommands_ = extractedCommands != null ? extractedCommands : "";
      
      // Update the button text if buttons already exist
      updateButtonText();
   }
   
   /**
    * Update the button text with the current extracted commands
    */
   private void updateButtonText() {
      if (verticalButtonStack_ != null) {
         // Find the parent container and recreate the button stack
         Widget parent = verticalButtonStack_.getParent();
         if (parent instanceof HorizontalPanel) {
            HorizontalPanel buttonRow = (HorizontalPanel) parent;
            buttonRow.remove(verticalButtonStack_);
            
            // Recreate with updated commands
            verticalButtonStack_ = createVerticalButtonStack(functionCallType_, extractedCommands_);
            buttonRow.add(verticalButtonStack_);
            buttonRow.setCellHorizontalAlignment(verticalButtonStack_, HorizontalPanel.ALIGN_RIGHT);
         }
      }
   }
   
   @Override
   protected void setVerticalStackEnabled(boolean enabled) {
      if (verticalButtonStack_ != null) {
         // Enable/disable the entire vertical stack
         verticalButtonStack_.getElement().getStyle().setProperty("pointerEvents", enabled ? "auto" : "none");
         verticalButtonStack_.getElement().getStyle().setOpacity(enabled ? 1.0 : 0.5);
         
         // Update each button's style
         for (int i = 0; i < verticalButtonStack_.getWidgetCount(); i++) {
            Widget widget = verticalButtonStack_.getWidget(i);
            if (widget instanceof HTML) {
               HTML button = (HTML) widget;
               if (enabled) {
                  button.getElement().getStyle().setProperty("cursor", "pointer");
                  button.getElement().getStyle().clearOpacity();
               } else {
                  button.getElement().getStyle().setProperty("cursor", "not-allowed");
                  button.getElement().getStyle().setOpacity(0.5);
               }
            }
         }
      }
   }
   
   @Override
   protected void hideVerticalStack() {
      if (verticalButtonStack_ != null) {
         verticalButtonStack_.setVisible(false);
      }
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
   
   @Override
   protected void onAllowListClicked()
   {
      if (extractedCommands_ != null && !extractedCommands_.trim().isEmpty()) {
         // Enable auto-accept terminal and add commands to allow list
         enableAutoAcceptTerminalWithCommands(extractedCommands_);
         
         // Then execute the current command automatically
         if (handler_ != null) {
            String command = terminalInput_.getCode();
            handler_.onRunCommand(getMessageId(), command);
            setButtonsEnabled(false);
         }
      } else {
         // Just enable auto-accept terminal
         enableAutoAcceptTerminal();
         
         // Then execute the current command automatically
         if (handler_ != null) {
            String command = terminalInput_.getCode();
            handler_.onRunCommand(getMessageId(), command);
            setButtonsEnabled(false);
         }
      }
   }

   /**
    * Enable auto-accept terminal and add extracted commands to allow list
    */
   private void enableAutoAcceptTerminalWithCommands(String commands) {
      AiServerOperations server = RStudioGinjector.INSTANCE.getServer();
      
      // First, enable auto-accept terminal
      server.setAutoAcceptTerminal(true, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Now add commands to the allow list
            addCommandsToAllowList(commands);
         }
         
         @Override
         public void onError(ServerError error) {
            // Failed to enable auto-accept terminal
         }
      });
   }

   /**
    * Enable auto-accept terminal without specific commands
    */
   private void enableAutoAcceptTerminal() {
      AiServerOperations server = RStudioGinjector.INSTANCE.getServer();
      
      // Enable auto-accept terminal
      server.setAutoAcceptTerminal(true, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Auto-accept terminal enabled successfully
         }
         
         @Override
         public void onError(ServerError error) {
            // Failed to enable auto-accept terminal
         }
      });
   }
   
   /**
    * Add commands to the terminal allow list
    */
   private void addCommandsToAllowList(String commands) {
      if (commands == null || commands.trim().isEmpty()) {
         return;
      }
      
      AiServerOperations server = RStudioGinjector.INSTANCE.getServer();
      
      // Get current allow list
      server.getAutomationList("auto_accept_terminal_allow_list", new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject response) {
            // Convert response to array and add new commands
            JsArrayString currentList = response.cast();
            JsArrayString newList = JavaScriptObject.createArray().cast();
            
            // Add existing items
            for (int i = 0; i < currentList.length(); i++) {
               newList.push(currentList.get(i));
            }
            
            // Add new commands (split by comma)
            String[] commandArray = commands.split(",\\s*");
            for (String cmd : commandArray) {
               if (!cmd.trim().isEmpty()) {
                  // Check if command is already in the list
                  boolean exists = false;
                  for (int i = 0; i < currentList.length(); i++) {
                     if (currentList.get(i).equals(cmd.trim())) {
                        exists = true;
                        break;
                     }
                  }
                  if (!exists) {
                     newList.push(cmd.trim());
                  }
               }
            }
            
            // Save updated list
            server.setAutomationList("auto_accept_terminal_allow_list", newList, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
                  // Commands added to terminal allow list successfully
               }
               
               @Override
               public void onError(ServerError error) {
                  // Failed to add commands to terminal allow list
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            // Failed to get terminal allow list
         }
      });
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