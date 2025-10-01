/*
 * AiFileEditorWidgetBase.java
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

import com.google.gwt.user.client.ui.*;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.core.client.JsArrayInteger;
import org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.widget.FontSizer;

/**
 * Abstract base class for AI widgets that edit files using search_replace
 * Contains all common functionality: diff highlighting, editor management, content handling
 */
public abstract class AiFileEditorWidgetBase extends AiWidgetBase
{
   // Common constructor for file editing widgets
   protected AiFileEditorWidgetBase(String messageId, 
                                   String requestId,
                                   String functionCallType,
                                   String filename,
                                   String explanation,
                                   boolean isEditable,
                                   boolean isCancelled,
                                   boolean skipDiffHighlighting,
                                   com.google.gwt.core.client.JavaScriptObject diffData)
   {
      super(messageId, requestId, functionCallType);
      filename_ = filename;
      explanation_ = explanation;
      isEditable_ = isEditable;
      isCancelled_ = isCancelled;
      skipDiffHighlighting_ = skipDiffHighlighting;
      preComputedDiffData_ = diffData;
      diffMarkers_ = JsArrayInteger.createArray().cast();
   }
   
   /**
    * Create the main widget structure - common to search_replace widgets
    */
   protected Widget createWidget(String content, String filename)
   {
      VerticalPanel container = new VerticalPanel();
      container.setWidth("100%");
      
      // Create header panel with filename and diff stats
      createHeaderPanel(filename, container);
      
      // Create editor wrapper with common styling
      SimplePanel editorWrapper = createEditorWrapper();
      
      // Create the ACE editor
      editor_ = createFileEditor(filename);
      editor_.getWidget().setWidth("100%");
      
      // Set initial content
      if (content != null && !content.trim().isEmpty())
      {
         editor_.setCode(content, false);
         editor_.retokenizeDocument();
      }
      
      editorWrapper.setWidget(editor_.getWidget());
      container.add(editorWrapper);
      
      // Create buttons if editable and diff data is available (not during streaming)
      if (isEditable_ && !isCancelled_ && preComputedDiffData_ != null) {
         createButtonContainer(container);
      }
      
      return container;
   }
   
   /**
    * Create buttons when they don't exist yet (for restored widgets)
    */
   public void createButtonsIfNeeded()
   {
      if (verticalButtonStack_ == null && isEditable_ && !isCancelled_) {
         Widget parent = this.getWidget();
         if (parent instanceof VerticalPanel) {
            createButtonContainer((VerticalPanel) parent);
         }
      }
   }
   
   /**
    * Create header panel with filename and diff stats parsing
    */
   private void createHeaderPanel(String filename, VerticalPanel container)
   {
      headerPanel_ = new HorizontalPanel();
      headerPanel_.setWidth("100%");
      headerPanel_.addStyleName(getHeaderStyleClass());
      
      // Always use dark header styling for all file editing widgets
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
      
      // Create filename label
      filenameLabel_ = new Label();
      filenameLabel_.addStyleName("filename");
      filenameLabel_.getElement().getStyle().setFontWeight(com.google.gwt.dom.client.Style.FontWeight.BOLD);
      filenameLabel_.getElement().getStyle().setColor("white");
      
      Label diffStatsLabel = null;
      
      if (filename != null && !filename.isEmpty()) {
         // Parse filename and diff stats
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
   }
   
   /**
    * Create editor wrapper with common styling
    */
   private SimplePanel createEditorWrapper()
   {
      SimplePanel wrapper = new SimplePanel();
      wrapper.setWidth("100%");
      wrapper.getElement().getStyle().setBorderWidth(1, Unit.PX);
      wrapper.getElement().getStyle().setBorderStyle(com.google.gwt.dom.client.Style.BorderStyle.SOLID);
      wrapper.getElement().getStyle().setBorderColor("#ccc");
      wrapper.getElement().getStyle().setProperty("borderRadius", "0 0 4px 4px");
      return wrapper;
   }
   
   /**
    * Create button container with vertical button stack
    * Uses exactly the same mechanism as console widgets for consistent positioning
    */
   private void createButtonContainer(VerticalPanel container)
   {
      // Create the new vertical button stack (no extracted items needed for file editing)
      verticalButtonStack_ = createVerticalButtonStack(functionCallType_, "");
      
      // Create a horizontal panel to hold the button stack on the right (same as console widgets)
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
   
   /**
    * Create ACE editor with file type detection and common configuration
    */
   private AceEditor createFileEditor(String filename)
   {
      AceEditor editor = new AceEditor();
      
      // Set unique ID for the editor
      editor.getWidget().getElement().setId(getEditorIdPrefix() + getMessageId());
      
      // Configure file type for syntax highlighting
      configureFileType(editor, filename);
      
      // Standard editor configuration
      editor.setShowLineNumbers(true);
      editor.setShowPrintMargin(false);
      editor.setUseWrapMode(true);
      editor.setReadOnly(false);
      editor.autoHeight();
      
      // Apply font sizing for proper theme integration
      FontSizer.applyNormalFontSize(editor.getWidget());
      
      return editor;
   }
   
   /**
    * Configure file type based on filename extension
    */
   private void configureFileType(AceEditor editor, String filename)
   {
      try {
         if (filename != null) {
            // Extract clean filename by removing HTML diff stats if present
            String cleanFilename = filename.replaceAll("<[^>]*>", "").trim();
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
         }
      } catch (Exception e) {
         Debug.log("DEBUG: Failed to set file type for filename: " + filename + ", error: " + e.getMessage());
      }
   }
   
   // Common content management methods
   
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
    */
   public void setContent(String content)
   {
      if (editor_ != null && content != null && !content.trim().isEmpty())
      {
         editor_.setCode(content, false);
         editor_.retokenizeDocument();
         
         // Apply diff highlighting after setting content
         com.google.gwt.core.client.Scheduler.get().scheduleDeferred(() -> {
            applyDiffHighlighting();
         });
      }
   }
   
   /**
    * Append streaming content to the editor
    */
   public void appendStreamingContent(String delta)
   {
      if (editor_ != null && delta != null && !delta.isEmpty())
      {
         String currentContent = editor_.getCode();
         String newContent = currentContent + delta;
         editor_.setCode(newContent, false);
      }
   }
   
   public String getFilename()
   {
      return filename_;
   }
   
   // Common diff highlighting functionality
   
   /**
    * Apply diff highlighting using pre-computed diff data
    */
   public void applyDiffHighlighting()
   {      
      if (preComputedDiffData_ != null) {
         applyDiffHighlightingFromData(preComputedDiffData_);
      }
   }
   
   /**
    * Apply comprehensive diff highlighting from R data with full metadata support
    */
   private void applyDiffHighlightingFromData(com.google.gwt.core.client.JavaScriptObject diffData) {
      if (diffData == null) return;
      
      // Update filename header with diff stats from the R response
      updateFilenameWithDiffStatsFromResponse(diffData);
      
      // Extract diff array from result
      com.google.gwt.core.client.JsArray<com.google.gwt.core.client.JavaScriptObject> diffArray = getDiffArray(diffData);
      
      if (diffArray == null) {
         return;
      }
      
      // Clear any existing highlighting
      clearDiffMarkers();
      
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
         
         // Create buttons now if they don't exist yet (streaming completion)
         if (verticalButtonStack_ == null && isEditable_ && !isCancelled_) {
            // Find the container to add buttons to
            Widget parent = this.getWidget();
            if (parent instanceof VerticalPanel) {
               createButtonContainer((VerticalPanel) parent);
            }
         }
      }
   }
   
   /**
    * Apply diff highlighting using ACE Editor annotations
    */
   private native void applyDiffAnnotations(com.google.gwt.core.client.JsArray<com.google.gwt.core.client.JavaScriptObject> diffArray) /*-{
      var self = this;
      var editor = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiFileEditorWidgetBase::editor_;
      
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
      var markerIds = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiFileEditorWidgetBase::diffMarkers_;
      
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
    * Update filename header with diff stats from R response
    */
   private native void updateFilenameWithDiffStatsFromResponse(com.google.gwt.core.client.JavaScriptObject diffResult) /*-{
      var self = this;
      
      // Extract filename_with_stats if available
      var filenameWithStats = diffResult.filename_with_stats;
      
      // Handle R vectors that come as arrays - extract the first element
      if (Array.isArray(filenameWithStats) && filenameWithStats.length > 0) {
         filenameWithStats = filenameWithStats[0];
      }
      
      if (filenameWithStats && filenameWithStats !== "" && typeof filenameWithStats === 'string') {
         // Schedule the filename update to avoid any timing issues
         setTimeout(function() {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiFileEditorWidgetBase::updateFilenameHeader(Ljava/lang/String;)(filenameWithStats);
         }, 50);
      }
   }-*/;
   
   /**
    * Update the filename header with diff statistics
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
      
      // Do the same parsing and splitting as widget creation code
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
   
   /**
    * Set up custom line numbers using JSNI to access ACE internals
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
    * Set up custom line numbers to show old/new line numbers for diffs
    */
   private native void setupCustomLineNumbers(com.google.gwt.core.client.JsArray<com.google.gwt.core.client.JavaScriptObject> diffArray) /*-{
      var self = this;
      var editor = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiFileEditorWidgetBase::editor_;
      
      if (!editor) return;
      
      try {
         var aceInstance = editor.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor::getWidget()().@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditorWidget::getEditor()();
         var session = aceInstance.@org.rstudio.studio.client.workbench.views.source.editors.text.ace.AceEditorNative::getSession()();
         
         if (!session) return;
         
         // Create custom gutter renderer for diff line numbers
         // First, analyze the diff data to calculate maximum line number widths
         var oldLineWidth = 1;
         var newLineWidth = 1;
         var diffLineInfo = [];
         
         for (var i = 0; i < diffArray.length; i++) {
            var diffLine = diffArray[i];
            var info = {
               type: diffLine.type,
               oldLine: diffLine.old_line,
               newLine: diffLine.new_line
            };
            
            // Extract values from arrays if needed
            if (Array.isArray(info.type) && info.type.length > 0) {
               info.type = info.type[0];
            }
            if (Array.isArray(info.oldLine) && info.oldLine.length > 0) {
               info.oldLine = info.oldLine[0];
            }
            if (Array.isArray(info.newLine) && info.newLine.length > 0) {
               info.newLine = info.newLine[0];
            }
            
            diffLineInfo[i] = info;
            
            // Calculate maximum widths needed
            if (info.oldLine > 0) {
               oldLineWidth = Math.max(oldLineWidth, String(info.oldLine).length);
            }
            if (info.newLine > 0) {
               newLineWidth = Math.max(newLineWidth, String(info.newLine).length);
            }
         }
         
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
      } catch (e) {
         console.error("Error setting up custom line numbers:", e);
      }
   }-*/;
   
   /**
    * Clear all diff highlighting markers
    */
   private native void clearDiffMarkers() /*-{
      var self = this;
      var editor = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiFileEditorWidgetBase::editor_;
      
      if (!editor || !editor.getWidget) return;
      
      var aceInstance = editor.getWidget().getEditor();
      if (!aceInstance || !aceInstance.session) return;
      
      var markerIds = self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiFileEditorWidgetBase::diffMarkers_;
      
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
   
   // Standard button management
   
   @Override
   public void hideButtons() {
      // For cancelled operations, buttons don't exist, so nothing to hide
      if (isCancelled_) {
         return;
      }
      
      // Use base class functionality
      hideVerticalStack();
   }
   
   @Override
   protected void hideVerticalStack() {
      if (verticalButtonStack_ != null) {
         verticalButtonStack_.setVisible(false);
      }
   }
   
   // Abstract methods that subclasses must implement for customization
   
   /**
    * Get the CSS style class for the header panel
    */
   protected abstract String getHeaderStyleClass();
   
   /**
    * Get the CSS style class for the button container
    */
   protected abstract String getButtonContainerStyleClass();
   

   
   /**
    * Get the ID prefix for the ACE editor element
    */
   protected abstract String getEditorIdPrefix();
   
   // Common fields
   protected final String filename_;
   protected final String explanation_;
   protected final boolean isEditable_;
   protected final boolean isCancelled_;
   protected final boolean skipDiffHighlighting_;
   protected final com.google.gwt.core.client.JavaScriptObject preComputedDiffData_;
   
   protected AceEditor editor_;
   protected VerticalPanel verticalButtonStack_;
   protected HorizontalPanel headerPanel_;
   protected Label filenameLabel_;
   protected JsArrayInteger diffMarkers_;
   
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
} 