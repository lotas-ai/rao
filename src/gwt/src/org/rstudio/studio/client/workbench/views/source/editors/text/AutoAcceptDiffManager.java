/*
 * AutoAcceptDiffManager.java
 *
 * Copyright (C) 2025 by Lotas Inc.
 *
 */
package org.rstudio.studio.client.workbench.views.source.editors.text;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.views.console.ConsoleResources;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.AceBackgroundHighlighter;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Position;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Range;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages auto-accept inline diff display with Accept/Reject buttons for each section
 */
public class AutoAcceptDiffManager
{
   interface Resources extends ClientBundle
   {
      @Source("AutoAcceptWidget.css")
      CssResource styles();
   }
   
   private static final Resources RES = GWT.create(Resources.class);
   static
   {
      RES.styles().ensureInjected();
   }
   
   private final AceEditor editor_;
   private final String filePath_;
   private AutoAcceptFloatingBar floatingBar_;
   
   // Track diff sections and their widgets
   private final List<DiffSection> diffSections_ = new ArrayList<>();
   private final Map<String, PinnedLineWidget> sectionWidgets_ = new HashMap<>();
   private final List<Integer> lineMarkerIds_ = new ArrayList<>();
   
   // Cached section info for operations
   private Map<String, AutoAcceptSectionComputer.SectionInfo> sectionInfoMap_ = new HashMap<>();
   
   // Undo/redo integration
   private final List<MetadataUndoAction> metadataActions_ = new ArrayList<>();
   private int previousRevision_ = -1;
   private HandlerRegistration undoRedoHandler_;
   private HandlerRegistration documentChangedHandler_;
   
   /**
    * Represents a contiguous section of changes
    */
   private static class DiffSection
   {
      String sectionId;
      int startLine;
      int endLine;
      List<Integer> addedLines = new ArrayList<>();
      List<String> deletedLines = new ArrayList<>();
      int widgetLineNumber; // Where to place the widget (after last added line or at deletion point)
   }
   
   /**
    * Tracks metadata changes tied to document revisions for undo/redo integration
    */
   private static class MetadataUndoAction
   {
      enum Type { ACCEPT, REJECT }
      
      int documentRevision;
      Type type;
      String previousAcceptedContent;
      String newAcceptedContent;
      String sectionId;
      
      // Constructor for ACCEPT operations
      MetadataUndoAction(int revision, String prevContent, String newContent, String section)
      {
         this.type = Type.ACCEPT;
         this.documentRevision = revision;
         this.previousAcceptedContent = prevContent;
         this.newAcceptedContent = newContent;
         this.sectionId = section;
      }
      
      // Constructor for REJECT operations
      static MetadataUndoAction createReject(int revision, String section)
      {
         MetadataUndoAction action = new MetadataUndoAction(revision, null, null, section);
         action.type = Type.REJECT;
         return action;
      }
   }
   
   /**
    * Diff entry from R backend
    */
   private static class DiffEntry
   {
      String type;
      String content;
      int oldLine;
      int newLine;
   }
   
   public AutoAcceptDiffManager(AceEditor editor, String filePath)
   {
      editor_ = editor;
      filePath_ = filePath;
      setupUndoRedoIntegration();
   }
   
   public void activate()
   {
      showFloatingBar();
      refreshAutoAcceptDiffs();
   }
   
   public void deactivate()
   {
      hideFloatingBar();
      clearAll();
   }
   
   /**
    * Public refresh method for external callers (e.g., DiffDisplayCoordinator)
    */
   public void refresh()
   {
      refreshAutoAcceptDiffs();
   }
   
   /**
    * Navigate to a specific section (0-based index)
    */
   public void navigateToSection(int sectionIndex)
   {
      if (sectionIndex < 0 || sectionIndex >= diffSections_.size())
      {
         return;
      }
      
      DiffSection section = diffSections_.get(sectionIndex);
      // Scroll to the section's widget line
      if (section.widgetLineNumber > 0)
      {
         editor_.scrollToLine(section.widgetLineNumber - 1, true);
      }
   }
   
   /**
    * Setup undo/redo integration to track metadata changes
    */
   private void setupUndoRedoIntegration()
   {
      previousRevision_ = getDocumentRevision();
      
      undoRedoHandler_ = editor_.addUndoRedoHandler(event -> {
         handleUndoRedo(event.isRedo());
      });
      
      documentChangedHandler_ = editor_.addDocumentChangedHandler(event -> {
         handleDocumentChange();
      });
   }
   
   /**
    * Get current document revision from undo manager
    */
   private int getDocumentRevision()
   {
      return editor_.getSession().getUndoManager().getRevision();
   }
   
   /**
    * Focus the editor and set cursor to the specified line (ACE line number, 0-based)
    */
   private void focusEditorAtLine(int aceLineNumber)
   {
      // Focus the editor
      editor_.focus();
      
      // Set cursor to the line (column 0)
      editor_.setCursorPosition(Position.create(aceLineNumber, 0));
      
      // Scroll to make the line visible if needed
      editor_.scrollToLine(aceLineNumber, true);
   }
   
   /**
    * Position cursor at a specific section by its ID
    */
   private void positionAtSection(String sectionId)
   {
      if (sectionId == null) {
         return;
      }
      
      // Find the section in the current diff sections
      DiffSection section = null;
      for (DiffSection s : diffSections_) {
         if (s.sectionId.equals(sectionId)) {
            section = s;
            break;
         }
      }
      
      if (section != null) {
         int aceLineNumber = section.startLine;
         editor_.setCursorPosition(Position.create(aceLineNumber, 0));
         editor_.scrollToLine(aceLineNumber, true);
      }
   }
   
   /**
    * Create a marker in the undo stack for accept operations
    * This creates minimal edits that increment the revision without visible changes
    */
   private void createUndoMarker()
   {
      int revisionBefore = getDocumentRevision();
      Position pos = editor_.getCursorPosition();
      int scrollTop = editor_.getScrollTop();
      int scrollLeft = editor_.getScrollLeft();
      
      // Start a new undo group
      editor_.getSession().getUndoManager().startNewGroup();
      
      // Insert a zero-width space and immediately remove it
      // This creates undo entries that are invisible but trackable
      Position insertPos = Position.create(0, 0);
      editor_.getSession().getDocument().insert(insertPos, "\u200B");
      editor_.getSession().getDocument().remove(
         Range.fromPoints(insertPos, Position.create(0, 1))
      );
      
      int revisionAfter = getDocumentRevision();
      
      // Restore cursor and scroll position immediately
      Scheduler.get().scheduleDeferred(() -> {
         editor_.setCursorPosition(pos);
         editor_.scrollToY(scrollTop, 0);
         editor_.scrollToX(scrollLeft);
      });
   }
   
   /**
    * Handle undo/redo operations to sync metadata state
    */
   private void handleUndoRedo(boolean isRedo)
   {
      int currentRevision = getDocumentRevision();
      
      if (isRedo)
      {
         executeMetadataActionsForRedo(previousRevision_, currentRevision);
      }
      else
      {
         executeMetadataActionsForUndo(previousRevision_, currentRevision);
      }
      
      previousRevision_ = currentRevision;
   }
   
   /**
    * Handle document changes to detect when redo stack is destroyed
    */
   private void handleDocumentChange()
   {
      int currentRevision = getDocumentRevision();
      
      if (currentRevision > previousRevision_)
      {
         cleanupMetadataActionsAfterRevision(previousRevision_);
      }
      
      previousRevision_ = currentRevision;
   }
   
   /**
    * Execute metadata undo actions when undoing past their recorded revisions
    */
   private void executeMetadataActionsForUndo(int fromRevision, int toRevision)
   {
      boolean hasChanges = false;
      String affectedSectionId = null;
      
      for (int i = metadataActions_.size() - 1; i >= 0; i--)
      {
         MetadataUndoAction action = metadataActions_.get(i);
         
         // When undoing FROM fromRevision TO toRevision, we want to revert actions
         // that were recorded in the range (toRevision, fromRevision]
         if (action.documentRevision > toRevision && 
             action.documentRevision <= fromRevision)
         {
            if (action.type == MetadataUndoAction.Type.ACCEPT)
            {
               AutoAcceptTracker tracker = AutoAcceptTracker.getInstance();
               tracker.updateAcceptedContent(filePath_, action.previousAcceptedContent);
            }
            else if (action.type == MetadataUndoAction.Type.REJECT)
            {
               // For reject, ACE's undo already reverted the document change
               // We just need to track which section was affected for positioning
            }
            
            hasChanges = true;
            affectedSectionId = action.sectionId;
         }
      }
      
      if (hasChanges)
      {
         final String sectionToFocus = affectedSectionId;
         Scheduler.get().scheduleDeferred(() -> {
            refreshAutoAcceptDiffs(() -> {
               // After refresh, position at the section that was affected
               positionAtSection(sectionToFocus);
            });
         });
      }
   }
   
   /**
    * Execute metadata redo actions when redoing forward through revisions
    */
   private void executeMetadataActionsForRedo(int fromRevision, int toRevision)
   {
      boolean hasChanges = false;
      String affectedSectionId = null;
      
      for (int i = 0; i < metadataActions_.size(); i++)
      {
         MetadataUndoAction action = metadataActions_.get(i);
         
         if (action.documentRevision > fromRevision && 
             action.documentRevision <= toRevision)
         {
            if (action.type == MetadataUndoAction.Type.ACCEPT)
            {
               AutoAcceptTracker tracker = AutoAcceptTracker.getInstance();
               tracker.updateAcceptedContent(filePath_, action.newAcceptedContent);
            }
            else if (action.type == MetadataUndoAction.Type.REJECT)
            {
               // For reject, ACE's redo already reapplied the document change
               // We just need to track which section was affected for positioning
            }
            
            hasChanges = true;
            affectedSectionId = action.sectionId;
         }
      }
      
      if (hasChanges)
      {
         final String sectionToFocus = affectedSectionId;
         Scheduler.get().scheduleDeferred(() -> {
            refreshAutoAcceptDiffs(() -> {
               // After refresh, position at the section that was affected
               positionAtSection(sectionToFocus);
            });
         });
      }
   }
   
   /**
    * Clean up metadata actions when user makes new edits (destroys redo stack)
    */
   private void cleanupMetadataActionsAfterRevision(int revision)
   {
      int sizeBefore = metadataActions_.size();
      metadataActions_.removeIf(action -> action.documentRevision > revision);
   }
   
   /**
    * Dispose and clean up resources
    */
   public void dispose()
   {
      if (undoRedoHandler_ != null)
      {
         undoRedoHandler_.removeHandler();
         undoRedoHandler_ = null;
      }
      
      if (documentChangedHandler_ != null)
      {
         documentChangedHandler_.removeHandler();
         documentChangedHandler_ = null;
      }
      
      metadataActions_.clear();
      deactivate();
   }
   
   private void refreshAutoAcceptDiffs()
   {
      refreshAutoAcceptDiffs(null);
   }
   
   private void refreshAutoAcceptDiffs(Runnable onComplete)
   {
      if (filePath_ == null || filePath_.isEmpty()) {
         if (onComplete != null) onComplete.run();
         return;
      }
      
      // Get tracking info from Java memory
      AutoAcceptTracker tracker = AutoAcceptTracker.getInstance();
      AutoAcceptTracker.FileTrackingInfo trackingInfo = tracker.getTrackingInfo(filePath_);
      
      if (trackingInfo == null) {
         clearAll();
         if (onComplete != null) onComplete.run();
         return;
      }
      
      // Get current content from editor
      String currentContent = editor_.getCode();
      String acceptedContent = trackingInfo.getAcceptedContent();
      
      // Validate and potentially remove tracking if content matches
      tracker.validateTracking(filePath_, currentContent);
      
      // Re-check after validation
      if (!tracker.isTracking(filePath_)) {
         clearAll();
         if (onComplete != null) onComplete.run();
         return;
      }
      
      // Special case: if accepted content is null or empty, treat as empty string
      if (acceptedContent == null) {
         acceptedContent = "";
      }
      
      // Compute diff in Java
      LineDiffComputer.DiffResult diffResult = LineDiffComputer.computeDiff(acceptedContent, currentContent);
      
      if (diffResult.diff.isEmpty()) {
         tracker.removeTracking(filePath_);
         clearAll();
         if (onComplete != null) onComplete.run();
         return;
      }
      
      // Compute sections
      sectionInfoMap_ = AutoAcceptSectionComputer.computeSections(diffResult.diff, acceptedContent, currentContent);
      
      // Process the diff with callback
      processDiffData(diffResult.diff, onComplete);
   }
   
   private void processDiffData(List<LineDiffComputer.DiffEntry> diffEntries, Runnable onComplete)
   {
      if (diffEntries == null || diffEntries.isEmpty()) {
         clearAll();
         if (onComplete != null) onComplete.run();
         return;
      }
      
      // Convert to internal DiffEntry format
      List<DiffEntry> entries = new ArrayList<>();
      for (LineDiffComputer.DiffEntry diff : diffEntries) {
         DiffEntry entry = new DiffEntry();
         entry.type = diff.type;
         entry.content = diff.content;
         entry.oldLine = diff.oldLine;
         entry.newLine = diff.newLine;
         entries.add(entry);
      }
      
      // Identify contiguous sections
      identifyDiffSections(entries);
      
      // Apply visual indicators
      Scheduler.get().scheduleDeferred(() -> {
         clearAll();
         applyGreenHighlighting();
         createSectionWidgets();
         
         if (floatingBar_ != null) {
            floatingBar_.updateSectionCount(diffSections_.size());
         }
         
         // Call completion callback after all visual updates are done
         if (onComplete != null) {
            onComplete.run();
         }
      });
   }
   
   /**
    * Identify contiguous blocks of changes as sections
    * Matches VSCode's section identification exactly
    */
   private void identifyDiffSections(List<DiffEntry> entries)
   {
      diffSections_.clear();
      
      DiffSection currentSection = null;
      
      for (DiffEntry entry : entries) {
         if ("unchanged".equals(entry.type)) {
            // Unchanged line breaks the section
            // Finalize the current section if it exists
            if (currentSection != null) {
               finalizeSectionId(currentSection);
            }
            currentSection = null;
            continue;
         }
         
         // Check if we need to start a new section
         if (currentSection == null) {
            int startLine = entry.newLine > 0 ? entry.newLine : entry.oldLine;
            currentSection = new DiffSection();
            currentSection.startLine = startLine;
            currentSection.endLine = currentSection.startLine;
            diffSections_.add(currentSection);
         }
         
         if ("added".equals(entry.type) && entry.newLine > 0) {
            currentSection.addedLines.add(entry.newLine);
            currentSection.endLine = entry.newLine;
            currentSection.widgetLineNumber = entry.newLine;
         } else if ("deleted".equals(entry.type)) {
            currentSection.deletedLines.add(entry.content);
            // If no added lines yet, place widget at the deletion point
            if (currentSection.widgetLineNumber == 0 && entry.oldLine > 0) {
               currentSection.widgetLineNumber = entry.oldLine;
            }
         }
      }
      
      // Finalize the last section if it exists
      if (currentSection != null) {
         finalizeSectionId(currentSection);
      }
   }
   
   /**
    * Create deterministic section ID matching VSCode format: section-{type}-{startLine}-{endLine}
    */
   private void finalizeSectionId(DiffSection section)
   {
      String sectionType;
      if (!section.addedLines.isEmpty() && !section.deletedLines.isEmpty()) {
         sectionType = "combined";
      } else if (!section.addedLines.isEmpty()) {
         sectionType = "added-only";
      } else {
         sectionType = "deleted-only";
      }
      
      section.sectionId = "section-" + sectionType + "-" + section.startLine + "-" + section.endLine;
   }
   
   /**
    * Apply green background to added lines using ACE markers
    */
   private void applyGreenHighlighting()
   {
      for (DiffSection section : diffSections_) {
         for (Integer line : section.addedLines) {
            int markerId = addLineMarker(line - 1, "erdos-ai-auto-accept-added-line");
            if (markerId >= 0) {
               lineMarkerIds_.add(markerId);
            }
         }
      }
   }
   
   /**
    * Add a full-line marker to the editor
    */
   private int addLineMarker(int line, String className)
   {
      // For fullLine markers, use the same line for start and end
      // Using line+1 would cause it to highlight into the next line
      return editor_.getSession().addMarker(
         Range.fromPoints(
            Position.create(line, 0),
            Position.create(line, Integer.MAX_VALUE)
         ),
         className,
         "fullLine",
         false
      );
   }
   
   /**
    * Get the chunk background color using DomUtils.extractCssValue()
    * Creates nested divs to match .ace_marker-layer .ace_foreign_line selector
    * This is the same approach used by PanmirrorThemeCreator
    */
   private String getChunkBackgroundColor()
   {
      JsArrayString classes = JsArrayString.createArray().cast();
      classes.push("ace_marker-layer");
      classes.push("ace_foreign_line");
      return DomUtils.extractCssValue(classes, "backgroundColor");
   }
   
   /**
    * Add native JavaScript click handler to button element
    */
   private native void addNativeClickHandler(com.google.gwt.dom.client.Element element, String sectionId, int lineNumber, boolean isAccept) /*-{
      var self = this;
      
      var clickHandler = function(event) {
         // Focus editor and set cursor to the line where the button is
         self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptDiffManager::focusEditorAtLine(I)(lineNumber);
         
         if (isAccept) {
            self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptDiffManager::acceptSection(Ljava/lang/String;)(sectionId);
         } else {
            self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptDiffManager::rejectSection(Ljava/lang/String;)(sectionId);
         }
         
         event.preventDefault();
         event.stopPropagation();
      };
      
      element.addEventListener('click', clickHandler, false);
   }-*/;
   
   /**
    * Create view zone widgets with deleted lines and Accept/Reject buttons
    */
   private void createSectionWidgets()
   {
      for (DiffSection section : diffSections_) {
         if (section.deletedLines.isEmpty() && section.addedLines.isEmpty()) {
            continue;
         }
         
         // Create widget content
         FlowPanel container = new FlowPanel();
         container.setStyleName("erdos-ai-auto-accept-section-widget");
         
         // Check if this line is in a code chunk
         int aceLineNumber = section.widgetLineNumber - 1; // ACE uses 0-based
         boolean inCodeChunk = false;
         AceBackgroundHighlighter highlighter = editor_.getBackgroundHighlighter();
         if (highlighter != null && highlighter.isEnabled()) {
            inCodeChunk = highlighter.isRowInCodeChunk(aceLineNumber);
         }
         
         // Apply code chunk styling if applicable
         if (inCodeChunk) {
            container.addStyleName("in-code-chunk");
            // Apply the chunk background color inline
            String chunkBg = getChunkBackgroundColor();
            if (chunkBg != null) {
               container.getElement().getStyle().setBackgroundColor(chunkBg);
            }
         }
         
         // Add deleted lines if any
         if (!section.deletedLines.isEmpty()) {
            FlowPanel deletedContainer = new FlowPanel();
            deletedContainer.setStyleName("erdos-ai-auto-accept-deleted-lines");
            
            ConsoleResources.ConsoleStyles consoleStyles = 
               ConsoleResources.INSTANCE.consoleStyles();
            
            for (String line : section.deletedLines) {
               HTML lineElement = new HTML(line.isEmpty() ? "&nbsp;" : line);
               lineElement.addStyleName(consoleStyles.output());
               // Don't set background color inline - let CSS handle it with theming
               lineElement.getElement().getStyle().setPadding(0, Style.Unit.PX);
               lineElement.getElement().getStyle().setPaddingLeft(4, Style.Unit.PX);
               lineElement.getElement().getStyle().setMargin(0, Style.Unit.PX);
               lineElement.getElement().getStyle().setProperty("display", "block");
               deletedContainer.add(lineElement);
            }
            
            container.add(deletedContainer);
         }
         
         // Add Accept/Reject buttons below the deleted lines
         FlowPanel buttonContainer = new FlowPanel();
         buttonContainer.setStyleName("erdos-ai-auto-accept-buttons");
         
         // Apply code chunk class to button container
         if (inCodeChunk) {
            buttonContainer.addStyleName("in-code-chunk");
            // Apply the chunk background color inline
            String chunkBg = getChunkBackgroundColor();
            if (chunkBg != null) {
               buttonContainer.getElement().getStyle().setBackgroundColor(chunkBg);
            }
         }
         
         Button acceptButton = new Button("Accept");
         acceptButton.setStyleName("erdos-ai-auto-accept-accept-button");
         if (inCodeChunk) {
            acceptButton.addStyleName("in-code-chunk");
         }
         final String sectionId = section.sectionId;
         
         // Add native click handler - pass the ACE line number for focusing
         addNativeClickHandler(acceptButton.getElement(), sectionId, aceLineNumber, true);
         
         Button rejectButton = new Button("Reject");
         rejectButton.setStyleName("erdos-ai-auto-accept-reject-button");
         if (inCodeChunk) {
            rejectButton.addStyleName("in-code-chunk");
         }
         
         // Add native click handler - pass the ACE line number for focusing
         addNativeClickHandler(rejectButton.getElement(), sectionId, aceLineNumber, false);
         
         buttonContainer.add(acceptButton);
         buttonContainer.add(rejectButton);
         container.add(buttonContainer);
         
         // Create pinned widget at the section's widget line
         
         PinnedLineWidget widget = new PinnedLineWidget(
            "auto-accept-section",
            editor_,
            container,
            aceLineNumber,
            null,
            null
         );
         
         sectionWidgets_.put(section.sectionId, widget);
      }
   }
   
   /**
    * Accept a specific section
    */
   private void acceptSection(String sectionId)
   {
      // Find the section
      DiffSection section = null;
      for (DiffSection s : diffSections_) {
         if (s.sectionId.equals(sectionId)) {
            section = s;
            break;
         }
      }
      
      if (section == null) {
         // Clear current state
         clearAll();
         
         // Check if we still have tracking for this file
         AutoAcceptTracker tracker = AutoAcceptTracker.getInstance();
         AutoAcceptTracker.FileTrackingInfo trackingInfo = tracker.getTrackingInfo(filePath_);
         
         if (trackingInfo == null) {
            hideFloatingBar();
            return;
         }
         
         // Check if content still differs
         String currentContent = editor_.getCode();
         String acceptedContent = trackingInfo.getAcceptedContent();
         
         if (currentContent != null && currentContent.equals(acceptedContent)) {
            tracker.removeTracking(filePath_);
            hideFloatingBar();
            return;
         }
         
         // Recompute diffs - editor is already focused from button click
         refreshAutoAcceptDiffs();
         return;
      }
      
      // For accept: update accepted_content to match current for this section
      acceptSectionChanges(section);
   }
   
   /**
    * Apply the accepted changes for a specific section
    */
   private void acceptSectionChanges(DiffSection section)
   {
      String sectionId = section.sectionId;
      
      // Get section info
      AutoAcceptSectionComputer.SectionInfo sectionInfo = sectionInfoMap_.get(sectionId);
      if (sectionInfo == null) {
         return;
      }
      
      // Get tracker and current info
      AutoAcceptTracker tracker = AutoAcceptTracker.getInstance();
      AutoAcceptTracker.FileTrackingInfo trackingInfo = tracker.getTrackingInfo(filePath_);
      if (trackingInfo == null) {
         return;
      }
      
      String previousAcceptedContent = trackingInfo.getAcceptedContent();
      
      // Apply section changes to accepted content
      String updatedAcceptedContent = AutoAcceptSectionComputer.acceptSection(
         previousAcceptedContent,
         sectionInfo
      );
      
      // Create a marker edit in the undo stack so this accept becomes undoable
      // Insert and immediately remove a zero-width space to create an undo entry
      createUndoMarker();
      
      // Record metadata change for undo/redo at the NEW revision after the marker
      int currentRevision = getDocumentRevision();
      metadataActions_.add(new MetadataUndoAction(
         currentRevision,
         previousAcceptedContent,
         updatedAcceptedContent,
         sectionId
      ));
      
      // Update tracking
      tracker.updateAcceptedContent(filePath_, updatedAcceptedContent);
      
      // Remove the visual indicators
      removeSection(sectionId);
      
      // Refresh to recalculate diffs - editor is already focused at the right line
      refreshAutoAcceptDiffs();
      
      // If no more sections, clear all tracking
      if (diffSections_.isEmpty()) {
         tracker.removeTracking(filePath_);
         deactivate();
      }
   }
   
   /**
    * Reject a specific section
    */
   private void rejectSection(String sectionId)
   {
      // Find the section
      DiffSection section = null;
      for (DiffSection s : diffSections_) {
         if (s.sectionId.equals(sectionId)) {
            section = s;
            break;
         }
      }
      
      if (section == null) {
         // Clear current state
         clearAll();
         
         // Check if we still have tracking for this file
         AutoAcceptTracker tracker = AutoAcceptTracker.getInstance();
         AutoAcceptTracker.FileTrackingInfo trackingInfo = tracker.getTrackingInfo(filePath_);
         
         if (trackingInfo == null) {
            hideFloatingBar();
            return;
         }
         
         // Check if content still differs
         String currentContent = editor_.getCode();
         String acceptedContent = trackingInfo.getAcceptedContent();
         
         if (currentContent != null && currentContent.equals(acceptedContent)) {
            tracker.removeTracking(filePath_);
            hideFloatingBar();
            return;
         }
         
         // Recompute diffs - editor is already focused from button click
         refreshAutoAcceptDiffs();
         return;
      }
      
      // For reject: need to remove added lines and restore deleted lines
      // This requires modifying the actual file content
      revertSectionChanges(section);
   }
   
   /**
    * Revert the changes for a specific section
    */
   private void revertSectionChanges(DiffSection section)
   {
      String sectionId = section.sectionId;
      
      // Get section info
      AutoAcceptSectionComputer.SectionInfo sectionInfo = sectionInfoMap_.get(sectionId);
      if (sectionInfo == null) {
         return;
      }
      
      // Get current content
      String currentContent = editor_.getCode();
      
      // Apply rejection (revert changes)
      String revertedContent = AutoAcceptSectionComputer.rejectSection(currentContent, sectionInfo);
      
      // Update editor - use preserveCursorPosition=true to preserve undo stack
      // This allows the rejection to be undoable via Cmd+Z
      editor_.setCode(revertedContent, true);
      
      // Get revision after the reject and record metadata
      int revisionAfter = getDocumentRevision();
      
      // Record reject metadata action for undo/redo positioning
      metadataActions_.add(MetadataUndoAction.createReject(revisionAfter, sectionId));
      
      // Remove the visual indicators
      removeSection(sectionId);
      
      // Refresh to recalculate diffs - editor is already focused at the right line
      refreshAutoAcceptDiffs();
      
      // If no more sections, clear all tracking
      if (diffSections_.isEmpty()) {
         AutoAcceptTracker.getInstance().removeTracking(filePath_);
         deactivate();
      }
   }
   
   /**
    * Remove a section's visual indicators
    */
   private void removeSection(String sectionId)
   {
      // Remove widget
      PinnedLineWidget widget = sectionWidgets_.remove(sectionId);
      if (widget != null) {
         widget.detach();
      }
      
      // Remove from sections list
      diffSections_.removeIf(s -> s.sectionId.equals(sectionId));
      
      // Update floating bar
      if (floatingBar_ != null) {
         floatingBar_.updateSectionCount(diffSections_.size());
      }
   }
   
   /**
    * Accept all changes and clear tracking
    */
   public void acceptAllChanges()
   {
      // Simply remove tracking - current content is now the accepted state
      AutoAcceptTracker.getInstance().removeTracking(filePath_);
      deactivate();
   }
   
   /**
    * Reject all changes and revert file
    */
   public void rejectAllChanges()
   {
      AutoAcceptTracker tracker = AutoAcceptTracker.getInstance();
      AutoAcceptTracker.FileTrackingInfo trackingInfo = tracker.getTrackingInfo(filePath_);
      
      if (trackingInfo != null) {
         // Revert editor to accepted content - preserve undo stack so this can be undone
         editor_.setCode(trackingInfo.getAcceptedContent(), true);
         
         // Remove tracking
         tracker.removeTracking(filePath_);
      }
      
      deactivate();
   }
   
   private void clearAll()
   {
      // Remove all widgets
      for (Map.Entry<String, PinnedLineWidget> entry : sectionWidgets_.entrySet()) {
         entry.getValue().detach();
      }
      sectionWidgets_.clear();
      
      // Remove all line markers
      for (Integer markerId : lineMarkerIds_) {
         editor_.getSession().removeMarker(markerId);
      }
      lineMarkerIds_.clear();
      
      // Note: diffSections_ is NOT cleared here - it contains the current section data needed for widget creation
      // Note: sectionInfoMap_ is managed by refreshAutoAcceptDiffs() and should not be cleared here
   }
   
   private void showFloatingBar()
   {
      if (floatingBar_ == null) {
         floatingBar_ = new AutoAcceptFloatingBar(editor_, this, filePath_);
      }
      floatingBar_.show();
   }
   
   private void hideFloatingBar()
   {
      if (floatingBar_ != null) {
         floatingBar_.hide();
      }
   }
   
}

