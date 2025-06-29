/*
 * AiEditFileWidget.java
 *
 * Copyright (C) 2025 by William Nickols
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 */

package org.rstudio.studio.client.workbench.views.ai.widgets;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.core.client.JsArrayInteger;
import org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.AceEditorNative;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Range;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Position;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.widget.FontSizer;

/**
 * Widget for displaying edit_file function calls with an Ace editor
 * Similar to AiConsoleWidget but specialized for file editing
 */
public class AiEditFileWidget extends Composite
{
   public interface EditFileCommandHandler
   {
      void onAccept(String messageId, String editedContent);
      void onCancel(String messageId);
   }
   
   public AiEditFileWidget(String messageId, 
                          String filename,
                          String content, 
                          String explanation,
                          String requestId,
                          boolean isEditable,
                          EditFileCommandHandler handler)
   {
      this(messageId, filename, content, explanation, requestId, isEditable, handler, false);
   }
   
   public AiEditFileWidget(String messageId, 
                          String filename,
                          String content, 
                          String explanation,
                          String requestId,
                          boolean isEditable,
                          EditFileCommandHandler handler,
                          boolean isCancelled)
   {
      messageId_ = messageId;
      filename_ = filename;
      explanation_ = explanation;
      requestId_ = requestId;
      handler_ = handler;
      isEditable_ = isEditable;
      isCancelled_ = isCancelled;
      diffMarkers_ = JsArrayInteger.createArray().cast();
      
      initWidget(createWidget(content, filename));
      addStyleName("aiEditFileWidget");
      
      // Only apply diff highlighting if not cancelled
      if (!isCancelled_) {
         // Apply diff highlighting after widget is fully rendered and ACE editor is ready
         com.google.gwt.core.client.Scheduler.get().scheduleDeferred(() -> {
            if (editor_ != null) {
               // Use a more robust approach - wait for ACE editor to be fully initialized
               waitForAceEditorAndApplyDiffHighlighting();
            }
         });
      }
   }
   
   private Widget createWidget(String content, String filename)
   {
      VerticalPanel container = new VerticalPanel();
      container.setWidth("100%");
      
      // Create simple header panel
      headerPanel_ = new HorizontalPanel();
      headerPanel_.setWidth("100%");
      headerPanel_.addStyleName("aiEditFileHeader");
      headerPanel_.getElement().getStyle().setBackgroundColor("#666");
      headerPanel_.getElement().getStyle().setColor("white");
      headerPanel_.getElement().getStyle().setFontSize(12, Unit.PX);
      headerPanel_.getElement().getStyle().setFontWeight(com.google.gwt.dom.client.Style.FontWeight.BOLD);
      headerPanel_.getElement().getStyle().setPadding(3, Unit.PX);
      headerPanel_.getElement().getStyle().setProperty("borderRadius", "4px 4px 0 0");
      headerPanel_.getElement().getStyle().setMargin(0, Unit.PX);
      headerPanel_.getElement().getStyle().setProperty("boxSizing", "border-box");
      headerPanel_.getElement().getStyle().setBorderWidth(1, Unit.PX);
      headerPanel_.getElement().getStyle().setBorderStyle(com.google.gwt.dom.client.Style.BorderStyle.SOLID);
      headerPanel_.getElement().getStyle().setBorderColor("#ccc");
      headerPanel_.getElement().getStyle().setProperty("borderBottom", "none");
      headerPanel_.getElement().getStyle().setProperty("position", "relative");
      
      // Create filename label - separate filename from diff-stats for proper positioning
      filenameLabel_ = new Label();
      Label diffStatsLabel = null;
      
      if (filename != null && !filename.isEmpty()) {
         // If filename contains HTML (diff stats), parse them separately
         if (filename.contains("<span")) {
            // Extract the clean filename (before the diff-stats span)
            String cleanFilename = filename.substring(0, filename.indexOf(" <span"));
            filenameLabel_.setText(cleanFilename);
            
            // Extract and create separate diff-stats element
            String diffStatsHtml = filename.substring(filename.indexOf("<span"));
            diffStatsLabel = new Label();
            diffStatsLabel.getElement().setInnerHTML(diffStatsHtml);
            diffStatsLabel.addStyleName("diff-stats-container");
         } else {
            filenameLabel_.setText(filename);
         }
      }
      
      // Add filename first (left side)
      headerPanel_.add(filenameLabel_);
      
      // Add diff-stats second (will float right)
      if (diffStatsLabel != null) {
         headerPanel_.add(diffStatsLabel);
      }
      
      container.add(headerPanel_);
      
      // Create edit file wrapper - minimal styling to let ACE handle themes
      SimplePanel editFileWrapper = new SimplePanel();
      editFileWrapper.setWidth("100%");
      editFileWrapper.getElement().getStyle().setBorderWidth(1, Unit.PX);
      editFileWrapper.getElement().getStyle().setBorderStyle(com.google.gwt.dom.client.Style.BorderStyle.SOLID);
      editFileWrapper.getElement().getStyle().setBorderColor("#ccc");
      editFileWrapper.getElement().getStyle().setProperty("borderRadius", "0 0 4px 4px");
      
      // Create the ace editor for file content
      editor_ = createFileEditor(filename);
      editor_.getWidget().setWidth("100%");
      
      // Set initial content
      if (content != null && !content.trim().isEmpty())
      {
         editor_.setCode(content, false);
         // Refresh syntax highlighting after setting initial content
         editor_.retokenizeDocument();
      }
      
      // Set editor read-only state without custom styling - let ACE handle appearance
      editor_.setReadOnly(!isEditable_);
      
      editFileWrapper.setWidget(editor_.getWidget());
      container.add(editFileWrapper);
      
      // Only create buttons if the edit is not cancelled
      if (!isCancelled_) {
         // FIXED: Create button container with EXACT same positioning as console/terminal widgets
         SimplePanel buttonContainer = new SimplePanel();
         buttonContainer.addStyleName("aiEditFileButtons");
         buttonContainer.setWidth("100%");
         buttonContainer.getElement().getStyle().setProperty("position", "relative");
         buttonContainer.getElement().getStyle().setHeight(0, Unit.PX); // No height so it doesn't take space
         buttonContainer.getElement().getStyle().setProperty("zIndex", "10");
         
         // FIXED: Create button wrapper with EXACT same positioning as console/terminal
         HorizontalPanel buttonWrapper = new HorizontalPanel();
         buttonWrapper.setSpacing(0);
         buttonWrapper.getElement().getStyle().setProperty("position", "absolute");
         buttonWrapper.getElement().getStyle().setProperty("top", "-12px"); // FIXED: Changed from -9px to -12px to match console/terminal alignment
         buttonWrapper.getElement().getStyle().setProperty("right", "8px"); // 8px from right edge
         buttonWrapper.getElement().getStyle().setProperty("zIndex", "999"); // FIXED: Higher z-index for clickability
         
         // Create buttons
         acceptButton_ = createNativeButton("Accept", "aiEditFileAcceptButton");
         
         cancelButton_ = createNativeButton("Cancel", "aiEditFileCancelButton");
         
         buttonWrapper.add(acceptButton_);
         buttonWrapper.add(cancelButton_);
         
         buttonContainer.setWidget(buttonWrapper);
         container.add(buttonContainer);
      }
      
      return container;
   }
   
   private AceEditor createFileEditor(String filename)
   {
      AceEditor editor = new AceEditor();
      
      editor.getWidget().getElement().setId("ai-edit-file-editor-" + messageId_);
      
      // Configure file type for syntax highlighting dynamically based on extension
      try {
         // Set file type based on extension only if we can determine it
         if (filename != null) {
            // Extract clean filename by removing HTML diff stats if present
            // Use a more robust regex to remove all HTML tags including nested ones
            String cleanFilename = filename.replaceAll("<[^>]*>", "").trim();
            // Also remove any remaining diff stats pattern like "+11 -0"
            cleanFilename = cleanFilename.replaceAll("\\s+[+\\-]\\d+\\s+[+\\-]\\d+\\s*$", "").trim();
            String lowerFilename = cleanFilename.toLowerCase();
            
            if (lowerFilename.endsWith(".r")) {
               editor.setFileType(FileTypeRegistry.R, true);
            } else if (lowerFilename.endsWith(".py")) {
               editor.setFileType(FileTypeRegistry.PYTHON, true);
            } else if (lowerFilename.endsWith(".js")) {
               editor.setFileType(FileTypeRegistry.JS, true);
            } else if (lowerFilename.endsWith(".html")) {
               editor.setFileType(FileTypeRegistry.HTML, true);
            } else if (lowerFilename.endsWith(".css")) {
               editor.setFileType(FileTypeRegistry.CSS, true);
            } else if (lowerFilename.endsWith(".sql")) {
               editor.setFileType(FileTypeRegistry.SQL, true);
            } else if (lowerFilename.endsWith(".sh") || lowerFilename.endsWith(".bash")) {
               editor.setFileType(FileTypeRegistry.SH, true);
            } else if (lowerFilename.endsWith(".json")) {
               editor.setFileType(FileTypeRegistry.JSON, true);
            }
         } else {
            Debug.log("DEBUG: No filename provided");
         }
         // No else clause - let ACE use default behavior when no filename provided
      } catch (Exception e) {
         // If file type setting fails, continue without syntax highlighting
         Debug.log("DEBUG: Failed to set file type for filename: " + filename + ", error: " + e.getMessage());
      }
      
      // Standard editor configuration - let ACE handle themes and colors
      editor.setShowLineNumbers(true);
      editor.setShowPrintMargin(false);
      editor.setUseWrapMode(true);
      editor.setReadOnly(false);
      editor.autoHeight();
      
      // Essential: Apply font sizing for proper theme integration (like run_console_cmd does)
      FontSizer.applyNormalFontSize(editor.getWidget());
      
      return editor;
   }
   

   
   // Create native HTML button with native DOM events (copied from console/terminal widgets)
   private Button createNativeButton(String text, String styleClass)
   {
      Button button = new Button(text);
      button.addStyleName(styleClass);
      
      // Apply styling based on button type
      if ("aiEditFileAcceptButton".equals(styleClass))
      {
         // Light green styling for accept button (like Run button)
         button.getElement().getStyle().setBackgroundColor("#e6ffe6");
         button.getElement().getStyle().setColor("#006400");
         button.getElement().getStyle().setBorderColor("#006400");
      }
      else if ("aiEditFileCancelButton".equals(styleClass))
      {
         // Light red styling for cancel button  
         button.getElement().getStyle().setBackgroundColor("#ffe6e6");
         button.getElement().getStyle().setColor("#8b0000");
         button.getElement().getStyle().setBorderColor("#8b0000");
      }
      
      // FIXED: Common styling with proper z-index and positioning
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
      // FIXED: Add proper z-index and positioning for clickability
      button.getElement().getStyle().setProperty("zIndex", "1000");
      button.getElement().getStyle().setProperty("position", "relative");
      
      // Add native DOM click event listener
      addNativeClickHandler(button.getElement(), text);
      
      return button;
   }
   
   // Add native DOM event handler using JSNI (copied from console/terminal widgets)
   private native void addNativeClickHandler(com.google.gwt.dom.client.Element element, String buttonText) /*-{
      var self = this;
      var messageId = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::messageId_;
      
      // FIXED: Use both capture and bubble phases to ensure events are caught
      var clickHandler = function(event) {
         if (buttonText === 'Accept') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::onAcceptClicked()();
         } else if (buttonText === 'Cancel') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::onCancelClicked()();
         }
         
         event.preventDefault();
         event.stopPropagation();
      };
      
      // FIXED: Add event listeners for both capture and bubble phases
      element.addEventListener('click', clickHandler, true);  // Capture phase
      element.addEventListener('click', clickHandler, false); // Bubble phase
      
      // FIXED: Also add mousedown event as backup
      element.addEventListener('mousedown', clickHandler, true);
   }-*/;
   
   private native Element querySelector(Element element, String selector) /*-{
      return element.querySelector(selector);
   }-*/;
   
   /**
    * Create a label with colored diff stats (+X in green, -Y in red)
    */

   

   
   private void onAcceptClicked()
   {
      if (handler_ != null)
      {
         String editedContent = getContent();
         handler_.onAccept(messageId_, editedContent);
         
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
   
   private void setButtonsEnabled(boolean enabled)
   {
      acceptButton_.setEnabled(enabled);
      cancelButton_.setEnabled(enabled);
   }
   
   /**
    * Permanently hides the buttons (called when buttons are clicked)
    */
   public void hideButtons() {
      // For cancelled edits, buttons don't exist, so nothing to hide
      if (isCancelled_) {
         return;
      }
      
      if (acceptButton_ != null) {
         // Remove focus before hiding to avoid aria-hidden accessibility issues
         acceptButton_.getElement().blur();
         acceptButton_.setVisible(false);
      }
      if (cancelButton_ != null) {
         // Remove focus before hiding to avoid aria-hidden accessibility issues
         cancelButton_.getElement().blur();
         cancelButton_.setVisible(false);
      }
   }
   
   public String getContent()
   {
      if (editor_ != null)
      {
         return editor_.getCode();
      }
      return "";
   }
   
   /**
    * Set the content of the editor
    * @param content The content to set
    */
   public void setContent(String content)
   {
      if (editor_ != null && content != null && !content.trim().isEmpty())
      {
         editor_.setCode(content, false);
         // Refresh syntax highlighting after setting content
         editor_.retokenizeDocument();
         
         // Apply diff highlighting after setting content
         com.google.gwt.core.client.Scheduler.get().scheduleDeferred(() -> {
            applyDiffHighlighting();
         });
      }
   }
   
   /**
    * Append streaming content to the editor
    * @param delta The delta content to append
    */
   public void appendStreamingContent(String delta)
   {
      if (editor_ != null && delta != null && !delta.isEmpty())
      {
         String currentContent = editor_.getCode();
         String newContent = currentContent + delta;
         editor_.setCode(newContent, false);
         // Refresh syntax highlighting after appending content
         editor_.retokenizeDocument();
      }
   }
   
   /**
    * Update the filename header with diff statistics
    * @param newFilename The new filename potentially containing diff stats HTML
    */
   public void updateFilenameHeader(String newFilename)
   {
      if (newFilename == null || newFilename.trim().isEmpty()) {
         return;
      }
      
      if (filenameLabel_ == null || headerPanel_ == null) {
         Debug.log("DIFF_STATS_DEBUG: Filename label or header panel is null");
         return;
      }
      
      // Do the same parsing and splitting as widget creation code (lines 110-128)
      if (newFilename.contains("<span")) {
         // Extract the clean filename (before the diff-stats span) 
         String cleanFilename = newFilename.substring(0, newFilename.indexOf(" <span"));
         filenameLabel_.setText(cleanFilename);
         
         // Extract and create separate diff-stats element
         String diffStatsHtml = newFilename.substring(newFilename.indexOf("<span"));
         Label diffStatsLabel = new Label();
         diffStatsLabel.getElement().setInnerHTML(diffStatsHtml);
         diffStatsLabel.addStyleName("diff-stats-container");
         
         // Add diff-stats to header panel (will float right)
         headerPanel_.add(diffStatsLabel);
      } else {
         filenameLabel_.setText(newFilename);
      }
   }
   
   public String getMessageId()
   {
      return messageId_;
   }
   
   public String getFilename()
   {
      return filename_;
   }
   
   public String getRequestId()
   {
      return requestId_;
   }
   
   private final String messageId_;
   private final String filename_;
   private final String explanation_;
   private final String requestId_;
   private final EditFileCommandHandler handler_;
   private final boolean isEditable_;
   private final boolean isCancelled_;

   private AceEditor editor_;
   private Button acceptButton_;
   private Button cancelButton_;
   private HorizontalPanel headerPanel_;
   private Label filenameLabel_;
   private JsArrayInteger diffMarkers_; // Store diff marker IDs for cleanup

   /**
    * Get pre-computed diff data from R backend
    */
   private void getDiffDataFromBackend()
   {
      // Make RPC call to get diff results (already computed on R side)
      org.rstudio.studio.client.server.ServerRequestCallback<com.google.gwt.core.client.JavaScriptObject> callback = 
         new org.rstudio.studio.client.server.ServerRequestCallback<com.google.gwt.core.client.JavaScriptObject>() {
            @Override
            public void onResponseReceived(com.google.gwt.core.client.JavaScriptObject diffResult) {
               // Apply diff highlighting using the pre-computed diff data
               if (diffResult != null) {
                  applyDiffHighlightingFromRData(diffResult);
                  
                  // Extract and display diff stats from the response (same as normal conversation loading)
                  updateFilenameWithDiffStatsFromResponse(diffResult);
               }
            }
            
            @Override
            public void onError(org.rstudio.studio.client.server.ServerError error) {
               Debug.log("DEBUG: Failed to get diff data: " + error.getMessage());
               // Continue without diff highlighting
            }
         };
      
      // Call R function to get pre-computed diff data for this message ID
      org.rstudio.studio.client.workbench.views.ai.AiPane aiPane = 
         org.rstudio.studio.client.workbench.views.ai.AiPane.getCurrentInstance();
      if (aiPane != null) {
         aiPane.getAiServerOperations().getDiffDataForEditFile(messageId_, callback);
      }
   }
   
   /**
    * Apply diff highlighting using pre-computed diff data from R
    */
   private void applyDiffHighlightingFromRData(com.google.gwt.core.client.JavaScriptObject diffResult)
   {
      if (diffResult == null) {
         return;
      }
      
      // Extract diff array from result
      com.google.gwt.core.client.JsArray<com.google.gwt.core.client.JavaScriptObject> diffArray = getDiffArray(diffResult);
      
      if (diffArray == null) {
         return;
      }
      
   
      
      // Clear any existing highlighting
      clearDiffHighlighting();
      
      // Build unified diff content (plain text, no HTML)
      StringBuilder unifiedContent = new StringBuilder();
      
         for (int i = 0; i < diffArray.length(); i++) {
            com.google.gwt.core.client.JavaScriptObject diffLine = diffArray.get(i);
         
         String lineType = getLineType(diffLine);
         String lineContent = getLineContent(diffLine);
         
         // Clean the line type string to handle any whitespace
            if (lineType != null) {
               lineType = lineType.trim();
            }
            
         // Add plain content to unified diff - only add newline if not the last line
         unifiedContent.append(lineContent != null ? lineContent : "");
         if (i < diffArray.length() - 1) {
            unifiedContent.append("\n");
      }
      }
      
      // Set the unified diff content in the editor (plain text)
      if (editor_ != null) {
         editor_.setCode(unifiedContent.toString(), false);
         
         // Apply annotations for diff highlighting
         applyDiffAnnotations(diffArray);
         
         // Set up custom line numbers for diff display
         setupDiffLineNumbers(diffArray);
      }
   }
   
   /**
    * Apply diff highlighting using ACE Editor annotations (simplified - just use markers for visual highlighting)
    */
   private native void applyDiffAnnotations(com.google.gwt.core.client.JsArray<com.google.gwt.core.client.JavaScriptObject> diffArray) /*-{
      var self = this;
      var editor = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::editor_;
      
      if (!editor) {
         return;
      }
      
      var aceInstance = null;
      var session = null;
      
      try {
         // RStudio pattern: editor.getWidget().getEditor().getSession()
         var widget = editor.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor::getWidget()();
         
         if (widget) {
            aceInstance = widget.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditorWidget::getEditor()();
            
            if (aceInstance) {
               session = aceInstance.@org.rstudio.studio.client.workbench.views.source.editors.text.ace.AceEditorNative::getSession()();
         }
      }
      } catch (e) {
         return;
      }
      
      if (!aceInstance || !session) {
         return;
      }
      
      // Get the marker tracking array from the Java field
      var markerIds = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::diffMarkers_;
      
      // Debug: Show each line's actual gutter content and what marker it should get
      for (var lineNum = 0; lineNum < session.getLength(); lineNum++) {
         var lineContent = session.getLine(lineNum) || "empty";
         
         // Get the actual gutter text by calling the custom renderer directly
         var actualGutterText = "no gutter";
         if (session.gutterRenderer && session.gutterRenderer.getText) {
            try {
               actualGutterText = session.gutterRenderer.getText(session, lineNum);
            } catch (e) {
               actualGutterText = "gutter error: " + e.message;
            }
         }
      }
      
      // Apply diff highlighting: go through each line and apply color based on diff data
      for (var lineNum = 0; lineNum < session.getLength(); lineNum++) {
         try {
            var Range = $wnd.ace.require("ace/range").Range;
            var lineContent = session.getLine(lineNum);
            var lineLength = lineContent ? lineContent.length : 0;
            var range = new Range(lineNum, 0, lineNum, Math.max(lineLength, 1));
            
            // Check diff data to determine color: green (added), red (deleted), or white (unchanged)
            var cssClassName = "ace_test_line_2"; // Default to white for unchanged
            if (lineNum < diffArray.length) {
               var diffLine = diffArray[lineNum];
               var lineType = diffLine.type;
               
               // Extract from array if needed
               if (Array.isArray(lineType) && lineType.length > 0) {
                  lineType = lineType[0];
               }
               
               if (lineType && lineType.trim) {
                  lineType = lineType.trim();
               }
               
               // Set CSS class based on line type
               if (lineType === "added") {
                  cssClassName = "ace_test_line_0"; // Green
               } else if (lineType === "deleted") {
                  cssClassName = "ace_test_line_1"; // Red
               }
               // unchanged lines keep the default white (ace_test_line_2)
            }
            
            // Always apply a marker since every line gets a color
            var markerId = session.addMarker(range, cssClassName, "fullLine", false);
            markerIds.push(markerId);
            
         } catch (e) {
            console.error("ERROR adding marker for line " + lineNum + ":", e);
         }
      }
   }-*/;
   
   /**
    * Extract diff array from R result
    */
   private native com.google.gwt.core.client.JsArray<com.google.gwt.core.client.JavaScriptObject> getDiffArray(com.google.gwt.core.client.JavaScriptObject result) /*-{
      return result.diff || null;
   }-*/;
   
   /**
    * Get line type from diff line object
    */
   private native String getLineType(com.google.gwt.core.client.JavaScriptObject diffLine) /*-{
      var type = diffLine.type;
      
      // Extract from array if needed
      if (Array.isArray(type) && type.length > 0) {
         type = type[0];
      }
      
      if (type == null || type == undefined) {
         return "unchanged";
      }
      var typeStr = String(type);
      return typeStr.trim ? typeStr.trim() : typeStr;
   }-*/;
   
   /**
    * Get line content from diff line object
    */
   private native String getLineContent(com.google.gwt.core.client.JavaScriptObject diffLine) /*-{
      var content = diffLine.content;
      
      // Extract from array if needed
      if (Array.isArray(content) && content.length > 0) {
         content = content[0];
      }
      
      return (content != null && content != undefined) ? String(content) : "";
   }-*/;
   
   /**
    * Get old line number from diff line object
    */
   private native int getOldLineNumber(com.google.gwt.core.client.JavaScriptObject diffLine) /*-{
      return diffLine.old_line || -1;
   }-*/;
   
   /**
    * Get new line number from diff line object
    */
   private native int getNewLineNumber(com.google.gwt.core.client.JavaScriptObject diffLine) /*-{
      return diffLine.new_line || -1;
   }-*/;

   /**
    * Apply diff highlighting to show added and deleted lines
    */
   public void applyDiffHighlighting()
   {      
      // This method now just calls the backend to get pre-computed diff data
      getDiffDataFromBackend();
   }
   
   /**
    * Clear all diff highlighting markers
    */
   public void clearDiffHighlighting()
   {
      clearAllMarkers();
   }
   
   /**
    * Clear all ACE editor markers
    */
   private native void clearAllMarkers() /*-{
      var self = this;
      var editor = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::editor_;
      
      if (!editor || !editor.getWidget) return;
      
      var aceInstance = editor.getWidget().getEditor();
      if (!aceInstance || !aceInstance.session) return;
      
      var markerIds = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::diffMarkers_;
      
      if (markerIds && markerIds.length > 0) {
         
         for (var i = 0; i < markerIds.length; i++) {
            try {
               aceInstance.session.removeMarker(markerIds[i]);
            } catch (e) {
               console.error("DEBUG: Error removing marker " + markerIds[i] + ":", e);
            }
         }
         
         // Clear the marker tracking array
         markerIds.length = 0;
      }
   }-*/;
   
   /**
    * Customize line numbers for diff display - show original vs new line numbers
    */
   private void setupDiffLineNumbers(com.google.gwt.core.client.JsArray<com.google.gwt.core.client.JavaScriptObject> diffArray)
   {
      if (editor_ == null || diffArray == null) {
         return;
      }
      
      // Create custom line number renderer for diff
      setupCustomLineNumbers(diffArray);
   }
   
   /**
    * Set up custom line numbers using JSNI to access ACE internals
    */
   private native void setupCustomLineNumbers(com.google.gwt.core.client.JsArray<com.google.gwt.core.client.JavaScriptObject> diffArray) /*-{
      var editor = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::editor_;
      if (!editor || !diffArray) {
         return;
      }
      
      var aceEditor = editor.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor::getWidget()();
      if (!aceEditor) {
         return;
      }
      
      var aceInstance = aceEditor.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditorWidget::getEditor()();
      if (!aceInstance) {
         return;
      }
      
      var session = aceInstance.getSession();
      if (!session) {
         console.log("DEBUG:  - session is null");
         return;
      }
      
      
      // Store diff line info for custom gutter
      var diffLineInfo = [];
      var maxOldLine = 0;
      var maxNewLine = 0;
      
      for (var i = 0; i < diffArray.length; i++) {
         var diffLine = diffArray[i];
         
         var lineType = diffLine.type || "unchanged";
         var oldLine = diffLine.old_line;
         var newLine = diffLine.new_line;
         
         
         // Extract values from arrays if needed
         if (Array.isArray(lineType) && lineType.length > 0) {
            lineType = lineType[0];
         }
         if (Array.isArray(oldLine) && oldLine.length > 0) {
            oldLine = oldLine[0];
         }
         if (Array.isArray(newLine) && newLine.length > 0) {
            newLine = newLine[0];
         }
         
         
         // Handle null/undefined line numbers
         oldLine = (oldLine != null && oldLine > 0) ? oldLine : -1;
         newLine = (newLine != null && newLine > 0) ? newLine : -1;
         

         
         // Track max line numbers for width calculation
         if (oldLine > maxOldLine) maxOldLine = oldLine;
         if (newLine > maxNewLine) maxNewLine = newLine;
         
         diffLineInfo[i] = {
            type: lineType,
            oldLine: oldLine,
            newLine: newLine
         };
         
      }
      
      // Calculate width needed for both line number columns
      var oldLineWidth = Math.max(String(maxOldLine).length, 1); // At least 1 char
      var newLineWidth = Math.max(String(maxNewLine).length, 1); // At least 1 char
      
      // Custom gutter renderer for dual line numbers
      session.gutterRenderer = {
         getWidth: function(session, lastLineNumber, config) {
            // Width for both columns plus separator and extra space, accounting for ACE's built-in padding
            var totalWidth = (oldLineWidth + newLineWidth + 2) * config.characterWidth + 2 * config.padding;
            return totalWidth;
         },
         
         getText: function(session, row) {
            var info = diffLineInfo[row];
            if (!info) {
               return "";
            }
            
            // Extract type from array if needed
            var lineType = info.type;
            if (Array.isArray(lineType) && lineType.length > 0) {
               lineType = lineType[0];
            }
            
            // Extract line numbers from arrays if needed
            var oldLineNum = info.oldLine;
            var newLineNum = info.newLine;
            if (Array.isArray(oldLineNum) && oldLineNum.length > 0) {
               oldLineNum = oldLineNum[0];
            }
            if (Array.isArray(newLineNum) && newLineNum.length > 0) {
               newLineNum = newLineNum[0];
            }
            
            // Format: "oldLine | newLine" with proper spacing
            var oldText = "";
            var newText = "";
            
            switch (lineType) {
               case "deleted":
                  oldText = (oldLineNum > 0) ? String(oldLineNum) : "";
                  newText = ""; // Blank for deleted lines
                  break;
               case "added":
                  oldText = ""; // Blank for added lines  
                  newText = (newLineNum > 0) ? String(newLineNum) : "";
                  break;
               case "unchanged":
                  oldText = (oldLineNum > 0) ? String(oldLineNum) : "";
                  newText = (newLineNum > 0) ? String(newLineNum) : "";
                  break;
            }
            
            // Ensure we have strings and pad to consistent width (right-align)
            oldText = String(oldText);
            newText = String(newText);
            
            // Right-align numbers in their respective columns
            while (oldText.length < oldLineWidth) {
               oldText = " " + oldText;
            }
            
            while (newText.length < newLineWidth) {
               newText = " " + newText;
            }
            
            // Combine with separator - add space after separator to balance the left padding
            var result = oldText + "|" + newText + " ";
            return result;
         }
      };
      
      // Force gutter update
      aceInstance.renderer.updateLines(0, session.getLength());
      aceInstance.renderer.updateFull();
   }-*/;
   
   /**
    * Refresh diff highlighting (useful when content changes)
    */
   public void refreshDiffHighlighting()
   {
      // Schedule diff highlighting refresh to happen after DOM updates
      com.google.gwt.core.client.Scheduler.get().scheduleDeferred(() -> {
         applyDiffHighlighting();
      });
   }

   /**
    * Use a more robust approach to wait for the ACE editor to be fully initialized before applying diff highlighting
    */
   private void waitForAceEditorAndApplyDiffHighlighting()
   {
      // Use a timer-based approach to wait for ACE editor to be fully ready
      com.google.gwt.user.client.Timer timer = new com.google.gwt.user.client.Timer() {
         private int attempts = 0;
         private final int maxAttempts = 10;
         
         @Override
         public void run() {
            attempts++;
            
            if (editor_ != null && isAceEditorReady()) {
               applyDiffHighlighting();
            } else if (attempts < maxAttempts) {
               schedule(50); // Try again in 50ms
            } else {
               Debug.log("DEBUG: ACE editor still not ready after " + maxAttempts + " attempts, giving up on diff highlighting");
            }
         }
      };
      timer.schedule(50); // Start with 50ms delay
   }
   
   /**
    * Check if the ACE editor is fully initialized and ready for markers
    */
   private boolean isAceEditorReady()
   {
      if (editor_ == null) {
         return false;
      }
      
      try {
         // Check if the ACE editor has a valid session and renderer
         return editor_.getSession() != null && 
                editor_.getWidget() != null && 
                editor_.getWidget().getElement() != null &&
                editor_.getWidget().getElement().getOffsetHeight() > 0;
      } catch (Exception e) {
         Debug.log("DEBUG: Exception checking ACE editor readiness: " + e.getMessage());
         return false;
      }
   }
   
   /**
    * Public method to manually trigger diff highlighting for testing
    */
   public void triggerDiffHighlighting()
   {
      applyDiffHighlighting();
   }

   /**
    * Add simple background markers for diff highlighting
    * Maps diff array indices to actual editor line numbers in unified diff format
    */
   private native void addBackgroundMarkersWithSession(com.google.gwt.core.client.JsArray<com.google.gwt.core.client.JavaScriptObject> diffArray, 
                                                     com.google.gwt.core.client.JavaScriptObject aceInstance,
                                                     com.google.gwt.core.client.JavaScriptObject session) /*-{
      var self = this;
      
      // Clear any existing markers first
      self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::clearAllMarkers()();
      
      // Initialize marker tracking array if not exists
      if (!self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::diffMarkers_) {
         self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::diffMarkers_ = [];
      }
      
      var markerIds = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget::diffMarkers_;
      var totalEditorLines = session.getLength();
      
      for (var lineNum = 0; lineNum < totalEditorLines; lineNum++) {
         var lineContent = session.getLine(lineNum) || "empty";
         
         // Get the actual gutter text by calling the custom renderer directly
         var actualGutterText = "no gutter";
         if (session.gutterRenderer && session.gutterRenderer.getText) {
            try {
               actualGutterText = session.gutterRenderer.getText(session, lineNum);
            } catch (e) {
               actualGutterText = "gutter error: " + e.message;
            }
         }
      }
      
      // Apply diff highlighting: go through each line and apply color based on diff data
      for (var lineNum = 0; lineNum < totalEditorLines; lineNum++) {
         try {
            var Range = $wnd.ace.require("ace/range").Range;
            var lineContent = session.getLine(lineNum);
            var lineLength = lineContent ? lineContent.length : 0;
            var range = new Range(lineNum, 0, lineNum, Math.max(lineLength, 1));
            
            // Check diff data to determine color: green (added), red (deleted), or white (unchanged)
            var cssClassName = "ace_test_line_2"; // Default to white for unchanged
            if (lineNum < diffArray.length) {
               var diffLine = diffArray[lineNum];
               var lineType = diffLine.type;
               
               // Extract from array if needed
               if (Array.isArray(lineType) && lineType.length > 0) {
                  lineType = lineType[0];
               }
               
               if (lineType && lineType.trim) {
                  lineType = lineType.trim();
               }
               
               // Set CSS class based on line type
               if (lineType === "added") {
                  cssClassName = "ace_test_line_0"; // Green
               } else if (lineType === "deleted") {
                  cssClassName = "ace_test_line_1"; // Red
               }
               // unchanged lines keep the default white (ace_test_line_2)
            }
            
            // Always apply a marker since every line gets a color
            var markerId = session.addMarker(range, cssClassName, "fullLine", false);
            markerIds.push(markerId);
            
         } catch (e) {
            console.error("ERROR adding marker for line " + lineNum + ":", e);
         }
      }
   }-*/;
   
   /**
    * Extract filename with diff stats from the R response and update filename header
    * This uses the same approach as normal conversation loading
    */
   private void updateFilenameWithDiffStatsFromResponse(com.google.gwt.core.client.JavaScriptObject diffResult)
   {
      if (diffResult == null) {
         Debug.log("DEBUG: diffResult is null, cannot update diff stats");
         return;
      }
      
      // Extract the formatted filename with diff stats from the response
      String filenameWithStats = getFilenameWithStats(diffResult);
      
      if (filenameWithStats != null && !filenameWithStats.trim().isEmpty()) {
         // Update filename header using the same method as normal conversation loading
         updateFilenameHeader(filenameWithStats);
      } else {
         Debug.log("DEBUG: filenameWithStats is null or empty, not updating filename header");
      }
   }
   
   /**
    * Extract filename_with_stats from diff result (same format as conversation loading)
    */
   private native String getFilenameWithStats(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var filenameWithStats = diffResult.filename_with_stats;
      
      // Extract from array if needed
      if (Array.isArray(filenameWithStats) && filenameWithStats.length > 0) {
         filenameWithStats = filenameWithStats[0];
      }
      
      return filenameWithStats || null;
   }-*/;
   
   /**
    * Extract added count from diff result, handling arrays
    */
   private native int getAddedCount(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var added = diffResult.added;
      
      // Extract from array if needed
      if (Array.isArray(added) && added.length > 0) {
         added = added[0];
      }
      
      return (added != null && added != undefined) ? parseInt(added) : 0;
   }-*/;
   
   /**
    * Extract deleted count from diff result, handling arrays
    */
   private native int getDeletedCount(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var deleted = diffResult.deleted;
      
      // Extract from array if needed
      if (Array.isArray(deleted) && deleted.length > 0) {
         deleted = deleted[0];
      }
      
      return (deleted != null && deleted != undefined) ? parseInt(deleted) : 0;
   }-*/;
   
   /**
    * Extract is_start_edit flag from diff result, handling arrays
    */
   private native boolean getIsStartEdit(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var isStartEdit = diffResult.is_start_edit;
      
      // Extract from array if needed
      if (Array.isArray(isStartEdit) && isStartEdit.length > 0) {
         isStartEdit = isStartEdit[0];
      }
      
      return (isStartEdit === true || isStartEdit === "true");
   }-*/;
   
   /**
    * Extract is_end_edit flag from diff result, handling arrays
    */
   private native boolean getIsEndEdit(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var isEndEdit = diffResult.is_end_edit;
      
      // Extract from array if needed
      if (Array.isArray(isEndEdit) && isEndEdit.length > 0) {
         isEndEdit = isEndEdit[0];
      }
      
      return (isEndEdit === true || isEndEdit === "true");
   }-*/;
   
   /**
    * Extract is_insert_mode flag from diff result, handling arrays
    */
   private native boolean getIsInsertMode(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var isInsertMode = diffResult.is_insert_mode;
      
      // Extract from array if needed
      if (Array.isArray(isInsertMode) && isInsertMode.length > 0) {
         isInsertMode = isInsertMode[0];
      }
      
      return (isInsertMode === true || isInsertMode === "true");
   }-*/;
   
   /**
    * Extract is_line_range_mode flag from diff result, handling arrays
    */
   private native boolean getIsLineRangeMode(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var isLineRangeMode = diffResult.is_line_range_mode;
      
      // Extract from array if needed
      if (Array.isArray(isLineRangeMode) && isLineRangeMode.length > 0) {
         isLineRangeMode = isLineRangeMode[0];
      }
      
      return (isLineRangeMode === true || isLineRangeMode === "true");
   }-*/;
   
   /**
    * Extract start_line from diff result, handling arrays
    */
   private native Integer getStartLine(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var startLine = diffResult.start_line;
      
      // Extract from array if needed
      if (Array.isArray(startLine) && startLine.length > 0) {
         startLine = startLine[0];
      }
      
      return (startLine != null && startLine != undefined) ? @java.lang.Integer::valueOf(I)(parseInt(startLine)) : null;
   }-*/;
   
   /**
    * Extract end_line from diff result, handling arrays
    */
   private native Integer getEndLine(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var endLine = diffResult.end_line;
      
      // Extract from array if needed
      if (Array.isArray(endLine) && endLine.length > 0) {
         endLine = endLine[0];
      }
      
      return (endLine != null && endLine != undefined) ? @java.lang.Integer::valueOf(I)(parseInt(endLine)) : null;
   }-*/;
   
   /**
    * Extract insert_line from diff result, handling arrays
    */
   private native Integer getInsertLine(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var insertLine = diffResult.insert_line;
      
      // Extract from array if needed
      if (Array.isArray(insertLine) && insertLine.length > 0) {
         insertLine = insertLine[0];
      }
      
      return (insertLine != null && insertLine != undefined) ? @java.lang.Integer::valueOf(I)(parseInt(insertLine)) : null;
   }-*/;
   
   /**
    * Log the contents of the diff result for debugging
    */
   private void logDiffResultContents(com.google.gwt.core.client.JavaScriptObject diffResult) 
   {
      if (diffResult != null) {
         com.google.gwt.core.client.JsArray<com.google.gwt.core.client.JavaScriptObject> diffArray = getDiffArray(diffResult);
      }
   }
} 