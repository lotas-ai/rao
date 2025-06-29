/*
 * AiTab.java
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
package org.rstudio.studio.client.workbench.views.ai;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;

import org.rstudio.core.client.command.CommandBinder;
import org.rstudio.core.client.command.Handler;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.events.SessionInitEvent;
import org.rstudio.studio.client.workbench.model.Session;
import org.rstudio.studio.client.workbench.ui.DelayLoadTabShim;
import org.rstudio.studio.client.workbench.ui.DelayLoadWorkbenchTab;
import org.rstudio.studio.client.workbench.views.ai.events.ActivateAiEvent;
import org.rstudio.studio.client.workbench.views.ai.events.ShowAiEvent;

public class AiTab extends DelayLoadWorkbenchTab<Ai>
{
   public abstract static class Shim extends DelayLoadTabShim<Ai, AiTab>
                                     implements ShowAiEvent.Handler,
                                                ActivateAiEvent.Handler
   {
      @Handler public abstract void onAiHome();
      @Handler public abstract void onAiSearch();
      @Handler public abstract void onAiAttach();
      

      public abstract void bringToFront();
   }

   public interface Binder extends CommandBinder<Commands, AiTab.Shim> {}

   @Inject
   public AiTab(final Shim shim,
                  Commands commands,
                  EventBus events,
                  final Session session)
   {
      super(constants_.aiText(), shim);
      ((Binder)GWT.create(Binder.class)).bind(commands, shim);
      events.addHandler(ShowAiEvent.TYPE, shim);
      events.addHandler(ActivateAiEvent.TYPE, shim);

      events.addHandler(SessionInitEvent.TYPE, (SessionInitEvent sie) ->
      {
         if (session.getSessionInfo().getShowAiHome())
         {
            shim.bringToFront();
         }
      });
   }
   private static final AiConstants constants_ = GWT.create(AiConstants.class);
}
