/*
 * AiPaneImages.java
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
import org.rstudio.core.client.Debug;

import java.util.ArrayList;

public class AiPaneImages
{
   public AiPaneImages(AiPane pane, AiServerOperations server, GlobalDisplay globalDisplay)
   {
      pane_ = pane;
      server_ = server;
      globalDisplay_ = globalDisplay;
   }
   
   /**
    * Initialize the images menu
    * @param commands The commands instance
    * @return The initialized menu
    */
   public AiImagesMenu initImagesMenu(Commands commands)
   {
      MenuItem clear = new MenuItem("Delete All Images", new Command() {
         @Override
         public void execute() {
            deleteAllImages();
         }
      });
      
      AiImagesMenu menu = new AiImagesMenu(Integer.MAX_VALUE, true, null, new MenuItem[] { clear }, this);
      
      // Load the available images
      loadImages(menu);
      
      return menu;
   }
   
   /**
    * Load images from the server
    * @param menu The menu to populate
    */
   public void loadImages(AiImagesMenu menu)
   {
      server_.listImages(new ServerRequestCallback<JsArrayString>() {
         @Override
         public void onResponseReceived(JsArrayString response)
         {
            menu.clearLinks();
            
            if (response != null) {
               for (int i = 0; i < response.length(); i++) {
                  String imagePath = response.get(i);
                  FileSystemItem file = FileSystemItem.createFile(imagePath);
                  String title = file.getName();
                  
                  // Create a link for the image
                  Link link = new Link(imagePath, title);
                  menu.addLink(link);
               }
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Error", "Failed to load images: " + error.getMessage());
         }
      });
   }
   
   /**
    * Get the current number of attached images
    * @return The number of currently attached images
    */
   public void getCurrentImageCount(ServerRequestCallback<Integer> callback)
   {      
      server_.listImages(new ServerRequestCallback<JsArrayString>() {
         @Override
         public void onResponseReceived(JsArrayString response)
         {
            int count = (response != null) ? response.length() : 0;
            callback.onResponseReceived(count);
         }
         
         @Override
         public void onError(ServerError error)
         {
            Debug.log("AiPaneImages.getCurrentImageCount: listImages failed, error=" + 
               (error != null ? error.getMessage() : "null"));
            // If we can't get the count, assume 0 for safety
            callback.onResponseReceived(0);
         }
      });
   }
   
   /**
    * Attach an image to the conversation (limited to 3 images maximum)
    * @param imagePath The path to the image file
    */
   public void attachImage(String imagePath)
   {
      // First check if this image content is already attached (content-based deduplication)
      server_.checkImageContentDuplicate(imagePath, new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean isDuplicate)
         {
            if (isDuplicate) {
               globalDisplay_.showErrorMessage("Duplicate Image", 
                  "This image content is already attached to the conversation.");
               return;
            }
            
            // Check current image count for the 3-image limit
            getCurrentImageCount(new ServerRequestCallback<Integer>() {
               @Override
               public void onResponseReceived(Integer currentCount)
               {
                  if (currentCount >= 3) {
                     globalDisplay_.showErrorMessage("Image Limit Reached", 
                        "Only 3 images can be attached per message. Please remove an existing image before adding a new one.");
                     return;
                  }
                  
                  // Proceed with attachment if not duplicate and under the limit
                  server_.saveAiImage(imagePath, new ServerRequestCallback<Void>() {
                     @Override
                     public void onResponseReceived(Void v)
                     {
                        // Refresh the images list after successful attachment
                        pane_.refreshImagesList();
                     }
                     
                     @Override
                     public void onError(ServerError error)
                     {
                        Debug.log("AiPaneImages.attachImage: Server call failed, error=" + 
                           (error != null ? error.getMessage() : "null"));
                        String errorMessage = (error != null) ? error.getMessage() : "Unknown error occurred";
                        globalDisplay_.showErrorMessage("Error", "Failed to attach image: " + errorMessage);
                     }
                  });
               }
               
               @Override
               public void onError(ServerError error)
               {
                  Debug.log("AiPaneImages.attachImage: getCurrentImageCount failed, error=" + 
                     (error != null ? error.getMessage() : "null"));
                  String errorMessage = (error != null) ? error.getMessage() : "Unknown error occurred";
                  globalDisplay_.showErrorMessage("Error", "Failed to check current image count: " + errorMessage);
               }
            });
         }
         
         @Override
         public void onError(ServerError error)
         {
            Debug.log("AiPaneImages.attachImage: checkImageContentDuplicate failed, error=" + 
               (error != null ? error.getMessage() : "null"));
            String errorMessage = (error != null) ? error.getMessage() : "Unknown error occurred";
            globalDisplay_.showErrorMessage("Error", "Failed to check for duplicate image content: " + errorMessage);
         }
      });
   }
   
   /**
    * Delete a specific image
    * @param imagePath The image path to delete
    */
   public void deleteImage(final String imagePath)
   {
      server_.deleteImage(imagePath, new ServerRequestCallback<Void>() {
         @Override
         public void onResponseReceived(Void v)
         {
            // Use the pane's refreshImagesList method instead
            pane_.refreshImagesList();
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Error", "Failed to delete image: " + error.getMessage());
         }
      });
   }
   
   /**
    * Delete all images for the current conversation
    */
   public void deleteAllImages()
   {
      server_.deleteAllImages(new ServerRequestCallback<Void>() {
         @Override
         public void onResponseReceived(Void v)
         {
            // Use the pane's refreshImagesList method instead
            pane_.refreshImagesList();
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Error", "Failed to delete all images: " + error.getMessage());
         }
      });
   }
   
   /**
    * Set the images menu reference
    * @param menu The menu to use
    */
   public void setImagesMenu(AiImagesMenu menu)
   {
      imagesMenu_ = menu;
   }
   
   private final AiPane pane_;
   private final AiServerOperations server_;
   private final GlobalDisplay globalDisplay_;
   private AiImagesMenu imagesMenu_;
} 