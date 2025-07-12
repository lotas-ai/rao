package org.rstudio.studio.client.workbench.views.source.editors.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rstudio.core.client.Debug;
import org.rstudio.core.client.HandlerRegistrations;
import org.rstudio.core.client.dom.DomUtils;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.AceEditorNative;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Renderer;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.LineWidget;
import org.rstudio.studio.client.workbench.views.source.editors.text.events.FoldChangeEvent;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * Manages persistent diff indicators in the ACE editor gutter using PinnedLineWidget approach
 */
public class PersistentDiffGutterManager
{
   private static final String GUTTER_CLASS_ACCEPTED = "ace_persistent_diff_accepted";
   private static final String GUTTER_CLASS_DELETED = "ace_persistent_diff_deleted";
   private static final String LINE_WIDGET_TYPE = "persistent_diff";
   
   private final AceEditor editor_;
   private final String filePath_;
   private final Map<Integer, DiffLineInfo> diffLines_;
   private final Map<Integer, List<String>> deletedLineGroups_;
   private final List<Integer> gutterDecorations_;
   private final Map<Integer, PinnedLineWidget> pinnedWidgets_;
   private final Map<Integer, Boolean> expandedStates_;
   private final HandlerRegistrations registrations_;
   private boolean isActive_;
   private com.google.gwt.user.client.Timer debounceTimer_;
   private boolean changeListenerSetup_;
   private String originalFileContent_;
   
   /**
    * Information about a line with diff changes
    */
   public static class DiffLineInfo
   {
      public final String type;
      public final String content;
      public final int oldLine;
      public final int newLine;
      public final int displayLine;
      
      public DiffLineInfo(String type, String content, int oldLine, int newLine, int displayLine)
      {
         this.type = type;
         this.content = content;
         this.oldLine = oldLine;
         this.newLine = newLine;
         this.displayLine = displayLine;
      }
   }
   
   /**
    * Host for managing PinnedLineWidget lifecycle
    */
   private class DiffLineWidgetHost implements PinnedLineWidget.Host
   {
      @Override
      public void onLineWidgetAdded(LineWidget widget)
      {
         // Widget added successfully
      }
      
      @Override
      public void onLineWidgetRemoved(LineWidget widget)
      {
         // Clean up when widget is removed
         for (Map.Entry<Integer, PinnedLineWidget> entry : pinnedWidgets_.entrySet())
         {
            if (entry.getValue().getLineWidget() == widget)
            {
               pinnedWidgets_.remove(entry.getKey());
               expandedStates_.remove(entry.getKey());
               break;
            }
         }
      }
   }
   
   public PersistentDiffGutterManager(AceEditor editor, String filePath)
   {
      editor_ = editor;
      filePath_ = filePath;
      diffLines_ = new HashMap<>();
      deletedLineGroups_ = new HashMap<>();
      gutterDecorations_ = new ArrayList<>();
      pinnedWidgets_ = new HashMap<>();
      expandedStates_ = new HashMap<>();
      registrations_ = new HandlerRegistrations();
      isActive_ = false;
      changeListenerSetup_ = false;
      originalFileContent_ = null;
            
      setupGutterClickHandler();
   }
   
   /**
    * Initialize the persistent diff indicators for the current file
    */
   public void initialize()
   {
      // Always clear existing state first, regardless of isActive_ status
      // This ensures we have a clean slate even if we're re-initializing
      clearAll();
      
      // Call R backend to get diff data
      callGetPersistentDiffData();
      
      isActive_ = true;
   }
   
   /**
    * Clear all diff indicators and widgets - this is the method AceEditor expects
    */
   public void clearAll()
   {
      // Clear all pinned widgets
      for (PinnedLineWidget widget : pinnedWidgets_.values())
      {
         widget.detach();
      }
      pinnedWidgets_.clear();
      expandedStates_.clear();
      
      // Clear gutter decorations (only if editor is ready)
      try {
         if (editor_ != null && editor_.getWidget() != null && editor_.getWidget().getEditor() != null) {
            clearGutterDecorations();
         } else {
            // Just clear the list since we can't access the actual gutter
            gutterDecorations_.clear();
         }
      } catch (Exception e) {
         Debug.log("PersistentDiffGutterManager: Exception in clearAll() when clearing gutter decorations: " + e.getMessage());
         // Clear the list anyway
         gutterDecorations_.clear();
      }
      
      // Clear data structures
      diffLines_.clear();
      deletedLineGroups_.clear();
      
      // Cancel any pending debounce timer
      if (debounceTimer_ != null) {
         debounceTimer_.cancel();
         debounceTimer_ = null;
      }
      
      // Reset change listener flag
      changeListenerSetup_ = false;
      
      // Clear original content
      originalFileContent_ = null;
      
      isActive_ = false;
   }
   
   /**
    * Set up dynamic diff updates when editor content changes
    */
   private void setupDynamicDiffUpdates()
   {
      // Only set up once
      if (changeListenerSetup_) {
         return;
      }
      
      AceEditorNative editor = editor_.getWidget().getEditor();
      if (editor == null) {
         return;
      }
      
      // Set up debounced refresh on editor changes
      setupChangeListener();
      changeListenerSetup_ = true;
   }
   
   /**
    * Set up change listener with debouncing using GWT patterns
    */
   private void setupChangeListener()
   {
      // Create debounce timer (500ms delay)
      debounceTimer_ = new com.google.gwt.user.client.Timer() {
         @Override
         public void run() {
            if (isActive_) {
               refreshDiffIndicators();
            }
         }
      };
      
      // Add change listener to editor using GWT's ACE integration
      // This approach uses the existing AceEditor change event system
      setupGWTChangeHandler();
   }
   
   /**
    * Set up GWT-compatible change handler for ACE editor
    */
   private void setupGWTChangeHandler()
   {
      // Use a native method to add the change listener, but only call GWT methods from the callback
      addSimpleChangeListener();
   }
   
   /**
    * Add change listener using simple callback approach
    */
   private native void addSimpleChangeListener() /*-{
      var self = this;
      var editor = this.@org.rstudio.studio.client.workbench.views.source.editors.text.PersistentDiffGutterManager::editor_.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor::getWidget()().@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditorWidget::getEditor()();
      
      if (editor) {
         editor.on('change', function(delta) {
            // Call the simple callback method directly
            self.@org.rstudio.studio.client.workbench.views.source.editors.text.PersistentDiffGutterManager::onEditorChangeCallback()();
         });
      }
   }-*/;
   
   /**
    * Simple callback method called from native code
    */
   private void onEditorChangeCallback()
   {
      // Use GWT's scheduler to defer the actual work
      com.google.gwt.core.client.Scheduler.get().scheduleDeferred(new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
         @Override
         public void execute() {
            onEditorChanged();
         }
      });
   }
   
   /**
    * Handle editor change events
    */
   private void onEditorChanged()
   {
      if (!isActive_) {
         return;
      }
      
      // Cancel previous timer
      if (debounceTimer_ != null) {
         debounceTimer_.cancel();
      }
      
      // Start new timer with 500ms delay
      if (debounceTimer_ != null) {
         debounceTimer_.schedule(500);
      }
   }
   
   /**
    * Store current editor content as baseline for future dynamic comparisons
    */
   private void storeOriginalContent()
   {
      AceEditorNative editor = editor_.getWidget().getEditor();
      if (editor == null) {
         return;
      }
      
      originalFileContent_ = getCurrentEditorContent();
   }
   
   /**
    * Get current content from the ACE editor
    */
   private native String getCurrentEditorContent() /*-{
      var editor = this.@org.rstudio.studio.client.workbench.views.source.editors.text.PersistentDiffGutterManager::editor_.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor::getWidget()().@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditorWidget::getEditor()();
      
      if (editor && editor.getValue) {
         return editor.getValue();
      }
      return "";
   }-*/;
   
   /**
    * Refresh diff indicators by recomputing diff with current editor content
    */
   private void refreshDiffIndicators()
   {
      if (!isActive_) {
         return;
      }
      
      if (originalFileContent_ == null) {
         return;
      }
      
      // Clear current indicators but keep the manager active
      clearCurrentIndicators();
      
      // Compute diff between original content and current editor content
      computeClientSideDiff();
   }
   
   /**
    * Compute diff between original and current content on the client side
    */
   private void computeClientSideDiff()
   {
      String currentContent = getCurrentEditorContent();
      if (currentContent == null) {
         return;
      }
      
      // Compute diff using JavaScript
      JsArray<JavaScriptObject> diffArray = computeDiffInJS(originalFileContent_, currentContent);
      
      // Process the diff data using existing logic
      processDiffData(diffArray);
   }
   
   /**
    * Compute diff between two strings using JavaScript LCS algorithm and return in same format as R backend
    */
   private native JsArray<JavaScriptObject> computeDiffInJS(String originalContent, String currentContent) /*-{
      
      // Split content into lines
      var originalLines = originalContent.split('\n');
      var currentLines = currentContent.split('\n');
      
      var origLen = originalLines.length;
      var currLen = currentLines.length;
      
      // Build LCS matrix for proper diff alignment
      var lcs = [];
      for (var i = 0; i <= origLen; i++) {
         lcs[i] = [];
         for (var j = 0; j <= currLen; j++) {
            lcs[i][j] = 0;
         }
      }
      
      // Fill LCS matrix
      for (var i = 1; i <= origLen; i++) {
         for (var j = 1; j <= currLen; j++) {
            if (originalLines[i-1] === currentLines[j-1]) {
               lcs[i][j] = lcs[i-1][j-1] + 1;
            } else {
               lcs[i][j] = Math.max(lcs[i-1][j], lcs[i][j-1]);
            }
         }
      }
      
      // Backtrack to generate diff entries - create the exact same structure as R backend
      var diffEntries = [];
      var i = origLen;
      var j = currLen;
      
      while (i > 0 || j > 0) {
         if (i > 0 && j > 0 && originalLines[i-1] === currentLines[j-1]) {
            // Lines are the same - create unchanged entry (like R backend)
            diffEntries.unshift({
               type: "unchanged",
               content: currentLines[j-1],
               old_line: i,
               new_line: j
            });
            i--;
            j--;
         } else if (j > 0 && (i === 0 || lcs[i][j-1] >= lcs[i-1][j])) {
            // Insertion in current file
            diffEntries.unshift({
               type: "added",
               content: currentLines[j-1],
               old_line: -1, // -1 represents NA_integer_ 
               new_line: j
            });
            j--;
         } else if (i > 0) {
            // Deletion from original file
            diffEntries.unshift({
               type: "deleted",
               content: originalLines[i-1],
               old_line: i,
               new_line: -1  // -1 represents NA_integer_
            });
            i--;
         }
      }
      
      return diffEntries;
   }-*/;
   
   /**
    * Clear current visual indicators without deactivating the manager
    */
   private void clearCurrentIndicators()
   {
      // Clear pinned widgets
      for (PinnedLineWidget widget : pinnedWidgets_.values())
      {
         widget.detach();
      }
      pinnedWidgets_.clear();
      expandedStates_.clear();
      
      // Clear gutter decorations
      if (editor_ != null && editor_.getWidget() != null && editor_.getWidget().getEditor() != null) {
         clearGutterDecorations();
      } else {
         gutterDecorations_.clear();
      }
      
      // Clear data structures
      diffLines_.clear();
      deletedLineGroups_.clear();
   }
   
   /**
    * Process diff data from R backend
    */
   private void processDiffData(JsArray<JavaScriptObject> diffData)
   {
      
      if (diffData.length() == 0) {
         return;
      }
      
      // Clear existing data
      diffLines_.clear();
      deletedLineGroups_.clear();
      
      // First pass: Parse all diff entries and store them without positioning
      List<DiffLineInfo> allDiffLines = new ArrayList<>();
      for (int i = 0; i < diffData.length(); i++)
      {
         JavaScriptObject diffEntry = diffData.get(i);
         
         // Parse the diff entry
         DiffLineInfo lineInfo = parseDiffLine(diffEntry);
         if (lineInfo == null) {
            continue;
         }
         
         allDiffLines.add(lineInfo);
      }
      
      // Second pass: Map to editor lines with complete context available
      for (DiffLineInfo lineInfo : allDiffLines)
      {
         // Map to editor line
         int editorLine = mapToEditorLine(lineInfo, allDiffLines);
         
         if (editorLine < 0) {
            continue;
         }
         
         diffLines_.put(editorLine, lineInfo);
         
         if ("deleted".equals(lineInfo.type))
         {
            // Group deleted lines by their position
            List<String> deletedLines = deletedLineGroups_.get(editorLine);
            if (deletedLines == null)
            {
               deletedLines = new ArrayList<>();
               deletedLineGroups_.put(editorLine, deletedLines);
            }
            deletedLines.add(lineInfo.content);
         }
      }
      
      // Apply the diff indicators
      applyDiffIndicators();
   }
   
   /**
    * Apply diff indicators to the editor
    */
   private void applyDiffIndicators()
   {
      AceEditorNative editor = editor_.getWidget().getEditor();
      if (editor == null) {
         return;
      }
      
      Renderer renderer = editor.getRenderer();
      if (renderer == null) {
         return;
      }
      
      int decorationsApplied = 0;
      
      // First pass: Apply green gutter decorations only for accepted/added changes
      for (Map.Entry<Integer, DiffLineInfo> entry : diffLines_.entrySet())
      {
         int line = entry.getKey();
         DiffLineInfo lineInfo = entry.getValue();
         
         // Only show green decorations for accepted/added changes
         if ("accepted".equals(lineInfo.type) || "added".equals(lineInfo.type))
         {
            renderer.addGutterDecoration(line, GUTTER_CLASS_ACCEPTED);
            gutterDecorations_.add(line);
            decorationsApplied++;
         } else {
         }
      }
      
      // Second pass: Add red dropdown arrows for lines with deleted content
      for (Map.Entry<Integer, List<String>> entry : deletedLineGroups_.entrySet())
      {
         int line = entry.getKey();
         List<String> deletedLines = entry.getValue();
         
         // Add red dropdown arrow indicator (not gutter decoration)
         addDropdownArrowToGutter(line);
         decorationsApplied++;
      }
      
   }
   
   /**
    * Map diff line info to editor line number using context-aware positioning for deleted lines
    */
   private int mapToEditorLine(DiffLineInfo lineInfo, List<DiffLineInfo> allDiffLines)
   {
      // Get current editor to check bounds
      AceEditorNative editor = editor_.getWidget().getEditor();
      if (editor == null) {
         return -1;
      }
      
      int editorLineCount = editor.getSession().getLength();
      
      // For added/unchanged lines, use the new line number (valid lines are > 0, -1 represents NA)
      if (lineInfo.newLine > 0)
      {
         int targetLine = lineInfo.newLine - 1; // Convert to 0-based
         // Clamp to valid editor range
         return Math.min(targetLine, editorLineCount - 1);
      }
      
      // For deleted lines (newLine is NA), find contextual position based on unchanged lines
      if ("deleted".equals(lineInfo.type))
      {
         return findContextualPositionForDeletedLine(lineInfo, allDiffLines);
      }
      
      return -1;
   }
   
   /**
    * Find the appropriate position for a deleted line by finding the next unchanged line
    */
   private int findContextualPositionForDeletedLine(DiffLineInfo deletedLine, List<DiffLineInfo> allDiffLines)
   {
      AceEditorNative editor = editor_.getWidget().getEditor();
      if (editor == null || deletedLine.oldLine < 0) {  // -1 represents NA_integer_
         return 0;
      }
      
      int editorLineCount = editor.getSession().getLength();
      
      // Find the next unchanged line after this deleted line in the original file
      DiffLineInfo nextUnchangedLine = null;
      int minOldLineAfterDeletion = Integer.MAX_VALUE;
      
      for (DiffLineInfo otherLine : allDiffLines)
      {
         // Look for unchanged lines that come after the deleted line in the original file
         // Make sure the other line has valid oldLine and newLine values
         if ("unchanged".equals(otherLine.type) && 
             otherLine.oldLine > 0 && otherLine.oldLine > deletedLine.oldLine && 
             otherLine.oldLine < minOldLineAfterDeletion &&
             otherLine.newLine > 0)
         {
            minOldLineAfterDeletion = otherLine.oldLine;
            nextUnchangedLine = otherLine;
         }
      }
      
      // If we found the next unchanged line, position one line before it in the current file
      if (nextUnchangedLine != null)
      {
         int targetLine = Math.max(0, nextUnchangedLine.newLine - 2); // One before, convert to 0-based
         return Math.min(targetLine, editorLineCount - 1);
      }
      
      // Fallback: if no unchanged line found after deletion, position at end of file
      return Math.max(0, editorLineCount - 1);
   }
   
   /**
    * Add dropdown arrow to gutter using the specific editor instance
    */
   private native void addDropdownArrowToGutter(int line) /*-{
      try {
         // Get the specific editor instance DOM element
         var editor = this.@org.rstudio.studio.client.workbench.views.source.editors.text.PersistentDiffGutterManager::editor_.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor::getWidget()();
         var editorElement = editor.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditorWidget::getElement()();
         
         if (!editorElement) {
            return;
         }
         
         // Find the gutter within this specific editor
         var gutter = editorElement.querySelector('.ace_gutter');
         if (!gutter) {
            return;
         }
         
         // Find the specific gutter cell for this line (0-based)
         var gutterCells = gutter.querySelectorAll('.ace_gutter-cell');
         if (line >= gutterCells.length) {
            return;
         }
         
         var gutterCell = gutterCells[line];
         if (!gutterCell) {
            return;
         }
         
         // Check if arrow already exists
         var existingArrow = gutterCell.querySelector('.ace_persistent_diff_dropdown_arrow');
         if (existingArrow) {
            return;
         }
         
         // Create dropdown arrow element
         var arrow = $doc.createElement('span');
         arrow.className = 'ace_persistent_diff_dropdown_arrow';
         arrow.innerHTML = '▶';  // Right-pointing triangle
         arrow.style.cssText = 'display: inline-block; margin-left: 4px; vertical-align: middle; color: #f44336; cursor: pointer; font-size: 10px; user-select: none; z-index: 10; line-height: 1;';
         
         // Add click handler
         var self = this;
         arrow.onclick = function(e) {
            e.stopPropagation();
            self.@org.rstudio.studio.client.workbench.views.source.editors.text.PersistentDiffGutterManager::toggleDeletedLinesWidget(I)(line);
         };
         
         // Don't modify gutter cell positioning - just append the arrow directly
         // The arrow's absolute positioning will be relative to the nearest positioned ancestor
         gutterCell.appendChild(arrow);
      } catch (e) {
         console.log("DEBUG: Exception in addDropdownArrowToGutter: " + e.message);
      }
   }-*/;
   
   /**
    * Setup click handler for gutter
    */
   private void setupGutterClickHandler()
   {
      AceEditorNative editor = editor_.getWidget().getEditor();
      if (editor == null)
         return;
      
      registrations_.add(editor_.addMouseDownHandler(new MouseDownHandler()
      {
         @Override
         public void onMouseDown(MouseDownEvent event)
         {
            Element target = event.getNativeEvent().getEventTarget().cast();
            if (target.getClassName().contains("ace_gutter"))
            {
               int line = getLineFromGutterElement(target);
               if (line >= 0 && deletedLineGroups_.containsKey(line))
               {
                  toggleDeletedLinesWidget(line);
               }
            }
         }
      }));
   }
   
   /**
    * Toggle the deleted lines widget for a specific line
    */
   private void toggleDeletedLinesWidget(int line)
   {
      if (pinnedWidgets_.containsKey(line))
      {
         // Hide the widget and change arrow to point right
         hideDeletedLinesWidget(line);
         updateDropdownArrow(line, false);
      }
      else
      {
         // Show the widget and change arrow to point down
         showDeletedLinesWidget(line);
         updateDropdownArrow(line, true);
      }
   }
   
   /**
    * Update dropdown arrow direction using the specific editor instance
    */
   private native void updateDropdownArrow(int line, boolean expanded) /*-{
      try {
         // Get the specific editor instance DOM element
         var editor = this.@org.rstudio.studio.client.workbench.views.source.editors.text.PersistentDiffGutterManager::editor_.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor::getWidget()();
         var editorElement = editor.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditorWidget::getElement()();
         
         if (!editorElement) {
            return;
         }
         
         // Find the gutter within this specific editor
         var gutter = editorElement.querySelector('.ace_gutter');
         if (!gutter) {
            return;
         }
         
         // Find the specific gutter cell for this line (0-based)
         var gutterCells = gutter.querySelectorAll('.ace_gutter-cell');
         if (line >= gutterCells.length) {
            return;
         }
         
         var gutterCell = gutterCells[line];
         if (!gutterCell) {
            return;
         }
         
         // Find the dropdown arrow for this line
         var arrow = gutterCell.querySelector('.ace_persistent_diff_dropdown_arrow');
         if (arrow) {
            arrow.innerHTML = expanded ? '▼' : '▶';
         }
      } catch (e) {
         console.log("DEBUG: Exception in updateDropdownArrow: " + e.message);
      }
   }-*/;
   
   /**
    * Show the deleted lines widget using simple HTML like console output
    */
   private void showDeletedLinesWidget(int line)
   {
      List<String> deletedLines = deletedLineGroups_.get(line);
      if (deletedLines == null || deletedLines.isEmpty())
         return;

      // Create wrapper panel
      SimplePanel wrapper = new SimplePanel();
      
      // Create the content container
      FlowPanel container = new FlowPanel();
      container.setStyleName("ace_persistent_diff_inline_container");
      
      // Use console resource styles to match exactly
      org.rstudio.studio.client.workbench.views.console.ConsoleResources.ConsoleStyles consoleStyles = 
         org.rstudio.studio.client.workbench.views.console.ConsoleResources.INSTANCE.consoleStyles();
      
      // Add each deleted line as a separate element
      for (String deletedLine : deletedLines) {
         HTML lineElement = new HTML(deletedLine.isEmpty() ? "&nbsp;" : deletedLine);
         lineElement.addStyleName(consoleStyles.output()); // Use exact console output styling
         lineElement.getElement().getStyle().setProperty("backgroundColor", "#f8d7da"); // Light red background
         lineElement.getElement().getStyle().setProperty("margin", "0"); // Remove any margin
         lineElement.getElement().getStyle().setProperty("padding", "0"); // Remove any padding
         lineElement.getElement().getStyle().setProperty("paddingLeft", "4px"); // Add 1px left spacing
         lineElement.getElement().getStyle().setProperty("border", "none"); // Remove border
         lineElement.getElement().getStyle().setProperty("borderRadius", "0"); // Square corners
         lineElement.getElement().getStyle().setProperty("display", "block"); // Block display
         container.add(lineElement);
      }
      
      // Style the container to match console exactly
      container.getElement().getStyle().setProperty("margin", "0");
      container.getElement().getStyle().setProperty("padding", "0");
      container.getElement().getStyle().setProperty("border", "none");
      container.getElement().getStyle().setProperty("borderRadius", "0");
      container.getElement().getStyle().setProperty("backgroundColor", "#f8d7da");
      
      wrapper.add(container);
      
      // Create the pinned line widget
      PinnedLineWidget pinnedWidget = new PinnedLineWidget(
         LINE_WIDGET_TYPE, 
         editor_, 
         wrapper, 
         line, 
         null, 
         new DiffLineWidgetHost()
      );
      
      // Store the widget
      pinnedWidgets_.put(line, pinnedWidget);
      expandedStates_.put(line, true);
   }
   
   /**
    * Configure mini editor to allow selection and copying but prevent editing
    * @deprecated This method is no longer used - we now use simple HTML content
    */
   @Deprecated
   private void configureMiniEditorForSelection(AceEditor miniEditor)
   {
      // Use standard ACE read-only mode - this allows selection and copying but prevents editing
      miniEditor.setReadOnly(true);
      
      // Disable autocompletion for cleaner appearance using the proper method
      miniEditor.getWidget().getEditor().setCompletionOptions(false, false, false, 0, 0);
   }
   
   /**
    * Hide the deleted lines widget for a specific line
    */
   private void hideDeletedLinesWidget(int line)
   {
      PinnedLineWidget widget = pinnedWidgets_.get(line);
      if (widget != null)
      {
         widget.detach();
         pinnedWidgets_.remove(line);
         expandedStates_.remove(line);
      }
   }
   
   /**
    * Clear all gutter decorations and dropdown arrows
    */
   private void clearGutterDecorations()
   {
      try {
         AceEditorNative editor = editor_.getWidget().getEditor();
         if (editor == null) {
            return;
         }
         
         Renderer renderer = editor.getRenderer();
         if (renderer == null) {
            return;
         }
         
         for (Integer line : gutterDecorations_)
         {
            renderer.removeGutterDecoration(line, GUTTER_CLASS_ACCEPTED);
            renderer.removeGutterDecoration(line, GUTTER_CLASS_DELETED);
         }
         
         // Also clear all dropdown arrows
         clearAllDropdownArrows();
         
         gutterDecorations_.clear();
      } catch (Exception e) {
         Debug.log("PersistentDiffGutterManager: Exception in clearGutterDecorations(): " + e.getMessage());
      }
   }
   
   /**
    * Clear all dropdown arrows from the gutter using the specific editor instance
    */
   private native void clearAllDropdownArrows() /*-{
      try {
         // Get the specific editor instance DOM element
         var editor = this.@org.rstudio.studio.client.workbench.views.source.editors.text.PersistentDiffGutterManager::editor_.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor::getWidget()();
         var editorElement = editor.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditorWidget::getElement()();
         
         if (!editorElement) {
            return;
         }
         
         // Find the gutter within this specific editor
         var gutter = editorElement.querySelector('.ace_gutter');
         if (!gutter) {
            return;
         }
         
         // Remove all dropdown arrows
         var arrows = gutter.querySelectorAll('.ace_persistent_diff_dropdown_arrow');
         for (var i = 0; i < arrows.length; i++) {
            arrows[i].remove();
         }
      } catch (e) {
         console.log("DEBUG: Exception in clearAllDropdownArrows: " + e.message);
      }
   }-*/;
   
   /**
    * Parse a diff line entry from JavaScript object
    */
   private DiffLineInfo parseDiffLine(JavaScriptObject diffLine)
   {
      String type = getStringFromDiff(diffLine, "type");
      String content = getStringFromDiff(diffLine, "content");
      int oldLine = getIntFromDiff(diffLine, "old_line");
      int newLine = getIntFromDiff(diffLine, "new_line");
      
      if (type == null || content == null)
         return null;
      
      return new DiffLineInfo(type, content, oldLine, newLine, -1);  // displayLine not used
   }
   
   /**
    * Get line number from gutter element
    */
   private native int getLineFromGutterElement(Element element) /*-{
      var gutterElement = element;
      while (gutterElement && !gutterElement.className.includes("ace_gutter-cell")) {
         gutterElement = gutterElement.parentElement;
      }
      
      if (gutterElement && gutterElement.textContent) {
         var lineNumber = parseInt(gutterElement.textContent.trim());
         if (!isNaN(lineNumber)) {
            return lineNumber - 1; // Convert to 0-based
         }
      }
      return -1;
   }-*/;
   
   /**
    * Get string value from diff object, handling arrays
    */
   private native String getStringFromDiff(JavaScriptObject diffLine, String key) /*-{
      var value = diffLine[key];
      if (value === null || value === undefined) {
         return null;
      }
      if (Array.isArray(value)) {
         return value.length > 0 ? value[0] : null;
      }
      return String(value);
   }-*/;
   
   /**
    * Get int value from diff object, returning -1 for NA_integer_ (null/undefined)
    */
   private native int getIntFromDiff(JavaScriptObject diffLine, String key) /*-{
      var value = diffLine[key];
      if (value === null || value === undefined || value === -1) {
         return -1;  // -1 represents NA_integer_ from R backend
      }
      return parseInt(value) || -1;
   }-*/;
   
   /**
    * Call R backend to get persistent diff data
    */
   private void callGetPersistentDiffData()
   {
      try {
         RStudioGinjector.INSTANCE.getServer().getPersistentDiffData(filePath_, new ServerRequestCallback<JavaScriptObject>()
         {
            @Override
            public void onResponseReceived(JavaScriptObject response)
            {
               try {
                  JsArray<JavaScriptObject> diffArray = convertResponseToDiffArray(response);
                  
                  // Extract and store the original content from R backend response
                  String originalContent = extractOriginalContentFromResponse(response);
                  if (originalContent != null) {
                     originalFileContent_ = originalContent;
                  } else {
                     storeOriginalContent();
                  }
                  
                  processDiffData(diffArray);
                  
                  // Set up dynamic diff updates AFTER initial diff data is successfully loaded
                  setupDynamicDiffUpdates();
               } catch (Exception e) {
                  Debug.log("PersistentDiffGutterManager: Exception in onResponseReceived: " + e.getMessage());
               }
            }
            
            @Override
            public void onError(ServerError error)
            {
               Debug.log("PersistentDiffGutterManager: onError() called");
               Debug.log("PersistentDiffGutterManager: Error getting diff data: " + error.getMessage());
            }
         });
      } catch (Exception e) {
         Debug.log("PersistentDiffGutterManager: Exception calling server: " + e.getMessage());
      }
   }
   
   /**
    * Extract original file content from R backend response
    */
   private native String extractOriginalContentFromResponse(JavaScriptObject response) /*-{
      if (!response) {
         return null;
      }
      
      // Check if R backend provided original_content field
      if (response.original_content) {
         // R sends strings as single-element arrays, so extract the first element
         var content = Array.isArray(response.original_content) ? 
                      response.original_content[0] : 
                      response.original_content;
         
         return content;
      } else {
         return null;
      }
   }-*/;
   
   /**
    * Convert server response to diff array
    */
   private native JsArray<JavaScriptObject> convertResponseToDiffArray(JavaScriptObject response) /*-{
      if (!response) {
         return [];
      }
      
      // The R function returns list(diffs = file_diffs) and the RPC system unwraps it
      // So the response object is directly {diffs: {...}}, not {result: {diffs: {...}}}
      if (response.diffs) {
         var allDiffData = [];
         
         // Iterate through all message entries in the diffs object
         for (var messageId in response.diffs) {
            if (response.diffs.hasOwnProperty(messageId)) {
               var messageEntry = response.diffs[messageId];
               // Extract diff_data array from this message
               if (messageEntry.diff_data && Array.isArray(messageEntry.diff_data)) {
                  // Add each diff entry to our flattened array
                  for (var i = 0; i < messageEntry.diff_data.length; i++) {
                     allDiffData.push(messageEntry.diff_data[i]);
                  }
               }
            }
         }
         
         return allDiffData;
      }
      
      return [];
   }-*/;
      
   /**
    * Dispose of resources
    */
   public void dispose()
   {
      clearAll();
      registrations_.removeHandler();
      
      // Clean up debounce timer
      if (debounceTimer_ != null) {
         debounceTimer_.cancel();
         debounceTimer_ = null;
      }
   }
   
   /**
    * Escape HTML content
    */
   private String escapeHtml(String html)
   {
      return SafeHtmlUtils.htmlEscape(html);
   }
} 