/*
 * AiAttachmentsMenu.java
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

import java.util.ArrayList;

public class AiAttachmentsMenu implements LinkMenu, HasSelectionHandlers<String>
{
   public AiAttachmentsMenu(int maxLinks,
                          boolean addFromTop,
                          MenuItem[] pre,
                          MenuItem[] post,
                          AiPaneAttachments attachments)
   {
      menu_ = new AiScrollableMenu();
      top_ = addFromTop;
      pre_ = pre != null ? pre : new MenuItem[0];
      post_ = post != null ? post : new MenuItem[0];
      attachments_ = attachments;
      clearLinks();
   }
   
   public void setAttachments(AiPaneAttachments attachments)
   {
      attachments_ = attachments;
   }
   
   public ScrollableToolbarPopupMenu getMenu()
   {
      return menu_;
   }

   public void addLink(Link link)
   {
      LinkMenuItem menuItem = new LinkMenuItem(link, this);
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
      menu_.removeItem(new LinkMenuItem(link, this));
      links_.remove(link);
   }
   
   public boolean containsLink(Link link)
   {
      boolean result = menu_.containsItem(new LinkMenuItem(link, this));
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
         // Set max height to show 4 items before scrolling
         // Approximate height per item is 25px
         return 4 * 25;
      }
      
      @Override
      public void onAttach()
      {
         super.onAttach();
         
         // Set minimum width for the popup menu
         getElement().getStyle().setProperty("minWidth", "250px");
      }
   }
   
   private class LinkMenuItem extends MenuItem
   {
      public LinkMenuItem(final Link link, 
                          final AiAttachmentsMenu thiz)
      {
         // Instead of having a click action, just display the file name
         super(link.getTitle(), (Command)null);
         
         link_ = link;
         
         // Add a delete button for this attachment
         if (thiz.attachments_ != null)
         {
            // Get the file path from the URL, not just the title
            final String filePath = link.getUrl();
            
            // Create a custom widget to handle the menu item layout with delete button
            // We need to defer this operation so the menu item is fully created
            com.google.gwt.core.client.Scheduler.get().scheduleDeferred(new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
               @Override
               public void execute() {
                  try {
                     // Get the menu item's element
                     final Element menuElement = getElement();
                     if (menuElement == null) return;
                     
                     // Make the parent element relatively positioned to contain the absolute buttons
                     menuElement.getStyle().setPosition(Position.RELATIVE);
                     
                     // Set a minimum width for the menu item to ensure there's enough space
                     menuElement.getStyle().setProperty("minWidth", "200px");
                     
                     // Find the text element and ensure there's padding on the right
                     // to prevent overlap with the buttons
                     Element textElement = findTextElement(menuElement, null);
                     if (textElement != null) {
                        // Add right padding to ensure text doesn't overlap with the button
                        textElement.getStyle().setPaddingRight(30, Unit.PX);
                        // Set text overflow behavior to prevent it from breaking the layout
                        textElement.getStyle().setProperty("textOverflow", "ellipsis");
                        textElement.getStyle().setProperty("overflow", "hidden");
                        textElement.getStyle().setProperty("whiteSpace", "nowrap");
                     }
                     
                     // Create the delete button
                     Element deleteButton = DOM.createSpan();
                     deleteButton.setClassName("ai-attachment-delete-button");
                     deleteButton.getStyle().setPosition(Position.ABSOLUTE);
                     deleteButton.getStyle().setRight(10, Unit.PX);
                     deleteButton.getStyle().setTop(1, Unit.PX);
                     deleteButton.getStyle().setFontSize(14, Unit.PX);
                     deleteButton.getStyle().setColor("#A00");
                     deleteButton.getStyle().setCursor(com.google.gwt.dom.client.Style.Cursor.POINTER);
                     deleteButton.getStyle().setZIndex(999);
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
                           
                           // Delete the attachment
                           if (thiz.attachments_ != null) {
                              thiz.deleteAttachmentAndRefresh(filePath);
                           }
                        }
                     });
                  } catch (Exception e) {
                     // No debug logging in the file
                  }
               }
            });
         }
      }
      
      @Override
      public int hashCode()
      {
         return link_.hashCode();
      }

      @Override
      public boolean equals(Object obj)
      {
         if (this == obj)
            return true;
         if (obj == null)
            return false;
         if (getClass() != obj.getClass())
            return false;
         LinkMenuItem other = (LinkMenuItem) obj;
         if (link_ == null)
         {
            if (other.link_ != null)
               return false;
         } else if (!link_.equals(other.link_))
            return false;
         return true;
      }

      private final Link link_;
   }

   /**
    * Helper method to find the text element in a menu item
    */
   private Element findTextElement(Element menuElement, Element deleteButton) {      
      // First try to find the first TD element which typically contains the menu text in GWT MenuItem
      NodeList<Element> tdElements = menuElement.getElementsByTagName("td");
      for (int i = 0; i < tdElements.getLength(); i++) {
         Element td = tdElements.getItem(i);
         return td;  // Return the first TD element, which should contain our text
      }
      
      // Fallback to direct child approach if no TD found
      if (deleteButton != null) {
         for (int i = 0; i < menuElement.getChildNodes().getLength(); i++) {
            if (menuElement.getChildNodes().getItem(i).getNodeType() == Node.ELEMENT_NODE) {
               Element child = (Element) menuElement.getChildNodes().getItem(i);
               // Skip our button
               if (!child.equals(deleteButton)) {
                  return child;
               }
            }
         }
      } else {
         // If button is null (during initial styling), just return the first element child
         for (int i = 0; i < menuElement.getChildNodes().getLength(); i++) {
            if (menuElement.getChildNodes().getItem(i).getNodeType() == Node.ELEMENT_NODE) {
               return (Element) menuElement.getChildNodes().getItem(i);
            }
         }
      }
      
      // Last resort: try to use the menuElement itself
      return menuElement;
   }

   /**
    * Helper method to delete an attachment and ensure the UI is refreshed
    */
   public void deleteAttachmentAndRefresh(String filePath)
   {
      if (attachments_ != null) {
         attachments_.deleteAttachment(filePath);
      }
   }

   private final HandlerManager handlers_ = new HandlerManager(null);
   private final ScrollableToolbarPopupMenu menu_;
   private final MenuItem[] pre_;
   private final MenuItem[] post_;
   private final ArrayList<Link> links_ = new ArrayList<>();
   private boolean top_;
   private AiPaneAttachments attachments_;
} 