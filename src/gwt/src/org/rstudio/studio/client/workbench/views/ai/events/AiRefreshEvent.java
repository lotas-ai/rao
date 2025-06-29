/*
 * AiRefreshEvent.java
 *
 * Copyright (C) 2025 by William Nickols
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.ai.events;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class AiRefreshEvent extends GwtEvent<AiRefreshEvent.Handler>
{
   public interface Handler extends EventHandler
   {
      void onAiRefreshEvent(AiRefreshEvent event);
   }

   public AiRefreshEvent()
   {
      data_ = "";
   }

   public AiRefreshEvent(String data)
   {
      data_ = data;
   }

   public String getData()
   {
      return data_;
   }

   @Override
   public Type<Handler> getAssociatedType()
   {
      return TYPE;
   }

   @Override
   protected void dispatch(Handler handler)
   {
      handler.onAiRefreshEvent(this);
   }

   public static final Type<Handler> TYPE = new Type<>();
   private final String data_;
} 