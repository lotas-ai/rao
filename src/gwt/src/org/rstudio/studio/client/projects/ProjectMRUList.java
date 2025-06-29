/*
 * ProjectMRUList.java
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
package org.rstudio.studio.client.projects;

import java.util.ArrayList;

import com.google.gwt.core.client.GWT;

import org.rstudio.core.client.DuplicateHelper;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.command.AppCommand;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.studio.client.application.Desktop;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.projects.events.OpenProjectNewWindowEvent;
import org.rstudio.studio.client.projects.events.SwitchToProjectEvent;
import org.rstudio.studio.client.projects.model.ProjectMRUEntry;
import org.rstudio.studio.client.workbench.MRUList;
import org.rstudio.studio.client.workbench.WorkbenchListManager;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.model.Session;

import com.google.gwt.resources.client.ImageResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ProjectMRUList extends MRUList
{
   @Inject 
   public ProjectMRUList(Commands commands, 
                         WorkbenchListManager listManager,
                         final EventBus eventBus,
                         Session session)
   {
      super(listManager.getProjectNameMruList(),
            new AppCommand[] {
                  commands.projectMru0(),
                  commands.projectMru1(),
                  commands.projectMru2(),
                  commands.projectMru3(),
                  commands.projectMru4(),
                  commands.projectMru5(),
                  commands.projectMru6(),
                  commands.projectMru7(),
                  commands.projectMru8(),
                  commands.projectMru9(),
                  commands.projectMru10(),
                  commands.projectMru11(),
                  commands.projectMru12(),
                  commands.projectMru13(),
                  commands.projectMru14()
            },
            commands.clearRecentProjects(),
            false,
            false,
            new OperationWithInput<String>() 
            {
               @Override
               public void execute(String file)
               {
                  openProjectFromMru(eventBus, new ProjectMRUEntry(file).getProjectFilePath());
               }
            });
      
      // set right image for project MRU commands
      if (Desktop.hasDesktopFrame() || session.getSessionInfo().getMultiSession())
      {
         ImageResource image = commands.openHtmlExternal().getImageResource();
         commands.projectMru0().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru1().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru2().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru3().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru4().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru5().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru6().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru7().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru8().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru9().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru10().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru11().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru12().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru13().setRightImage(image, NEW_SESSION_DESC);
         commands.projectMru14().setRightImage(image, NEW_SESSION_DESC);
      }
   }
   
   public static void setOpenInNewWindow(boolean openInNewWindow)
   {
      openInNewWindow_ = openInNewWindow;
   }
   
   public static void openProjectFromMru(EventBus eventBus, String file)
   {
      if (openInNewWindow_)
         eventBus.fireEvent(new OpenProjectNewWindowEvent(file, null));
      else
         eventBus.fireEvent(new SwitchToProjectEvent(file));
   }
   
   @Override
   protected String transformMruEntryPath(String entryPath)
   {
      // split out the path component and tweak it
      ProjectMRUEntry mruEntry = new ProjectMRUEntry(entryPath);
      String newPath = FileSystemItem.createFile(mruEntry.getProjectFilePath()).getParentPathString();

      // then reappend the custom name (if any)
      return new ProjectMRUEntry(newPath, mruEntry.getProjectName()).getMRUValue();
   }
   
   @Override
   protected ArrayList<String> generateLabels(ArrayList<String> mruEntries, boolean includeExt)
   {
      // split out the paths and names so we can dedupe the paths
      ArrayList<String> mruPaths = new ArrayList<String>();
      ArrayList<String> mruNames = new ArrayList<String>();
      for (String entry : mruEntries)
      {
         ProjectMRUEntry mruEntry = new ProjectMRUEntry(entry);
         mruPaths.add(mruEntry.getProjectFilePath());
         mruNames.add(StringUtil.notNull(mruEntry.getProjectName()));
      }
      // Before the project naming feature was added, this generateLabels() method consisted 
      // of a single of code:
      //
      //   return DuplicateHelper.getPathLabels(mruEntries, true);
      //
      // Note that it hardcoded true for the "includeExtensions" parameter and did not use
      // the value of the "includeExt" parameter. Also have to do that here to avoid
      // https://github.com/rstudio/rstudio/issues/14107.
      mruPaths = DuplicateHelper.getPathLabels(mruPaths, true);
      
      // recombine paths and names for display
      ArrayList<String> result = new ArrayList<String>();
      for (int i = 0; i < mruEntries.size(); i++)
      {
         if (mruNames.get(i).length() > 0)
            result.add(mruPaths.get(i) + " (" + mruNames.get(i) + ")");
         else
            result.add(mruPaths.get(i));
      }
      return result;
   }
   private static final StudioClientProjectConstants constants_ = GWT.create(StudioClientProjectConstants.class);

   private static boolean openInNewWindow_ = false;

   public final static String NEW_SESSION_DESC = constants_.openProjectLabel();
}
