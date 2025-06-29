/*
 * RefreshDocumentContentEvent.java
 *
 * Copyright (C) 2025 by Posit Software, PBC
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
package org.rstudio.studio.client.workbench.views.source.events;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class RefreshDocumentContentEvent extends GwtEvent<RefreshDocumentContentEvent.Handler>
{
   public interface Handler extends EventHandler
   {
      void onRefreshDocumentContent(RefreshDocumentContentEvent event);
   }

   public RefreshDocumentContentEvent(String documentId, String filePath, String content, boolean markClean)
   {
      documentId_ = documentId;
      filePath_ = filePath;
      content_ = content;
      markClean_ = markClean;
   }
   
   // Backwards compatibility constructor
   public RefreshDocumentContentEvent(String documentId, String filePath, String content)
   {
      this(documentId, filePath, content, true); // Default to marking clean for backwards compatibility
   }

   public String getDocumentId()
   {
      return documentId_;
   }

   public String getFilePath()
   {
      return filePath_;
   }

   public String getContent()
   {
      return content_;
   }

   public boolean shouldMarkClean()
   {
      return markClean_;
   }

   @Override
   protected void dispatch(Handler handler)
   {
      handler.onRefreshDocumentContent(this);
   }

   @Override
   public GwtEvent.Type<Handler> getAssociatedType()
   {
      return TYPE;
   }

   public static final GwtEvent.Type<Handler> TYPE = new GwtEvent.Type<>();

   private final String documentId_;
   private final String filePath_;
   private final String content_;
   private final boolean markClean_;
} 