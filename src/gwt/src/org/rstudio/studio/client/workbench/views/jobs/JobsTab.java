/*
 * JobsTab.java
 *
 * Copyright (C) 2022 by Posit Software, PBC
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
package org.rstudio.studio.client.workbench.views.jobs;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.inject.Inject;

import org.rstudio.core.client.command.CommandBinder;
import org.rstudio.core.client.command.Handler;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.ui.DelayLoadTabShim;
import org.rstudio.studio.client.workbench.ui.DelayLoadWorkbenchTab;
import org.rstudio.studio.client.workbench.views.jobs.events.JobElapsedTickEvent;
import org.rstudio.studio.client.workbench.views.jobs.events.JobInitEvent;
import org.rstudio.studio.client.workbench.views.jobs.events.JobOutputEvent;
import org.rstudio.studio.client.workbench.views.jobs.events.JobSelectionEvent;
import org.rstudio.studio.client.workbench.views.jobs.events.JobUpdatedEvent;
import org.rstudio.studio.client.workbench.views.jobs.events.JobsActivateEvent;
import org.rstudio.studio.client.workbench.views.jobs.events.JobsPresenterEventHandlers;

public class JobsTab extends DelayLoadWorkbenchTab<JobsPresenter>
                     implements JobInitEvent.Handler
{
   public abstract static class Shim 
        extends DelayLoadTabShim<JobsPresenter, JobsTab>
        implements JobsPresenterEventHandlers
   {
      abstract void confirmClose(Command onConfirmed);
      
      @Handler
      public abstract void onActivateBackgroundJobs();
   }
   
   public interface Binder extends CommandBinder<Commands, JobsTab.Shim> {}

   @Inject
   public JobsTab(final Shim shim, 
                        Binder binder,
                        Commands commands,
                        EventBus events)
   {
      super(constants_.backgroundJobsTitle(), shim);
      shim_ = shim;
      
      binder.bind(commands, shim);
      events.addHandler(JobUpdatedEvent.TYPE, shim);
      events.addHandler(JobOutputEvent.TYPE, shim);
      events.addHandler(JobSelectionEvent.TYPE, shim);
      events.addHandler(JobElapsedTickEvent.TYPE, shim);
      events.addHandler(JobsActivateEvent.TYPE, shim);
      
      events.addHandler(JobInitEvent.TYPE, this);
   }

   @Override
   public void onJobInit(JobInitEvent event)
   {
      if (event.state() != null && event.state().hasJobs())
      {
         // don't message the shim unless there are jobs, otherwise will trigger
         // unnecessary loading of the deferred tab code
         shim_.setInitialJobs(event.state());
      }
   }

   @Override
   public boolean closeable()
   {
      return true;
   }

   @Override
   public void confirmClose(Command onConfirmed)
   {
      shim_.confirmClose(onConfirmed);
   }
   
   final Shim shim_;
   private static final JobsConstants constants_ = GWT.create(JobsConstants.class);
}
