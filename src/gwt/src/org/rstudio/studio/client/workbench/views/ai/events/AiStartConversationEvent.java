/*
 * AiStartConversationEvent.java
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

public class AiStartConversationEvent extends GwtEvent<AiStartConversationEvent.Handler>
{
   public static final Type<Handler> TYPE = new Type<>();

   public interface Handler extends EventHandler
   {
      void onAiStartConversation(AiStartConversationEvent event);
   }

   public static class Data extends JavaScriptObject
   {
      protected Data()
      {
      }

      public final native String getUserMessage() /*-{
         return this.userMessage || "";
      }-*/;

      public final native String getAssistantMessageId() /*-{
         return this.assistantMessageId || "";
      }-*/;
   }

   public AiStartConversationEvent(Data data)
   {
      data_ = data;
   }

   public String getUserMessage()
   {
      return data_.getUserMessage();
   }

   public String getAssistantMessageId()
   {
      return data_.getAssistantMessageId();
   }

   @Override
   public Type<Handler> getAssociatedType()
   {
      return TYPE;
   }

   @Override
   protected void dispatch(Handler handler)
   {
      handler.onAiStartConversation(this);
   }

   private final Data data_;
} 