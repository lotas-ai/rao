/*
 * AiLoadConversationEvent.java
 *
 * Copyright (C) 2025 by William Nickols
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 */

package org.rstudio.studio.client.workbench.views.ai.events;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class AiLoadConversationEvent extends GwtEvent<AiLoadConversationEvent.Handler>
{
   public static final Type<Handler> TYPE = new Type<>();

   public interface Handler extends EventHandler
   {
      void onAiLoadConversation(AiLoadConversationEvent event);
   }

   public static class Data extends JavaScriptObject
   {
      protected Data()
      {
      }

      public final native int getConversationId() /*-{
         return this.conversationId || 0;
      }-*/;

      public final native String getConversationName() /*-{
         return this.conversationName || "";
      }-*/;
   }

   public AiLoadConversationEvent(Data data)
   {
      data_ = data;
   }

   public int getConversationId()
   {
      return data_.getConversationId();
   }

   public String getConversationName()
   {
      return data_.getConversationName();
   }

   @Override
   public Type<Handler> getAssociatedType()
   {
      return TYPE;
   }

   @Override
   protected void dispatch(Handler handler)
   {
      handler.onAiLoadConversation(this);
   }

   private final Data data_;
} 