/*
 * AiPaneAttachments.java
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

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.MenuItem;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.studio.client.workbench.views.ai.model.Link;
import org.rstudio.core.client.files.FileSystemItem;

import java.util.ArrayList;

public class AiPaneAttachments
{
   public AiPaneAttachments(AiPane pane, AiServerOperations server, GlobalDisplay globalDisplay)
   {
      pane_ = pane;
      server_ = server;
      globalDisplay_ = globalDisplay;
   }
   
   /**
    * Initialize the attachments menu
    * @param commands The commands instance
    * @return The initialized menu
    */
   public AiAttachmentsMenu initAttachmentsMenu(Commands commands)
   {
      MenuItem clear = new MenuItem("Delete All Attachments", new Command() {
         @Override
         public void execute() {
            deleteAllAttachments();
         }
      });
      
      AiAttachmentsMenu menu = new AiAttachmentsMenu(Integer.MAX_VALUE, true, null, new MenuItem[] { clear }, this);
      
      // Load the available attachments
      loadAttachments(menu);
      
      return menu;
   }
   
   /**
    * Load attachments from the server
    * @param menu The menu to populate
    */
   public void loadAttachments(final AiAttachmentsMenu menu)
   {
      // Store the menu reference for later use
      setAttachmentsMenu(menu);
      
      server_.listAttachments(new ServerRequestCallback<JsArrayString>() {
         @Override
         public void onResponseReceived(JsArrayString response)
         {
            if (response == null || response.length() == 0)
            {
               menu.clearLinks();
               return;
            }
            
            menu.clearLinks();
            
            // Add links for each attachment
            for (int i = 0; i < response.length(); i++)
            {
               final String filePath = response.get(i);
               
               // Extract filename from path
               FileSystemItem file = FileSystemItem.createFile(filePath);
               String filename = file.getName();
               
               Link link = new Link(filePath, filename);
               menu.addLink(link);
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            // Don't show error message if it's specifically from Anthropic model
            String errorMsg = error.getMessage();
            if (errorMsg == null || !errorMsg.contains("Anthropic")) {
               globalDisplay_.showErrorMessage("Error", "Failed to load attachments: " + errorMsg);
            }
         }
      });
   }
   
   /**
    * Print all attachments to the browser console
    * This can be called externally when the menu button is clicked
    */
   public void printAttachmentsToConsole()
   {
      server_.listAttachments(new ServerRequestCallback<JsArrayString>() {
         @Override
         public void onResponseReceived(JsArrayString response)
         {
            // Not printing to console anymore
         }
         
         @Override
         public void onError(ServerError error)
         {
            // Not printing to console anymore
         }
      });
   }
   
   /**
    * Delete a specific attachment
    * @param filePath The file path to delete
    */
   public void deleteAttachment(final String filePath)
   {
      server_.deleteAttachment(filePath, new ServerRequestCallback<Void>() {
         @Override
         public void onResponseReceived(Void v)
         {
            // Use the pane's refreshAttachmentsList method instead
            pane_.refreshAttachmentsList();
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Error", "Failed to delete attachment: " + error.getMessage());
         }
      });
   }
   
   /**
    * Delete all attachments for the current conversation
    */
   public void deleteAllAttachments()
   {
      server_.deleteAllAttachments(new ServerRequestCallback<Void>() {
         @Override
         public void onResponseReceived(Void v)
         {
            // Use the pane's refreshAttachmentsList method instead
            pane_.refreshAttachmentsList();
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Error", "Failed to delete all attachments: " + error.getMessage());
         }
      });
   }
   
   /**
    * Set the attachments menu reference
    * @param menu The menu to use
    */
   public void setAttachmentsMenu(AiAttachmentsMenu menu)
   {
      attachmentsMenu_ = menu;
   }
   
   private final AiPane pane_;
   private final AiServerOperations server_;
   private final GlobalDisplay globalDisplay_;
   private AiAttachmentsMenu attachmentsMenu_;
} 