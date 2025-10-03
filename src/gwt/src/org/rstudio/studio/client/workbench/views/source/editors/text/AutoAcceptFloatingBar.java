/*
 * AutoAcceptFloatingBar.java
 *
 * Copyright (C) 2025 by Lotas Inc.
 *
 */
package org.rstudio.studio.client.workbench.views.source.editors.text;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Floating bar for navigating and managing auto-accepted changes
 */
public class AutoAcceptFloatingBar extends Composite
{
   interface Resources extends ClientBundle
   {
      @Source("AutoAcceptFloatingBar.css")
      Styles styles();
   }
   
   interface Styles extends CssResource
   {
      String floatingBar();
      String reviewMode();
      String sectionNav();
      String fileNav();
      String chevron();
      String rightChevron();
      String actionButton();
      String counter();
   }
   
   private static final Resources RES = GWT.create(Resources.class);
   static
   {
      RES.styles().ensureInjected();
   }
   private final AceEditor editor_;
   private final AutoAcceptDiffManager diffManager_;
   private final String filePath_;
   
   private FlowPanel mainPanel_;
   private FlowPanel sectionNav_;
   private HTML prevSectionButton_;
   private Label sectionCounterLabel_;
   private HTML nextSectionButton_;
   private Button undoButton_;
   private Button keepButton_;
   private FlowPanel fileNav_;
   private HTML prevFileButton_;
   private Label fileCounterLabel_;
   private HTML nextFileButton_;
   private HTML reviewLabel_;
   
   private int sectionCount_ = 0;
   private int currentSectionIndex_ = 0;
   private boolean isInReviewMode_ = false;
   
   public AutoAcceptFloatingBar(AceEditor editor, AutoAcceptDiffManager diffManager, String filePath)
   {
      editor_ = editor;
      diffManager_ = diffManager;
      filePath_ = filePath;
      
      mainPanel_ = new FlowPanel();
      mainPanel_.addStyleName(RES.styles().floatingBar());
      
      // Section navigation (only shown if >1 sections)
      sectionNav_ = new FlowPanel();
      sectionNav_.addStyleName(RES.styles().sectionNav());
      
      prevSectionButton_ = new HTML(createChevronSvg("up"));
      prevSectionButton_.addStyleName(RES.styles().chevron());
      
      sectionCounterLabel_ = new Label("1 / 1");
      sectionCounterLabel_.addStyleName(RES.styles().counter());
      
      nextSectionButton_ = new HTML(createChevronSvg("down"));
      nextSectionButton_.addStyleName(RES.styles().chevron());
      
      sectionNav_.add(prevSectionButton_);
      sectionNav_.add(sectionCounterLabel_);
      sectionNav_.add(nextSectionButton_);
      
      // Action buttons
      undoButton_ = new Button("Undo");
      undoButton_.addStyleName(RES.styles().actionButton());
      undoButton_.addClickHandler(e -> undoCurrentFile());
      
      keepButton_ = new Button("Keep");
      keepButton_.addStyleName(RES.styles().actionButton());
      keepButton_.addClickHandler(e -> keepCurrentFile());
      
      // File navigation (only shown if >1 files)
      fileNav_ = new FlowPanel();
      fileNav_.addStyleName(RES.styles().fileNav());
      
      prevFileButton_ = new HTML(createChevronSvg("left"));
      prevFileButton_.addStyleName(RES.styles().chevron());
      
      fileCounterLabel_ = new Label("1 / 1");
      fileCounterLabel_.addStyleName(RES.styles().counter());
      
      nextFileButton_ = new HTML(createChevronSvg("right"));
      nextFileButton_.addStyleName(RES.styles().chevron());
      nextFileButton_.addStyleName(RES.styles().rightChevron());
      
      fileNav_.add(prevFileButton_);
      fileNav_.add(fileCounterLabel_);
      fileNav_.add(nextFileButton_);
      
      // Review label (shown when entire bar is in review mode)
      // Use inline SVG like other AI widgets do, not codicon CSS classes
      String chevronRightSvg = "<svg width='12' height='12' viewBox='0 0 24 24' style='position: relative; top: 3px; margin-left: 1px;' fill='currentColor'>" +
                               "<path d='M16 10L10 16L12 18L20 10L12 2L10 4Z'/>" +
                               "</svg>";
      reviewLabel_ = new HTML("Review next file " + chevronRightSvg);
      reviewLabel_.setVisible(false);
      
      mainPanel_.add(sectionNav_);
      mainPanel_.add(undoButton_);
      mainPanel_.add(keepButton_);
      mainPanel_.add(fileNav_);
      mainPanel_.add(reviewLabel_);
            
      initWidget(mainPanel_);
      
      attachToEditor();
      
      setupNativeClickHandlers();
   }
   
   private void attachToEditor()
   {
      AceEditorWidget widget = editor_.getWidget();
      if (widget == null)
      {
         return;
      }
      
      Element aceScroller = DomUtils.getFirstElementWithClassName(
            widget.getElement(),
            "ace_scroller");
      
      if (aceScroller == null)
      {
         return;
      }
      
      aceScroller.appendChild(getElement());
      
      // Ensure pointer events work
      getElement().getStyle().setProperty("pointerEvents", "auto");
   }
   
   /**
    * Set up native click handlers like AI widgets do
    */
   private void setupNativeClickHandlers()
   {
      addNativeClickHandler(undoButton_.getElement(), "undo");
      addNativeClickHandler(keepButton_.getElement(), "keep");
      addNativeClickHandler(prevSectionButton_.getElement(), "prevSection");
      addNativeClickHandler(nextSectionButton_.getElement(), "nextSection");
      addNativeClickHandler(prevFileButton_.getElement(), "prevFile");
      addNativeClickHandler(nextFileButton_.getElement(), "nextFile");
      addNativeClickHandler(mainPanel_.getElement(), "reviewNextFile");
   }
   
   /**
    * Native JavaScript click handler following AI widget pattern
    */
   private native void addNativeClickHandler(Element element, String action) /*-{
      var self = this;
      
      var clickHandler = function(event) {
         
         if (action === 'undo') {
            self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptFloatingBar::undoCurrentFile()();
         } else if (action === 'keep') {
            self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptFloatingBar::keepCurrentFile()();
         } else if (action === 'prevSection') {
            self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptFloatingBar::navigateToPreviousSection()();
         } else if (action === 'nextSection') {
            self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptFloatingBar::navigateToNextSection()();
         } else if (action === 'prevFile') {
            self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptFloatingBar::navigateToPreviousFile()();
         } else if (action === 'nextFile') {
            self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptFloatingBar::navigateToNextFile()();
         } else if (action === 'reviewNextFile') {
            // Only handle click if we're in review mode
            var isInReviewMode = self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptFloatingBar::isInReviewMode_;
            if (isInReviewMode) {
               self.@org.rstudio.studio.client.workbench.views.source.editors.text.AutoAcceptFloatingBar::navigateToNextFileWithChanges()();
               event.preventDefault();
               event.stopPropagation();
            }
            return;
         }
         
         event.preventDefault();
         event.stopPropagation();
      };
      
      element.addEventListener('click', clickHandler, false);
   }-*/;
   
   public void updateSectionCount(int count)
   {
      sectionCount_ = count;
      currentSectionIndex_ = count > 0 ? 1 : 0;
      updateUI();
   }
   
   private void navigateToPreviousSection()
   {
      if (sectionCount_ > 1 && currentSectionIndex_ > 1 && diffManager_ != null)
      {
         currentSectionIndex_--;
         updateUI();
         diffManager_.navigateToSection(currentSectionIndex_ - 1);
      }
   }
   
   private void navigateToNextSection()
   {
      if (sectionCount_ > 1 && currentSectionIndex_ < sectionCount_ && diffManager_ != null)
      {
         currentSectionIndex_++;
         updateUI();
         diffManager_.navigateToSection(currentSectionIndex_ - 1);
      }
   }
   
   private String createChevronSvg(String direction)
   {
      String path;
      String extraStyle = "";
      switch (direction)
      {
         case "up":
            path = "M10 16L4 22L2 20L10 12L18 20L16 22Z";
            extraStyle = " style='margin-top: -4px;'";
            break;
         case "down":
            path = "M10 16L16 10L18 12L10 20L2 12L4 10Z";
            extraStyle = " style='margin-top: -2px;'";
            break;
         case "left":
            // This should point LEFT (used for previous file)
            path = "M16 10L22 4L20 2L12 10L20 18L22 16Z";
            extraStyle = " style='margin-top: 2px;'";
            break;
         case "right":
            // This should point RIGHT (used for next file)
            path = "M16 10L10 16L12 18L20 10L12 2L10 4Z";
            extraStyle = " style='margin-top: 2px;'";
            break;
         default:
            path = "";
      }
      
      return "<svg width='15' height='15' viewBox='0 0 24 24' class='auto-accept-chevron-svg'" + extraStyle + ">" +
             "<path d='" + path + "' fill='currentColor' stroke='currentColor' stroke-width='0.7'/>" +
             "</svg>";
   }
   
   public void show()
   {
      setVisible(true);
   }
   
   public void hide()
   {
      setVisible(false);
   }
   
   
   private void undoCurrentFile()
   {
      if (diffManager_ != null)
      {
         diffManager_.rejectAllChanges();
      }
   }
   
   private void keepCurrentFile()
   {
      if (diffManager_ != null)
      {
         diffManager_.acceptAllChanges();
      }
   }
   
   private void navigateToPreviousFile()
   {
      // Get fresh sorted list
      List<String> filesList = getSortedTrackedFiles();
      if (filesList.isEmpty())
      {
         return;
      }
      
      // Find current file in the list
      int currentIndex = filesList.indexOf(filePath_);
      if (currentIndex == -1)
      {
         return;
      }
      
      // Move to previous file (wrap around)
      int newIndex = (currentIndex - 1 + filesList.size()) % filesList.size();
      String fileToOpen = filesList.get(newIndex);
      
      // Open the file
      RStudioGinjector.INSTANCE.getFileTypeRegistry()
         .editFile(FileSystemItem.createFile(fileToOpen));
   }
   
   private void navigateToNextFile()
   {
      // Get fresh sorted list
      List<String> filesList = getSortedTrackedFiles();
      if (filesList.isEmpty())
      {
         return;
      }
      
      // Find current file in the list
      int currentIndex = filesList.indexOf(filePath_);
      if (currentIndex == -1)
      {
         return;
      }
      
      // Move to next file (wrap around)
      int newIndex = (currentIndex + 1) % filesList.size();
      String fileToOpen = filesList.get(newIndex);
      
      // Open the file
      RStudioGinjector.INSTANCE.getFileTypeRegistry()
         .editFile(FileSystemItem.createFile(fileToOpen));
   }
   
   private void navigateToNextFileWithChanges()
   {
      // Get fresh sorted list
      List<String> filesList = getSortedTrackedFiles();
      if (filesList.isEmpty())
      {
         return;
      }
      
      // Find current file in the list
      int currentIndex = filesList.indexOf(filePath_);
      if (currentIndex == -1)
      {
         // If current file not in list, go to first tracked file
         String fileToOpen = filesList.get(0);
         RStudioGinjector.INSTANCE.getFileTypeRegistry()
            .editFile(FileSystemItem.createFile(fileToOpen));
         return;
      }
      
      // Try to find next file with changes (starting from next file)
      int startIndex = (currentIndex + 1) % filesList.size();
      for (int i = 0; i < filesList.size(); i++)
      {
         int checkIndex = (startIndex + i) % filesList.size();
         String fileToCheck = filesList.get(checkIndex);
         
         // Skip current file
         if (fileToCheck.equals(filePath_))
         {
            continue;
         }
         
         // Open this file (first different file in the list)
         RStudioGinjector.INSTANCE.getFileTypeRegistry()
            .editFile(FileSystemItem.createFile(fileToCheck));
         return;
      }
   }
   
   private void updateUI()
   {
      // Get universal sorted list of tracked files
      List<String> filesList = getSortedTrackedFiles();
      int totalFiles = filesList.size();
      
      // Find where current file is in the universal list
      int currentFileIndex = filesList.indexOf(filePath_);
      
      // If current file has no changes but other files do, enter review mode
      if (sectionCount_ == 0 && totalFiles > 0)
      {
         isInReviewMode_ = true;
         
         // Hide all normal controls
         sectionNav_.setVisible(false);
         undoButton_.setVisible(false);
         keepButton_.setVisible(false);
         fileNav_.setVisible(false);
         
         // Show review label and add review mode style
         reviewLabel_.setVisible(true);
         mainPanel_.addStyleName(RES.styles().reviewMode());
         return;
      }
      
      // Normal state: exit review mode
      isInReviewMode_ = false;
      reviewLabel_.setVisible(false);
      mainPanel_.removeStyleName(RES.styles().reviewMode());
      undoButton_.setVisible(true);
      keepButton_.setVisible(true);
      
      // Section navigation: only visible if >1 sections
      if (sectionCount_ > 1)
      {
         sectionNav_.setVisible(true);
         String sectionText = currentSectionIndex_ + " / " + sectionCount_;
         sectionCounterLabel_.setText(sectionText);
      }
      else
      {
         sectionNav_.setVisible(false);
      }
      
      // File navigation: only visible if >1 files
      if (totalFiles > 1 && currentFileIndex != -1)
      {
         fileNav_.setVisible(true);
         String fileText = (currentFileIndex + 1) + " / " + totalFiles;
         fileCounterLabel_.setText(fileText);
      }
      else
      {
         fileNav_.setVisible(false);
      }
   }
   
   /**
    * Get the universal sorted list of tracked files from the tracker
    */
   private List<String> getSortedTrackedFiles()
   {
      Set<String> filesSet = AutoAcceptTracker.getInstance().getTrackedFiles();
      return new ArrayList<>(filesSet); // TreeSet is already sorted alphabetically
   }
}
