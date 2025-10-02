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
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import org.rstudio.core.client.theme.ThemeHelper;

public class AiConsoleWidget extends AiWidgetBase
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
                          ConsoleCommandHandler handler,
                          String functionCallType)
   {
      super(messageId, requestId, functionCallType != null ? functionCallType : "run_console_cmd");
      explanation_ = explanation;
      handler_ = handler;
      isEditable_ = isEditable;
      
      initWidget(createWidget(initialCommand));
      addStyleName(AiStreamingPanel.RES.styles().aiConsoleWidget());
   }
   
   private VerticalPanel container_;
   private SimplePanel consoleWrapper_;
   private Label headerLabel_;

   private Widget createWidget(String initialCommand)
   {
      container_ = new VerticalPanel();
      container_.setWidth("100%");
      container_.setSpacing(0);
      container_.getElement().getStyle().setPadding(0, Unit.PX);
      container_.getElement().getStyle().setMargin(0, Unit.PX);
      
      // Add header (determine based on command type)
      String headerText = determineHeaderText();
      headerLabel_ = new Label(headerText);
      headerLabel_.addStyleName("aiConsoleHeader");
      // Background and text colors are handled by CSS theme classes
      headerLabel_.getElement().getStyle().setFontSize(12, Unit.PX);
      headerLabel_.getElement().getStyle().setFontWeight(com.google.gwt.dom.client.Style.FontWeight.BOLD);
      headerLabel_.getElement().getStyle().setPadding(3, Unit.PX);
      headerLabel_.getElement().getStyle().setProperty("borderRadius", "4px 4px 0 0");
      headerLabel_.getElement().getStyle().setMargin(0, Unit.PX);
      headerLabel_.getElement().getStyle().setProperty("width", "100%");
      headerLabel_.getElement().getStyle().setProperty("boxSizing", "border-box");
      headerLabel_.getElement().getStyle().setBorderWidth(1, Unit.PX);
      headerLabel_.getElement().getStyle().setBorderStyle(com.google.gwt.dom.client.Style.BorderStyle.SOLID);
      headerLabel_.getElement().getStyle().setBorderColor(ThemeHelper.getVisibleBorder());
      headerLabel_.getElement().getStyle().setProperty("borderBottom", "none");
      container_.add(headerLabel_);
      
      // Create console editor container
      HorizontalPanel editorContainer = new HorizontalPanel();
      editorContainer.setWidth("100%");
      editorContainer.addStyleName(AiStreamingPanel.RES.styles().aiConsoleEditorContainer());
      editorContainer.addStyleName("ace_editor"); // Get background from ACE theme like main console
      editorContainer.getElement().getStyle().setProperty("maxWidth", "100%");
      editorContainer.getElement().getStyle().setProperty("boxSizing", "border-box");
      editorContainer.getElement().getStyle().setMargin(0, Unit.PX);
      editorContainer.getElement().getStyle().setPadding(0, Unit.PX);
      
      // Create a wrapper around the entire editor container (prompt + editor) with the border
      consoleWrapper_ = new SimplePanel();
      consoleWrapper_.setWidth("100%");
      consoleWrapper_.addStyleName("aiConsoleWrapper");
      consoleWrapper_.getElement().getStyle().setBorderWidth(1, Unit.PX);
      consoleWrapper_.getElement().getStyle().setBorderStyle(com.google.gwt.dom.client.Style.BorderStyle.SOLID);
      consoleWrapper_.getElement().getStyle().setBorderColor(ThemeHelper.getVisibleBorder());
      consoleWrapper_.getElement().getStyle().setProperty("borderRadius", "0 0 4px 4px");
      // Background color is handled by ACE theme, not manually set
      consoleWrapper_.getElement().getStyle().setPadding(0, Unit.PX);
      consoleWrapper_.getElement().getStyle().setMargin(0, Unit.PX);
      consoleWrapper_.getElement().getStyle().setProperty("lineHeight", "0");
      consoleWrapper_.getElement().getStyle().setProperty("display", "block");
      consoleWrapper_.getElement().getStyle().setProperty("boxSizing", "border-box");
      consoleWrapper_.getElement().getStyle().setProperty("maxWidth", "100%");
      consoleWrapper_.getElement().getStyle().setProperty("overflow", "hidden");
      
      // Create console prompt (matching main RStudio console)
      Label promptLabel = new Label(">");
      promptLabel.addStyleName(AiStreamingPanel.RES.styles().aiConsolePrompt());
      promptLabel.addStyleName("ace_keyword"); // Use same color as main console prompt
      promptLabel.getElement().getStyle().setProperty("fontFamily", "monospace");
      promptLabel.getElement().getStyle().setPaddingLeft(3, Unit.PX);
      promptLabel.getElement().getStyle().setMarginRight(8, Unit.PX);
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
         // Disabled background colors are handled by CSS theme classes
      }
      
      // Add editor directly to container, then container to wrapper
      editor_.getWidget().setWidth("100%");
      editorContainer.add(editor_.getWidget());
      editorContainer.setCellWidth(editor_.getWidget(), "100%");
      consoleWrapper_.setWidget(editorContainer);

      container_.add(consoleWrapper_);
      
      // Don't create buttons during widget creation - they will be created when streaming completes
      
      return container_;
   }

   /**
    * Add a display row (icon + text) to the console area below the header.
    * Type can be: "file", "folder", "chat", "docs".
    */
   public void addContextDisplayRow(String type, String text)
   {
      if (container_ == null) return;
      HorizontalPanel row = new HorizontalPanel();
      row.getElement().getStyle().setPaddingTop(2, Unit.PX);
      row.getElement().getStyle().setPaddingBottom(2, Unit.PX);
      row.getElement().getStyle().setPaddingLeft(6, Unit.PX);
      row.getElement().getStyle().setPaddingRight(6, Unit.PX);
      row.getElement().getStyle().setProperty("alignItems", "center");

      // Icon span
      HTML icon = new HTML("", false);
      icon.getElement().getStyle().setProperty("display", "inline-block");
      icon.getElement().getStyle().setWidth(16, Unit.PX);
      icon.getElement().getStyle().setHeight(16, Unit.PX);

      String svg;
      if ("folder".equals(type))
      {
         // New folder icon
         svg = "<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='" + ThemeHelper.getIconColor() + "' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'>"
             + "<path d='M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'/>"
             + "</svg>";
      }
      else if ("file".equals(type))
      {
         // Page with folded corner (matches Files & Folders menu file icon)
         svg = "<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='" + ThemeHelper.getIconColor() + "' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'>"
             + "<path d='M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/>"
             + "<polyline points='14 2 14 8 20 8'/>"
             + "</svg>";
      }
      else if ("chat".equals(type))
      {
         // Chat bubble
         svg = "<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='" + ThemeHelper.getIconColor() + "' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'>"
             + "<path d='M21 15a2 2 0 0 1-2 2H8l-4 4V5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2z'/>"
             + "</svg>";
      }
      else // docs
      {
         // Open book (current Docs icon)
         svg = "<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='" + ThemeHelper.getIconColor() + "' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'>"
             + "<path d='M12 7 A9 9 0 0 0 3 7'/>"
             + "<path d='M12 7 A9 9 0 0 1 21 7'/>"
             + "<path d='M3 7 L3 19'/>"
             + "<path d='M21 7 L21 19'/>"
             + "<path d='M3 19 Q7 16 12 19'/>"
             + "<path d='M21 19 Q17 16 12 19'/>"
             + "<path d='M12 8 L12 19'/>"
             + "</svg>";
      }
      icon.setHTML(svg);

      // Text label
      Label label = new Label(text == null ? "" : text);
      label.getElement().getStyle().setMarginLeft(6, Unit.PX);
      label.getElement().getStyle().setFontSize(11, Unit.PX);

      row.add(icon);
      row.add(label);

      // Insert below header (index 1), above console wrapper
      int insertIndex = 1;
      container_.insert(row, insertIndex);
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
      
      // Create the new vertical button stack using functions from R
      verticalButtonStack_ = createVerticalButtonStack(functionCallType_, extractedFunctions_);
      
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

   // Fields to store extracted functions and files passed from R
   private String extractedFunctions_ = "";
   private String extractedFiles_ = "";
   
   /**
    * Set the extracted functions list from R
    */
   public void setExtractedFunctions(String extractedFunctions) {
      extractedFunctions_ = extractedFunctions != null ? extractedFunctions.trim() : "";
      
      // Update the button text if buttons already exist
      updateButtonText();
   }
   
   /**
    * Set the extracted files list from R (for run_file commands)
    */
   public void setExtractedFiles(String extractedFiles) {
      extractedFiles_ = extractedFiles != null ? extractedFiles.trim() : "";
      
      // Update the button text if buttons already exist
      updateButtonText();
   }
   
   /**
    * Update the button text with the current extracted items (functions or files)
    */
   private void updateButtonText() {
      // Determine which extracted items to use based on function call type
      String extractedItems = "";
      if ("run_file".equals(functionCallType_)) {
         extractedItems = extractedFiles_;
      } else {
         extractedItems = extractedFunctions_;
      }
      
      if (verticalButtonStack_ != null) {
         // Find the parent container and recreate the button stack
         Widget parent = verticalButtonStack_.getParent();
         if (parent instanceof HorizontalPanel) {
            HorizontalPanel buttonRow = (HorizontalPanel) parent;
            buttonRow.remove(verticalButtonStack_);
            
            // Recreate with updated items
            verticalButtonStack_ = createVerticalButtonStack(functionCallType_, extractedItems);
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
      
      // Apply the current ACE theme (same as main console) for proper background color
      editor.getWidget().getEditor().setTheme(RStudioGinjector.INSTANCE.getAceThemes().getCurrentTheme());
      
      // Apply proper font sizing using FontSizer system
      FontSizer.applyNormalFontSize(editor.getWidget());
      
      return editor;
   }
   
   
   // Implement abstract methods from AiWidgetBase
   
   @Override
   protected void onRunClicked()
   {
      if (handler_ != null)
      {
         // Get the command from the editor
         String command = editor_.getCode();
         handler_.onRun(getMessageId(), command);
         
         // Disable buttons during execution
         setButtonsEnabled(false);
      }
   }
   
   @Override
   protected void onCancelClicked()
   {
      if (handler_ != null)
      {
         handler_.onCancel(getMessageId());
         
         // Disable buttons during execution to prevent double-clicks
         setButtonsEnabled(false);
      }
   }
   
   @Override
   protected void onAllowListClicked()
   {
      if ("run_file".equals(functionCallType_)) {
         // Handle run_file commands - use file-based allow list
         if (extractedFiles_ != null && !extractedFiles_.trim().isEmpty()) {
            enableAutoAcceptRunFileWithFiles(extractedFiles_);
         } else {
            enableAutoAcceptRunFile();
         }
      } else {
         // Handle run_console_cmd commands - use function-based allow list
         if (extractedFunctions_ != null && !extractedFunctions_.trim().isEmpty()) {
            enableAutoAcceptConsoleWithFunctions(extractedFunctions_);
         } else {
            enableAutoAcceptConsole();
         }
      }
      
      // Then execute the current command automatically
      if (handler_ != null) {
         String command = editor_.getCode();
         handler_.onRun(getMessageId(), command);
         setButtonsEnabled(false);
      }
   }

   /**
    * Enable auto-accept console and add extracted functions to allow list
    */
   private void enableAutoAcceptConsoleWithFunctions(String functions) {
      AiServerOperations server = RStudioGinjector.INSTANCE.getServer();
      
      // First, enable auto-accept console
      server.setAutoAcceptConsole(true, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Now add functions to the allow list
            addFunctionsToAllowList(functions);
         }
         
         @Override
         public void onError(ServerError error) {
            // Failed to enable auto-accept console
         }
      });
   }
   
   /**
    * Add functions to the console allow list
    */
   private void addFunctionsToAllowList(String functions) {
      if (functions == null || functions.trim().isEmpty()) {
         return;
      }
      
      AiServerOperations server = RStudioGinjector.INSTANCE.getServer();
      
      // Get current allow list
      server.getAutomationList("auto_accept_console_allow_list", new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject response) {
            // Convert response to array and add new functions
            JsArrayString currentList = response.cast();
            JsArrayString newList = JavaScriptObject.createArray().cast();
            
            // Add existing items
            for (int i = 0; i < currentList.length(); i++) {
               newList.push(currentList.get(i));
            }
            
            // Add new functions (split by comma)
            String[] functionArray = functions.split(",\\s*");
            for (String func : functionArray) {
               if (!func.trim().isEmpty()) {
                  // Check if function is already in the list
                  boolean exists = false;
                  for (int i = 0; i < currentList.length(); i++) {
                     if (currentList.get(i).equals(func.trim())) {
                        exists = true;
                        break;
                     }
                  }
                  if (!exists) {
                     newList.push(func.trim());
                  }
               }
            }
            
            // Save updated list
            server.setAutomationList("auto_accept_console_allow_list", newList, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
                  // Functions added to allow list successfully
               }
               
               @Override
               public void onError(ServerError error) {
                  // Failed to add functions to allow list
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            // Failed to get current allow list
         }
      });
   }

   /**
    * Enable auto-accept console without specific functions
    */
   private void enableAutoAcceptConsole() {
      AiServerOperations server = RStudioGinjector.INSTANCE.getServer();
      server.setAutoAcceptConsole(true, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Auto-accept console enabled successfully
         }
         
         @Override
         public void onError(ServerError error) {
            // Failed to enable auto-accept console
         }
      });
   }
   
   /**
    * Enable auto-accept run_file and add extracted files to allow list
    */
   private void enableAutoAcceptRunFileWithFiles(String files) {
      AiServerOperations server = RStudioGinjector.INSTANCE.getServer();
      
      // First, enable auto-run files
      server.setAutoRunFiles(true, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Hide buttons
            hideVerticalStack();
            
            // Then add files to allow list
            addFilesToAllowList(files);
         }
         
         @Override
         public void onError(ServerError error) {
            // Failed to enable auto-accept run_file
         }
      });
   }
   
   /**
    * Enable auto-accept run_file without adding to allow list
    */
   private void enableAutoAcceptRunFile() {
      AiServerOperations server = RStudioGinjector.INSTANCE.getServer();
      
      server.setAutoRunFiles(true, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Hide buttons
            hideVerticalStack();
         }
         
         @Override
         public void onError(ServerError error) {
            // Failed to enable auto-accept run_file
         }
      });
   }
   
   /**
    * Add files to the run_file allow list
    */
   private void addFilesToAllowList(String files) {
      if (files == null || files.trim().isEmpty()) {
         return;
      }
      
      AiServerOperations server = RStudioGinjector.INSTANCE.getServer();
      
      // Get current allow list
      server.getAutomationList("auto_run_files_allow_list", new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject response) {
            // Convert response to array and add new files
            JsArrayString currentList = response.cast();
            JsArrayString newList = JavaScriptObject.createArray().cast();
            
            // Add existing items
            for (int i = 0; i < currentList.length(); i++) {
               newList.push(currentList.get(i));
            }
            
            // Add new files (split by comma)
            String[] fileArray = files.split(",\\s*");
            for (String file : fileArray) {
               if (!file.trim().isEmpty()) {
                  // Check if file is already in the list
                  boolean exists = false;
                  for (int i = 0; i < currentList.length(); i++) {
                     if (currentList.get(i).equals(file.trim())) {
                        exists = true;
                        break;
                     }
                  }
                  if (!exists) {
                     newList.push(file.trim());
                  }
               }
            }
            
            // Save updated list
            server.setAutomationList("auto_run_files_allow_list", newList, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void response) {
                  // Files added to allow list successfully
               }
               
               @Override
               public void onError(ServerError error) {
                  // Failed to add files to allow list
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            // Failed to get current allow list
         }
      });
   }
   
   public String getCommand()
   {
      return editor_.getCode();
   }
   
   public void setCommand(String command)
   {
      editor_.setCode(command, false);
   }
   
   /**
    * Programmatically trigger Run (used for auto-accept flows)
    */
   public void autoRun() {
      onRunClicked();
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
   
   private final String explanation_;
   private final ConsoleCommandHandler handler_;
   private final boolean isEditable_;
   private AceEditor editor_;
   private VerticalPanel verticalButtonStack_;
} 