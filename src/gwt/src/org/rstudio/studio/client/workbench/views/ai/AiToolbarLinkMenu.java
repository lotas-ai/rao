/*
 * AiToolbarLinkMenu.java
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
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.FocusPanel;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.widget.ScrollableToolbarPopupMenu;
import org.rstudio.studio.client.workbench.views.ai.Ai.LinkMenu;
import org.rstudio.studio.client.workbench.views.ai.model.Link;
import org.rstudio.core.client.regex.Pattern;
import org.rstudio.core.client.regex.Match;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.TextBoxBase;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;

import java.util.ArrayList;

public class AiToolbarLinkMenu implements LinkMenu, HasSelectionHandlers<String>
{
   public AiToolbarLinkMenu(int maxLinks,
                          boolean addFromTop,
                          MenuItem[] pre,
                          MenuItem[] post)
   {
      this(maxLinks, addFromTop, pre, post, null);
   }
   
   public AiToolbarLinkMenu(int maxLinks,
                          boolean addFromTop,
                          MenuItem[] pre,
                          MenuItem[] post,
                          AiPaneConversations conversations)
   {
      menu_ = new AiScrollableMenu();
      top_ = addFromTop;
      pre_ = pre != null ? pre : new MenuItem[0];
      post_ = post != null ? post : new MenuItem[0];
      conversations_ = conversations;
      clearLinks();
   }
   
   public void setConversations(AiPaneConversations conversations)
   {
      conversations_ = conversations;
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
   
   // Custom ScrollableToolbarPopupMenu that sets a max height
   private class AiScrollableMenu extends ScrollableToolbarPopupMenu
   {
      @Override
      protected int getMaxHeight()
      {
         // Set a reasonable max height that will show approximately 12 items
         // before scrolling
         return 300;
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
                          final AiToolbarLinkMenu thiz)
      {
         super(link.getTitle(), new Command() {
            public void execute()
            {
               // If we have a conversations manager, parse the conversation ID directly
               if (thiz.conversations_ != null)
               {
                  String conversationIdStr = link.getUrl();
                  try
                  {
                     final int conversationId = Integer.parseInt(conversationIdStr);
                     thiz.conversations_.switchToConversation(conversationId, false);
                     return;
                  }
                  catch (NumberFormatException e)
                  {
                     Debug.log("AiToolbarLinkMenu: Error parsing conversation ID: " + conversationIdStr);
                  }
               }
               
               // Fall back to original behavior if no conversations manager or couldn't parse ID
               SelectionEvent.fire(thiz, link.getUrl());
            }
         });
         
         link_ = link;
         
         // Add edit and delete buttons for this conversation
         if (thiz.conversations_ != null)
         {
            // Get the conversation ID directly from the URL (which is now just the ID)
            String conversationIdStr = link.getUrl();
            try
            {
               final int conversationId = Integer.parseInt(conversationIdStr);
               
               // Create a custom widget to handle the menu item layout with edit/delete buttons
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
                        Element textElement = findTextElement(menuElement, null, null);
                        if (textElement != null) {
                           // Add right padding to ensure text doesn't overlap with the buttons
                           textElement.getStyle().setPaddingRight(50, Unit.PX);
                           // Set text overflow behavior to prevent it from breaking the layout
                           textElement.getStyle().setProperty("textOverflow", "ellipsis");
                           textElement.getStyle().setProperty("overflow", "hidden");
                           textElement.getStyle().setProperty("whiteSpace", "nowrap");
                        }
                        
                        // Create the edit button
                        Element editButton = DOM.createSpan();
                        editButton.setClassName("ai-edit-button");
                        editButton.getStyle().setPosition(Position.ABSOLUTE);
                        editButton.getStyle().setRight(30, Unit.PX);
                        editButton.getStyle().setTop(2, Unit.PX);
                        editButton.getStyle().setFontSize(14, Unit.PX);
                        editButton.getStyle().setColor("#666");
                        editButton.getStyle().setCursor(com.google.gwt.dom.client.Style.Cursor.POINTER);
                        editButton.getStyle().setZIndex(999);
                        editButton.setInnerHTML("✎");
                        
                        // Create the delete button
                        Element deleteButton = DOM.createSpan();
                        deleteButton.setClassName("ai-delete-button");
                        deleteButton.getStyle().setPosition(Position.ABSOLUTE);
                        deleteButton.getStyle().setRight(10, Unit.PX);
                        deleteButton.getStyle().setTop(1, Unit.PX);
                        deleteButton.getStyle().setFontSize(14, Unit.PX);
                        deleteButton.getStyle().setColor("#A00");
                        deleteButton.getStyle().setCursor(com.google.gwt.dom.client.Style.Cursor.POINTER);
                        deleteButton.getStyle().setZIndex(999);
                        deleteButton.setInnerHTML("×");
                        
                        // Add the buttons to the menu element
                        menuElement.appendChild(editButton);
                        menuElement.appendChild(deleteButton);
                        
                        // Track editing state
                        final boolean[] isEditing = { false };
                        
                        // Add event handling for the edit button
                        Event.sinkEvents(editButton, Event.ONCLICK);
                        Event.setEventListener(editButton, new EventListener() {
                           @Override
                           public void onBrowserEvent(Event event) {
                              // Prevent menu item from activating
                              event.stopPropagation();
                              event.preventDefault();
                                                                   
                              // Find the text node of the menu item (typically the first child)
                              final Element textElement = findTextElement(menuElement, editButton, deleteButton);
                              
                              if (textElement == null) {
                                 return;
                              }
                              
                              // Get current title
                              final String currentTitle = link.getTitle();
                              
                              // Save original text content
                              final String originalText = textElement.getInnerHTML();
                              
                              // Create a container for the edit UI
                              final FlowPanel editPanel = new FlowPanel();
                              editPanel.getElement().getStyle().setDisplay(Display.FLEX);
                              editPanel.getElement().getStyle().setProperty("alignItems", "center");
                              
                              // Create a text box for direct editing
                              final TextBox textBox = new TextBox();
                              textBox.setText(currentTitle);
                              textBox.getElement().getStyle().setWidth(150, Unit.PX);
                              textBox.getElement().getStyle().setBorderWidth(1, Unit.PX);
                              textBox.getElement().getStyle().setPadding(2, Unit.PX);
                              textBox.getElement().getStyle().setMargin(0, Unit.PX);
                              
                              // Create confirm button (check mark)
                              Element confirmButton = DOM.createSpan();
                              confirmButton.setClassName("ai-confirm-button");
                              confirmButton.getStyle().setMarginLeft(5, Unit.PX);
                              confirmButton.getStyle().setMarginRight(5, Unit.PX);
                              confirmButton.getStyle().setFontSize(14, Unit.PX);
                              confirmButton.getStyle().setColor("#080");
                              confirmButton.getStyle().setCursor(com.google.gwt.dom.client.Style.Cursor.POINTER);
                              confirmButton.setInnerHTML("✓");
                              
                              // Create cancel button (X)
                              Element cancelButton = DOM.createSpan();
                              cancelButton.setClassName("ai-cancel-button");
                              cancelButton.getStyle().setFontSize(14, Unit.PX);
                              cancelButton.getStyle().setColor("#A00");
                              cancelButton.getStyle().setCursor(com.google.gwt.dom.client.Style.Cursor.POINTER);
                              cancelButton.setInnerHTML("✗");
                              
                              // Add elements to the edit panel
                              editPanel.getElement().appendChild(textBox.getElement());
                              editPanel.getElement().appendChild(confirmButton);
                              editPanel.getElement().appendChild(cancelButton);
                              
                              // Hide the text content by emptying the element temporarily
                              textElement.setInnerHTML("");
                              
                              // Add the edit panel directly to the text element
                              textElement.appendChild(editPanel.getElement());
                              
                              // Focus the text box
                              textBox.setFocus(true);
                              textBox.selectAll();
                              
                              // Function to apply changes
                              final Command applyChanges = new Command() {
                                 @Override
                                 public void execute() {
                                    String newTitle = textBox.getText().trim();
                                    
                                    if (!newTitle.isEmpty()) {
                                       // Update both the link object and menu text
                                       link.setTitle(newTitle);
                                       setText(newTitle); // Use MenuItem's setText method
                                       
                                       // Update on the server
                                       thiz.conversations_.setConversationName(conversationId, newTitle);
                                    } else {
                                       // Restore original text if empty
                                       textElement.setInnerHTML(originalText);
                                    }
                                    
                                    // Clean up
                                    if (editPanel.isAttached()) {
                                       textElement.removeChild(editPanel.getElement());
                                    }
                                 }
                              };
                              
                              // Function to cancel
                              final Command cancelChanges = new Command() {
                                 @Override
                                 public void execute() {
                                    // Restore original text
                                    textElement.setInnerHTML(originalText);
                                    
                                    // Clean up
                                    if (editPanel.isAttached()) {
                                       textElement.removeChild(editPanel.getElement());
                                    }
                                 }
                              };
                              
                              // Add click handler to confirm button
                              Event.sinkEvents(confirmButton, Event.ONCLICK);
                              Event.setEventListener(confirmButton, new EventListener() {
                                 @Override
                                 public void onBrowserEvent(Event event) {
                                    event.stopPropagation();
                                    event.preventDefault();
                                    applyChanges.execute();
                                 }
                              });
                              
                              // Add click handler to cancel button
                              Event.sinkEvents(cancelButton, Event.ONCLICK);
                              Event.setEventListener(cancelButton, new EventListener() {
                                 @Override
                                 public void onBrowserEvent(Event event) {
                                    event.stopPropagation();
                                    event.preventDefault();
                                    cancelChanges.execute();
                                 }
                              });
                           }
                        });
                        
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
                              
                              // Delete the conversation
                              if (thiz.conversations_ != null) {
                                 thiz.conversations_.deleteConversation(conversationId);
                              }
                           }
                        });
                     } catch (Exception e) {
                        Debug.log("Error adding edit/delete buttons: " + e.getMessage());
                     }
                  }
               });
            }
            catch (NumberFormatException e)
            {
               Debug.log("AiToolbarLinkMenu: Error parsing conversation ID: " + e.getMessage());
            }
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
   private Element findTextElement(Element menuElement, Element editButton, Element deleteButton) {      
      // First try to find the first TD element which typically contains the menu text in GWT MenuItem
      NodeList<Element> tdElements = menuElement.getElementsByTagName("td");
      for (int i = 0; i < tdElements.getLength(); i++) {
         Element td = tdElements.getItem(i);
         return td;  // Return the first TD element, which should contain our text
      }
      
      // Fallback to direct child approach if no TD found
      if (editButton != null && deleteButton != null) {
         for (int i = 0; i < menuElement.getChildNodes().getLength(); i++) {
            if (menuElement.getChildNodes().getItem(i).getNodeType() == Node.ELEMENT_NODE) {
               Element child = (Element) menuElement.getChildNodes().getItem(i);
               // Skip our buttons
               if (!child.equals(editButton) && !child.equals(deleteButton)) {
                  return child;
               }
            }
         }
      } else {
         // If buttons are null (during initial styling), just return the first element child
         for (int i = 0; i < menuElement.getChildNodes().getLength(); i++) {
            if (menuElement.getChildNodes().getItem(i).getNodeType() == Node.ELEMENT_NODE) {
               return (Element) menuElement.getChildNodes().getItem(i);
            }
         }
      }
      
      // Last resort: try to use the menuElement itself
      return menuElement;
   }

   private final HandlerManager handlers_ = new HandlerManager(null);
   private final ScrollableToolbarPopupMenu menu_;
   private final MenuItem[] pre_;
   private final MenuItem[] post_;
   private final ArrayList<Link> links_ = new ArrayList<>();
   private boolean top_;
   private AiPaneConversations conversations_;
}
