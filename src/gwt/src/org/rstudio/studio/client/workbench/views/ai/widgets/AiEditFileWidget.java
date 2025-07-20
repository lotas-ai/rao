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
public class AiEditFileWidget extends AiFileEditorWidgetBase
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
      this(messageId, filename, content, explanation, requestId, isEditable, handler, isCancelled, false);
   }
   
   public AiEditFileWidget(String messageId, 
                          String filename,
                          String content, 
                          String explanation,
                          String requestId,
                          boolean isEditable,
                          EditFileCommandHandler handler,
                          boolean isCancelled,
                          boolean skipDiffHighlighting)
   {
      this(messageId, filename, content, explanation, requestId, isEditable, handler, isCancelled, skipDiffHighlighting, null);
   }
   
   // NEW CONSTRUCTOR: Accepts pre-computed diffData from R 
   public AiEditFileWidget(String messageId, 
                          String filename,
                          String content, 
                          String explanation,
                          String requestId,
                          boolean isEditable,
                          EditFileCommandHandler handler,
                          boolean isCancelled,
                          boolean skipDiffHighlighting,
                          com.google.gwt.core.client.JavaScriptObject diffData)
   {
      super(messageId, requestId, filename, explanation, isEditable, isCancelled, skipDiffHighlighting, diffData);
      handler_ = handler;
      
      initWidget(createWidget(content, filename));
      addStyleName("aiEditFileWidget");
      
      // Only apply diff highlighting if not cancelled and not skipped
      if (!isCancelled_ && !skipDiffHighlighting_) {
         // Apply diff highlighting immediately
         com.google.gwt.core.client.Scheduler.get().scheduleDeferred(() -> {
            if (editor_ != null && preComputedDiffData_ != null) {
               applyDiffHighlighting();
            }
         });
      }
   }
   
   // Implement abstract methods for EditFile-specific styling
   
   @Override
   protected String getHeaderStyleClass() {
      return "aiEditFileHeader";
   }
   
   @Override
   protected String getButtonContainerStyleClass() {
      return "aiEditFileButtons";
   }
   
   @Override
   protected String getAcceptButtonStyleClass() {
      return "aiEditFileAcceptButton";
   }
   
   @Override
   protected String getCancelButtonStyleClass() {
      return "aiEditFileCancelButton";
   }
   
   @Override
   protected String getEditorIdPrefix() {
      return "ai-edit-file-editor-";
   }
   
   
   private native Element querySelector(Element element, String selector) /*-{
      return element.querySelector(selector);
   }-*/;
   
   /**
    * Create a label with colored diff stats (+X in green, -Y in red)
    */

   

   
   // Implement abstract methods from AiWidgetBase
   
   @Override
   protected Button[] getStandardButtons() {
      return new Button[] { acceptButton_, cancelButton_ };
   }
   
   @Override
   protected void onAcceptClicked()
   {
      if (handler_ != null)
      {
         String editedContent = getContent();
         handler_.onAccept(getMessageId(), editedContent);
         
         // Disable buttons during execution
         setButtonsEnabled(false);
      }
      else
      {
         Debug.log("DEBUG: Handler is null! This is the problem.");
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
      else
      {
         Debug.log("DEBUG: Handler is null! This is the problem.");
      }
   }
   
   @Override
   public void hideButtons() {
      // For cancelled edits, buttons don't exist, so nothing to hide
      if (isCancelled_) {
         return;
      }
      
      // Use base class functionality
      hideButtonsInternal();
   }
   
   private final EditFileCommandHandler handler_;
} 