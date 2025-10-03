/*
 * UpdateBufferStatusEvent.java
 *
 * Copyright (C) 2025 by Lotas Inc.
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.ai.events;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class UpdateBufferStatusEvent extends GwtEvent<UpdateBufferStatusEvent.Handler>
{
   public static final Type<Handler> TYPE = new Type<>();

   public interface Handler extends EventHandler
   {
      void onUpdateBufferStatus(UpdateBufferStatusEvent event);
   }

   public static class Data extends JavaScriptObject
   {
      protected Data()
      {
      }

      public final native int getBufferCount() /*-{
         return this.buffer_count || 0;
      }-*/;
      
      public final native boolean isProcessing() /*-{
         return !!this.is_processing;
      }-*/;
   }

   public UpdateBufferStatusEvent(Data data)
   {
      data_ = data;
   }
   
   public int getBufferCount()
   {
      return data_.getBufferCount();
   }
   
   public boolean isProcessing()
   {
      return data_.isProcessing();
   }
   
   @Override
   public Type<Handler> getAssociatedType()
   {
      return TYPE;
   }

   @Override
   protected void dispatch(Handler handler)
   {
      handler.onUpdateBufferStatus(this);
   }

   private final Data data_;
}


