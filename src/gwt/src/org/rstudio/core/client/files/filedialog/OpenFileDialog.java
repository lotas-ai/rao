/*
 * OpenFileDialog.java
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
package org.rstudio.core.client.files.filedialog;

import com.google.gwt.aria.client.Roles;
import org.rstudio.core.client.files.FileSystemContext;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.core.client.widget.ProgressOperationWithInput;
import org.rstudio.core.client.events.SelectionCommitEvent;

import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.user.client.ui.Label;

public class OpenFileDialog extends FileDialog
{
   public OpenFileDialog(String title,
                         String label,
                         FileSystemContext context,
                         String filter,
                         boolean canChooseDirectories,
                         ProgressOperationWithInput<FileSystemItem> operation)
   {
      super(title, null, Roles.getDialogRole(), label, false, false, false, context, filter, operation);
      canChooseDirectories_ = canChooseDirectories;
      
      // If we can choose directories, add a note to the dialog
      if (canChooseDirectories)
      {
         addNoteWidget(new Label("Select a file, or select a folder and press " + label + " to choose the folder."));
      }
   }

   @Override
   public boolean shouldAccept()
   {
      FileSystemItem item = browser_.getSelectedItem();
      String fileInput = browser_.getFilename().trim();

      // Handle both files and directories
      if (canChooseDirectories_)
      {
         // Handle case when a directory is selected
         if (item != null && item.isDirectory())
            return true;
         
         // Handle case when user typed a directory path
         if (fileInput.length() > 0)
         {
            FileSystemItem enteredItem = FileSystemItem.createFile(fileInput);
            if (enteredItem.isDirectory() && enteredItem.exists())
               return true;
         }
         
         // Handle case when no selection but in a directory
         if (item == null && fileInput.isEmpty())
            return true;
      }
      
      // Handle standard file selection - use parent's logic
      // This ensures files can be selected normally
      return super.shouldAccept();
   }

   @Override
   public void onNavigated()
   {
      super.onNavigated();
      
      // For directory selection, set the filename to the current path
      if (canChooseDirectories_)
      {
         browser_.setFilename(context_.pwd());
      }
      else
      {
         browser_.setFilename("");
      }
   }

   @Override
   protected FileSystemItem getSelectedItem()
   {
      // Get what the user has selected or entered
      FileSystemItem item = browser_.getSelectedItem();
      String filename = browser_.getFilename().trim();
      
      if (canChooseDirectories_)
      {
         // If a directory is selected, return it
         if (item != null && item.isDirectory())
            return item;
            
         // If user entered a path that is a directory, return it
         if (!filename.isEmpty())
         {
            FileSystemItem fileItem = FileSystemItem.createFile(filename);
            if (fileItem.exists() && fileItem.isDirectory())
               return fileItem;
         }
         
         // If no selection and empty filename, return current directory
         if (item == null && filename.isEmpty())
            return browser_.getCurrentDirectory();
      }
      
      // Standard behavior for files - use parent implementation
      // This allows files to be selected properly
      return super.getSelectedItem();
   }

   @Override
   public void onSelection(SelectionEvent<FileSystemItem> event)
   {
      super.onSelection(event);

      FileSystemItem item = event.getSelectedItem();
      
      // Different handling based on if it's a file or directory
      if (item != null)
      {
         if (item.isDirectory())
         {
            // For directories, show path when in directory mode
            if (canChooseDirectories_)
            {
               browser_.setFilename(item.getPath());
            }
            else
            {
               // In file-only mode, clear the filename for directories
               browser_.setFilename("");
            }
         }
         else
         {
            // For files, always show the filename
            browser_.setFilename(item.getName());
         }
      }
   }

   @Override
   public void onSelectionCommit(SelectionCommitEvent<FileSystemItem> event)
   {
      FileSystemItem item = event.getSelectedItem();
      
      // If this is a directory and we can choose directories
      if (item != null && item.isDirectory() && canChooseDirectories_)
      {
         // For double-clicks, still navigate into the directory
         if (browser_.hasDoubleClicked())
         {
            browser_.cd(item);
         }
         // For enter or select button, actually select the directory
         else
         {
            // Set the filename field to the directory path to make it clear it's selected
            browser_.setFilename(item.getPath());
            
            // Accept the dialog with this directory
            accept(item);
         }
      }
      else
      {
         // Standard behavior - navigate into directories or select files
         super.onSelectionCommit(event);
      }
   }

   @Override
   public String getFilenameLabel()
   {
      return canChooseDirectories_ ? "Folder" : "File name";
   }

   protected final boolean canChooseDirectories_;
}