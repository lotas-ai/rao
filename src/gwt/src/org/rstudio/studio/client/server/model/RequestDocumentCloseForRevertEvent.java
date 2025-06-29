/*
 * RequestDocumentCloseForRevertEvent.java
 *
 * Copyright (C) 2025 by William Nickols
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
package org.rstudio.studio.client.server.model;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class RequestDocumentCloseForRevertEvent extends GwtEvent<RequestDocumentCloseForRevertEvent.Handler>
{
   public static class Data extends JavaScriptObject
   {
      protected Data()
      {
      }

      public native final String getFilePath()
      /*-{
         return this["file_path"];
      }-*/;

   }

   public RequestDocumentCloseForRevertEvent(Data data)
   {
      data_ = data;
   }

   public String getFilePath()
   {
      return data_.getFilePath();
   }

   private final Data data_;

   // Boilerplate ----

   public interface Handler extends EventHandler
   {
      void onRequestDocumentCloseForRevert(RequestDocumentCloseForRevertEvent event);
   }

   @Override
   public Type<Handler> getAssociatedType()
   {
      return TYPE;
   }

   @Override
   protected void dispatch(Handler handler)
   {
      handler.onRequestDocumentCloseForRevert(this);
   }

   public static final Type<Handler> TYPE = new Type<>();
} 