/*
 * AiTerminalWidget.java
 *
 * Copyright (C) 2025 by Lotas Inc.
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
import com.google.gwt.user.client.ui.FlowPanel;
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
import org.rstudio.core.client.theme.ThemeHelper;
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
   private SimplePanel terminalWrapper_;
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
      
      // Create header with chevron button using simple flex layout
      FlowPanel headerPanel = new FlowPanel();
      headerPanel.setWidth("100%");
      headerPanel.addStyleName("aiTerminalHeader");
      headerPanel.getElement().getStyle().setFontSize(12, Unit.PX);
      headerPanel.getElement().getStyle().setProperty("fontWeight", "650");
      headerPanel.getElement().getStyle().setPadding(3, Unit.PX);
      headerPanel.getElement().getStyle().setPaddingLeft(4, Unit.PX);
      headerPanel.getElement().getStyle().setProperty("borderRadius", "4px 4px 0 0");
      headerPanel.getElement().getStyle().setMargin(0, Unit.PX);
      headerPanel.getElement().getStyle().setProperty("boxSizing", "border-box");
      headerPanel.getElement().getStyle().setBorderWidth(1, Unit.PX);
      headerPanel.getElement().getStyle().setBorderStyle(com.google.gwt.dom.client.Style.BorderStyle.SOLID);
      headerPanel.getElement().getStyle().setBorderColor(ThemeHelper.getVisibleBorder());
      headerPanel.getElement().getStyle().setProperty("borderBottom", "none");
      headerPanel.getElement().getStyle().setProperty("position", "relative");
      headerPanel.getElement().getStyle().setProperty("display", "flex");
      headerPanel.getElement().getStyle().setProperty("alignItems", "center");
      headerPanel.getElement().getStyle().setProperty("justifyContent", "space-between");
      
      // Store reference for collapse functionality
      headerPanel_ = headerPanel;
      
      // Add header label
      Label headerLabel = new Label("Terminal");
      headerPanel.add(headerLabel);
      
      // Create container for copy button and chevron (right side of header)
      FlowPanel rightButtonsPanel = new FlowPanel();
      rightButtonsPanel.getElement().getStyle().setProperty("display", "flex");
      rightButtonsPanel.getElement().getStyle().setProperty("alignItems", "center");
      rightButtonsPanel.getElement().getStyle().setProperty("gap", "4px");
      
      // Add copy button (left of chevron)
      HTML copyButton = createCopyButton();
      copyButtonElement_ = copyButton.getElement();
      addCopyClickHandler(copyButtonElement_);
      rightButtonsPanel.add(copyButton);
      
      // Add chevron button on the far right
      HTML chevron = createChevronButton();
      rightButtonsPanel.add(chevron);
      
      headerPanel.add(rightButtonsPanel);
      
      container.add(headerPanel);
      
      // Create a vertical panel to hold all collapsible content (wrapper + future buttons)
      VerticalPanel collapsibleContent = new VerticalPanel();
      collapsibleContent.setWidth("100%");
      collapsibleContent.setSpacing(0);
      collapsibleContent.getElement().getStyle().setPadding(0, Unit.PX);
      collapsibleContent.getElement().getStyle().setMargin(0, Unit.PX);
      
      // Create terminal editor container using FlowPanel for proper flex layout
      FlowPanel editorContainer = new FlowPanel();
      editorContainer.getElement().getStyle().setProperty("display", "flex");
      editorContainer.getElement().getStyle().setProperty("alignItems", "flex-start");
      editorContainer.getElement().getStyle().setProperty("width", "100%");
      editorContainer.addStyleName(AiStreamingPanel.RES.styles().aiTerminalEditorContainer());
      editorContainer.addStyleName("ace_editor"); // Get background from ACE theme like main console
      editorContainer.getElement().getStyle().setProperty("maxWidth", "100%");
      editorContainer.getElement().getStyle().setProperty("boxSizing", "border-box");
      editorContainer.getElement().getStyle().setMargin(0, Unit.PX);
      editorContainer.getElement().getStyle().setPadding(0, Unit.PX);
      
      // Create a wrapper around the entire editor container (prompt + editor) with the border
      terminalWrapper_ = new SimplePanel();
      terminalWrapper_.setWidth("100%");
      terminalWrapper_.addStyleName("aiTerminalWrapper");
      terminalWrapper_.getElement().getStyle().setBorderWidth(1, Unit.PX);
      terminalWrapper_.getElement().getStyle().setBorderStyle(com.google.gwt.dom.client.Style.BorderStyle.SOLID);
      terminalWrapper_.getElement().getStyle().setBorderColor(ThemeHelper.getVisibleBorder());
      terminalWrapper_.getElement().getStyle().setProperty("borderRadius", "0 0 4px 4px");
      // Background color is handled by ACE theme, not manually set
      terminalWrapper_.getElement().getStyle().setPadding(0, Unit.PX);
      terminalWrapper_.getElement().getStyle().setMargin(0, Unit.PX);
      terminalWrapper_.getElement().getStyle().setProperty("lineHeight", "0");
      terminalWrapper_.getElement().getStyle().setProperty("display", "block");
      terminalWrapper_.getElement().getStyle().setProperty("boxSizing", "border-box");
      terminalWrapper_.getElement().getStyle().setProperty("maxWidth", "100%");
      
      // Create terminal prompt
      promptLabel_ = new Label("$");
      promptLabel_.addStyleName(AiStreamingPanel.RES.styles().aiTerminalPrompt());
      promptLabel_.getElement().getStyle().setColor(ThemeHelper.getSubtleText());
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
      
      // Set terminal styling
      terminalInput_.getWidget().addStyleName(AiStreamingPanel.RES.styles().aiTerminalEditor());
      
      // Apply the current ACE theme (same as main console) for proper background color
      terminalInput_.getWidget().getEditor().setTheme(RStudioGinjector.INSTANCE.getAceThemes().getCurrentTheme());
      
      // Apply font sizing for proper integration
      FontSizer.applyNormalFontSize(terminalInput_.getWidget());
      
      terminalInput_.getWidget().setWidth("100%");
      terminalInput_.getWidget().getElement().getStyle().setProperty("flex", "1");
      editorContainer.add(terminalInput_.getWidget());
      
      // Add editorContainer to wrapper
      terminalWrapper_.setWidget(editorContainer);
      collapsibleContent.add(terminalWrapper_);
      
      // Add collapsible content to container
      container.add(collapsibleContent);
      
      // Set the content container for collapse/expand functionality
      setContentContainer(collapsibleContent);
      
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
      
      // Buttons should be added to the collapsible content container
      if (contentContainer_ == null || !(contentContainer_ instanceof VerticalPanel)) {
         return;
      }
      VerticalPanel collapsibleContent = (VerticalPanel) contentContainer_;
      
      // Create the new vertical button stack using commands from R
      verticalButtonStack_ = createVerticalButtonStack(functionCallType_, extractedCommands_);
      
      // Create a DIV wrapper with flexbox to hold the button stack on the right
      FlowPanel buttonWrapper = new FlowPanel();
      buttonWrapper.getElement().getStyle().setProperty("display", "flex");
      buttonWrapper.getElement().getStyle().setProperty("justifyContent", "flex-end");
      buttonWrapper.getElement().getStyle().setProperty("width", "100%");
      buttonWrapper.getElement().getStyle().setMargin(0, Unit.PX);
      buttonWrapper.getElement().getStyle().setPadding(0, Unit.PX);
      
      // Add the button stack to the wrapper
      buttonWrapper.add(verticalButtonStack_);
      
      // Add the button wrapper to the collapsible content with no spacing
      collapsibleContent.add(buttonWrapper);
      collapsibleContent.setCellHeight(buttonWrapper, "0px"); // Minimize height
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
         if (parent instanceof FlowPanel) {
            FlowPanel buttonWrapper = (FlowPanel) parent;
            buttonWrapper.remove(verticalButtonStack_);
            
            // Recreate with updated commands
            verticalButtonStack_ = createVerticalButtonStack(functionCallType_, extractedCommands_);
            buttonWrapper.add(verticalButtonStack_);
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
      
      // Auto-scroll to bottom
      if (terminalWrapper_ != null)
      {
         terminalWrapper_.getElement().setScrollTop(terminalWrapper_.getElement().getScrollHeight());
      }
   }
   
   public void focus()
   {
      terminalInput_.focus();
   }
   
   /**
    * Add click handler for copy button using JSNI
    */
   private native void addCopyClickHandler(com.google.gwt.dom.client.Element element) /*-{
      var self = this;
      
      var clickHandler = function(event) {
         self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiTerminalWidget::onCopyClicked()();
         event.preventDefault();
         event.stopPropagation();
      };
      
      element.addEventListener('click', clickHandler, false);
   }-*/;
   
   /**
    * Handle copy button click - copies command text to clipboard
    */
   private void onCopyClicked()
   {
      String command = terminalInput_.getCode();
      org.rstudio.core.client.dom.Clipboard.setText(command);
      
      // Show success animation
      if (copyButtonElement_ != null) {
         showCopySuccessAnimation(copyButtonElement_);
      }
   }
   
   private com.google.gwt.dom.client.Element copyButtonElement_;
} 