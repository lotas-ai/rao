/*
 * AiImagesMenu.java
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

import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.event.dom.client.ErrorEvent;
import com.google.gwt.event.dom.client.ErrorHandler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.Label;
import org.rstudio.core.client.widget.ScrollableToolbarPopupMenu;
import org.rstudio.studio.client.workbench.views.ai.Ai.LinkMenu;
import org.rstudio.studio.client.workbench.views.ai.model.Link;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.core.client.Scheduler;

import java.util.ArrayList;

public class AiImagesMenu implements LinkMenu, HasSelectionHandlers<String>
{
   public AiImagesMenu(int maxLinks,
                      boolean addFromTop,
                      MenuItem[] pre,
                      MenuItem[] post,
                      AiPaneImages images)
   {
      menu_ = new AiScrollableMenu();
      top_ = addFromTop;
      pre_ = pre != null ? pre : new MenuItem[0];
      post_ = post != null ? post : new MenuItem[0];
      images_ = images;
      clearLinks();
   }
   
   public void setImages(AiPaneImages images)
   {
      images_ = images;
   }
   
   public ScrollableToolbarPopupMenu getMenu()
   {
      return menu_;
   }

   public void addLink(Link link)
   {
      ImageMenuItem menuItem = new ImageMenuItem(link, this);
      int beforeIndex;
      if (top_)
      {
         beforeIndex = pre_.length == 0 ? 0 : pre_.length + 1;
      }
      else
      {
         beforeIndex = menu_.getItemCount();
         if (pre_.length > 0)
            beforeIndex++; // initial separator isn't counted in getItemCount()
         if (post_.length > 0)
            beforeIndex -= post_.length + 1;
         
         // some weird race condition causes beforeIndex to go negative
         beforeIndex = Math.max(0, beforeIndex);
      }

      try
      {
         menu_.insertItem(menuItem, beforeIndex);
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      
      links_.add(top_ ? 0 : links_.size(), link);
   }

   public void removeLink(Link link)
   {
      menu_.removeItem(new ImageMenuItem(link, this));
      links_.remove(link);
   }
   
   public boolean containsLink(Link link)
   {
      boolean result = menu_.containsItem(new ImageMenuItem(link, this));
      return result;
   }
   
   public void clearLinks()
   {
      menu_.clearItems();
      for (MenuItem mi : pre_)
         menu_.addItem(mi);
      if (pre_.length > 0)
         menu_.addSeparator();
      if (post_.length > 0)
         menu_.addSeparator();
      for (MenuItem mi : post_)
         menu_.addItem(mi);
      
      links_.clear();
   }
   
   public ArrayList<Link> getLinks()
   {
      return new ArrayList<>(links_);
   }

   public HandlerRegistration addLinkSelectionHandler(
                                            SelectionHandler<String> handler)
   {
      return handlers_.addHandler(SelectionEvent.getType(), handler);
   }
   
   @Override
   public HandlerRegistration addSelectionHandler(SelectionHandler<String> handler)
   {
      return addLinkSelectionHandler(handler);
   }
   
   public void fireEvent(GwtEvent<?> event)
   {
      handlers_.fireEvent(event);
   }
   
   // Custom ScrollableToolbarPopupMenu that opens upwards
   private class AiScrollableMenu extends ScrollableToolbarPopupMenu
   {
      
      @Override
      protected int getMaxHeight()
      {
         // Set max height to show 3 items before scrolling
         // Images need more space, approximately 128px per item (120px + padding)
         return 3 * 128;
      }
      
      @Override
      public void onAttach()
      {
         super.onAttach();
         
         // Allow menu to size naturally to content - no minimum width constraint
         getElement().getStyle().clearProperty("minWidth");
         getElement().getStyle().setProperty("width", "auto");
      }
   }
   
   private class ImageMenuItem extends MenuItem
   {
      public ImageMenuItem(final Link link,
                          final AiImagesMenu thiz)
      {
         super("", new Command()
         {
            public void execute()
            {
               SelectionEvent.fire(thiz, link.getUrl());
            }
         });
         
         link_ = link;
         
         // Schedule the creation of the image thumbnail after the item is added to DOM
         Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            @Override
            public void execute() {
               try {
                  // Get the menu item's element
                  final Element menuElement = getElement();
                  if (menuElement == null) return;
                  
                  // Create thumbnail image
                  Image thumbnail = new Image();
                  String imagePath = link.getUrl(); // The link URL contains the image path
                  
                  // Use the actual image file - convert file path to accessible URL
                  // The RStudio server should be able to serve files from the session directory
                  String imageUrl = constructImageUrl(imagePath);
                  thumbnail.setUrl(imageUrl);
                  
                  // Style the thumbnail - uniform height, maintain aspect ratio, tight bounding
                  thumbnail.getElement().getStyle().setProperty("height", "120px");
                  thumbnail.getElement().getStyle().setProperty("width", "auto");
                  thumbnail.getElement().getStyle().setProperty("objectFit", "contain");
                  thumbnail.getElement().getStyle().setProperty("border", "1px solid #ddd");
                  thumbnail.getElement().getStyle().setProperty("backgroundColor", "#f9f9f9");
                  thumbnail.getElement().getStyle().setProperty("display", "block");
                  thumbnail.getElement().getStyle().setProperty("margin", "0");
                  
                  // Add error handling for images that fail to load
                  thumbnail.addErrorHandler(new ErrorHandler() {
                     @Override
                     public void onError(ErrorEvent event) {
                        // If image fails to load, show a fallback placeholder
                        Element img = thumbnail.getElement();
                        img.getStyle().setProperty("display", "none");
                        
                        // Create a fallback placeholder div
                        Element placeholder = Document.get().createDivElement();
                        placeholder.getStyle().setProperty("height", "120px");
                        placeholder.getStyle().setProperty("width", "120px");
                        placeholder.getStyle().setProperty("backgroundColor", "#f0f0f0");
                        placeholder.getStyle().setProperty("border", "1px solid #ddd");
                        placeholder.getStyle().setProperty("display", "flex");
                        placeholder.getStyle().setProperty("alignItems", "center");
                        placeholder.getStyle().setProperty("justifyContent", "center");
                        placeholder.getStyle().setProperty("fontSize", "14px");
                        placeholder.getStyle().setProperty("color", "#999");
                        placeholder.getStyle().setProperty("margin", "0");
                        placeholder.setInnerHTML("IMG");
                        
                        // Replace the image with the placeholder
                        Element parent = img.getParentElement();
                        if (parent != null) {
                           parent.insertBefore(placeholder, img);
                           parent.removeChild(img);
                        }
                     }
                  });
                  
                  // Clear the menu element and add the thumbnail
                  menuElement.setInnerHTML("");
                  menuElement.appendChild(thumbnail.getElement());
                  
                  // Make the parent element relatively positioned to contain the absolute buttons
                  menuElement.getStyle().setPosition(Position.RELATIVE);
                  menuElement.getStyle().setProperty("textAlign", "center");
                  menuElement.getStyle().setProperty("padding", "0");
                  menuElement.getStyle().setProperty("minHeight", "124px");
                  menuElement.getStyle().setProperty("width", "auto");
                  menuElement.getStyle().setProperty("display", "inline-block");
                  menuElement.getStyle().clearProperty("maxWidth");
                  menuElement.getStyle().clearProperty("minWidth");
                  
                  // Create the delete button
                  Element deleteButton = DOM.createSpan();
                  deleteButton.setClassName("ai-image-delete-button");
                  deleteButton.getStyle().setPosition(Position.ABSOLUTE);
                  deleteButton.getStyle().setRight(3, Unit.PX);
                  deleteButton.getStyle().setTop(3, Unit.PX);
                  deleteButton.getStyle().setFontSize(16, Unit.PX);
                  deleteButton.getStyle().setColor("#A00");
                  deleteButton.getStyle().setCursor(com.google.gwt.dom.client.Style.Cursor.POINTER);
                  deleteButton.getStyle().setZIndex(999);
                  deleteButton.getStyle().setProperty("lineHeight", "1");
                  deleteButton.setInnerHTML("×");
                  
                  // Add the delete button to the menu element
                  menuElement.appendChild(deleteButton);
                  
                  // Add event handling for the delete button
                  Event.sinkEvents(deleteButton, Event.ONCLICK);
                  Event.setEventListener(deleteButton, new EventListener() {
                     @Override
                     public void onBrowserEvent(Event event) {
                        // Stop event propagation
                        event.stopPropagation();
                        event.preventDefault();
                        
                        // Hide the menu to prevent scrolling issues
                        menu_.hide();
                        
                        // Delete the image
                        if (thiz.images_ != null) {
                           thiz.deleteImageAndRefresh(imagePath);
                        }
                     }
                  });
               } catch (Exception e) {
                  // Error handling
               }
            }
         });
      }
      
      @Override
      public int hashCode()
      {
         return link_.hashCode();
      }
      
      @Override
      public boolean equals(Object object)
      {
         if (object == null)
            return false;
         
         if (!(object instanceof ImageMenuItem))
            return false;
         
         ImageMenuItem other = (ImageMenuItem)object;
         if (other.link_ == null ^ link_ == null)
            return false;
         
         if (link_ == null)
            return true;
         
         return link_.equals(other.link_);
      }
      
      private final Link link_;
   }

   /**
    * Helper method to delete an image and ensure the UI is refreshed
    */
   public void deleteImageAndRefresh(String imagePath)
   {
      if (images_ != null) {
         images_.deleteImage(imagePath);
      }
   }

   /**
    * Constructs a URL that the browser can use to access the image file
    * @param imagePath The file system path to the image
    * @return A URL that can be used to access the image
    */
   private String constructImageUrl(String imagePath)
   {
      if (imagePath == null || imagePath.isEmpty()) {
         return "";
      }
      
      // Convert absolute file path to a relative path that RStudio server can serve
      // RStudio typically serves files from the session directory via specific endpoints
      try {
         // For images in the session directory, we can use the file serving endpoint
         // The RStudio server should be able to serve files from the current working directory
         if (imagePath.startsWith("/")) {
            // Absolute path - need to convert to relative or use file serving endpoint
            // RStudio usually has endpoints like /session/[id]/file/[path] for serving files
            return "file_show?path=" + encodeURIComponent(imagePath);
         } else {
            // Relative path - can be used directly with file serving
            return "file_show?path=" + encodeURIComponent(imagePath);
         }
      } catch (Exception e) {
         throw new RuntimeException("Failed to construct image URL", e);
      }
   }

   /**
    * Native method to encode URI components
    */
   private native String encodeURIComponent(String str) /*-{
      return encodeURIComponent(str);
   }-*/;

   private final HandlerManager handlers_ = new HandlerManager(null);
   private final ScrollableToolbarPopupMenu menu_;
   private final MenuItem[] pre_;
   private final MenuItem[] post_;
   private final ArrayList<Link> links_ = new ArrayList<>();
   private boolean top_;
   private AiPaneImages images_;
} 