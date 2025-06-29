/*
 * SVNSelectChangelistTablePresenter.java
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
package org.rstudio.studio.client.workbench.views.vcs.svn;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import org.rstudio.core.client.widget.Operation;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.common.vcs.ProcessResult;
import org.rstudio.studio.client.common.vcs.SVNServerOperations;
import org.rstudio.studio.client.common.vcs.StatusAndPath;
import org.rstudio.studio.client.workbench.views.vcs.ViewVcsConstants;
import org.rstudio.studio.client.workbench.views.vcs.svn.model.SVNState;

import java.util.ArrayList;

public class SVNSelectChangelistTablePresenter extends SVNChangelistTablePresenter
{
   @Inject
   public SVNSelectChangelistTablePresenter(final SVNSelectChangelistTable view,
                                            SVNState svnState,
                                            final SVNServerOperations server,
                                            final GlobalDisplay globalDisplay)
   {
      super(view, svnState);
      view_ = view;

      view.getCommitColumn().setFieldUpdater(new FieldUpdater<StatusAndPath, Boolean>()
      {
         @Override
         public void update(final int index,
                            final StatusAndPath object,
                            Boolean value)
         {
            if (value)
            {
               if (object.getStatus() == "?")
               {
                  server.svnAdd(toArray(object.getPath()), new SimpleRequestCallback<ProcessResult>()
                  {
                     @Override
                     public void onResponseReceived(ProcessResult response)
                     {
                        if (response.getExitCode() == 0)
                           view.setSelected(object, true);
                     }
                  });
                  return;
               }
               if (object.getStatus() == "!")
               {
                  server.svnDelete(toArray(object.getPath()),
                                   new SimpleRequestCallback<ProcessResult>()
                                   {
                                      @Override
                                      public void onResponseReceived(
                                            ProcessResult response)
                                      {
                                         if (response.getExitCode() == 0)
                                            view.setSelected(object, true);
                                      }
                                   });
                  return;
               }
               if (object.getStatus() == "C")
               {
                  globalDisplay.showYesNoMessage(
                        GlobalDisplay.MSG_WARNING,
                        constants_.fileConflictCapitalized(),
                        constants_.fileConflictMarkAsResolved(),
                        new Operation()
                        {
                           @Override
                           public void execute()
                           {
                              server.svnResolve(
                                    "working",
                                    toArray(object.getPath()),
                                    new SimpleRequestCallback<ProcessResult>()
                                    {
                                       @Override
                                       public void onResponseReceived(
                                             ProcessResult response)
                                       {
                                          if (response.getExitCode() == 0)
                                             view.setSelected(object, true);
                                       }
                                    });
                           }
                        },
                        false
                  );
                  return;
               }
            }

            view.setSelected(object, value);
         }

         private ArrayList<String> toArray(String path)
         {
            ArrayList<String> result = new ArrayList<>();
            result.add(path);
            return result;
         }
      });
   }

   @Override
   protected boolean rejectItem(StatusAndPath item)
   {
      return super.rejectItem(item) || "X".equals(item.getStatus());
   }

   public void clearSelection()
   {
      view_.clearSelection();
   }

   private final SVNSelectChangelistTable view_;
   private static final ViewVcsConstants constants_ = GWT.create(ViewVcsConstants.class);
}
