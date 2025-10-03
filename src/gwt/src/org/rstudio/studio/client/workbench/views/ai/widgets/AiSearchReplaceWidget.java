/*
 * AiSearchReplaceWidget.java
 *
 * Copyright (C) 2025 by Lotas Inc.
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
import org.rstudio.core.client.Debug;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Range;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Position;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.core.client.widget.FontSizer;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.ServerError;

/**
 * Widget for displaying search_replace function calls with an Ace editor
 * Specialized for search and replace operations
 */
public class AiSearchReplaceWidget extends AiFileEditorWidgetBase
{
   public interface SearchReplaceCommandHandler
   {
      void onAccept(String messageId, String editedContent);
      void onCancel(String messageId);
   }
   
   public AiSearchReplaceWidget(String messageId, 
                               String filename,
                               String content, 
                               String explanation,
                               String requestId,
                               boolean isEditable,
                               SearchReplaceCommandHandler handler)
   {
      this(messageId, filename, content, explanation, requestId, isEditable, handler, false, false, null);
   }
   
   public AiSearchReplaceWidget(String messageId, 
                               String filename,
                               String content, 
                               String explanation,
                               String requestId,
                               boolean isEditable,
                               SearchReplaceCommandHandler handler,
                               boolean isCancelled,
                               boolean skipDiffHighlighting,
                               com.google.gwt.core.client.JavaScriptObject diffData)
   {
      super(messageId, requestId, "search_replace", filename, explanation, isEditable, isCancelled, skipDiffHighlighting, diffData);
      handler_ = handler;
      
      initWidget(createWidget(content, filename));
      addStyleName("aiSearchReplaceWidget");
      
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
      
   @Override
   protected String getHeaderStyleClass() {
      return "aiSearchReplaceHeader";
   }
   
   @Override
   protected String getButtonContainerStyleClass() {
      return "aiSearchReplaceButtons";
   }
   

   
   @Override
   protected String getEditorIdPrefix() {
      return "ai-search-replace-editor-";
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
      // Enable auto-accept edits mode
      enableAutoAcceptEdits();
      
      // Then execute the current search/replace automatically
      if (handler_ != null) {
         String editedContent = getContent();
         handler_.onAccept(getMessageId(), editedContent);
         setButtonsEnabled(false);
      }
   }

   /**
    * Enable auto-accept edits mode
    */
   private void enableAutoAcceptEdits() {
      AiServerOperations server = RStudioGinjector.INSTANCE.getServer();
      server.setAutoAcceptEdits(true, new ServerRequestCallback<java.lang.Void>() {
         @Override
         public void onResponseReceived(java.lang.Void response) {
            // Auto-accept edits enabled successfully
         }
         
         @Override
         public void onError(ServerError error) {
            // Failed to enable auto-accept edits
         }
      });
   }
   
   @Override
   public void hideButtons() {
      // For cancelled operations, buttons don't exist, so nothing to hide
      if (isCancelled_) {
         return;
      }
      
      // Use base class functionality
      hideVerticalStack();
   }
   
   // SearchReplace-specific fields
   private final SearchReplaceCommandHandler handler_;
}