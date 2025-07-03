/*
 * AiPaneConversations.java
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

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.FlowPanel;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.views.ai.Ai.LinkMenu;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.studio.client.workbench.views.ai.model.ConversationNamesResult;
import org.rstudio.studio.client.workbench.views.ai.model.CreateAiConversationResult;
import org.rstudio.studio.client.workbench.views.ai.model.Link;
import org.rstudio.studio.client.workbench.views.ai.model.ApiKeyManagementResult;
import org.rstudio.core.client.regex.Match;
import org.rstudio.core.client.regex.Pattern;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.command.CommandBinder;
import org.rstudio.core.client.command.Handler;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.core.client.widget.Operation;
import org.rstudio.core.client.dom.WindowEx;

import java.util.HashMap;
import java.util.Map;

public class AiPaneConversations
{
   public AiPaneConversations(AiPane pane, AiServerOperations server, GlobalDisplay globalDisplay)
   {
      pane_ = pane;
      server_ = server;
      globalDisplay_ = globalDisplay;
      nameCache_ = new HashMap<>();
   }
   
   /**
    * Initialize the conversation menu with available conversations
    * @param commands The commands instance
    * @return The initialized menu
    */
   public AiToolbarLinkMenu initConversationMenu(Commands commands)
   {
      MenuItem clear = commands.clearAiHistory().createMenuItem(false);
      AiToolbarLinkMenu menu = new AiToolbarLinkMenu(Integer.MAX_VALUE, true, null, new MenuItem[] { clear }, this);
      
      // Load the available conversations
      loadConversations(menu);
      
      return menu;
   }
   
   /**
    * Load available conversations from the server
    * @param menu The menu to populate
    */
   public void loadConversations(final AiToolbarLinkMenu menu)
   {
      // First load the list of conversation names
      server_.listConversationNames(new ServerRequestCallback<ConversationNamesResult>() {
         @Override
         public void onResponseReceived(ConversationNamesResult result)
         {
            // Store the names in the cache
            nameCache_.clear();
            if (result != null)
            {
               JsArray<ConversationNamesResult.ConversationNameEntry> names = result.getNames();
               for (int i = 0; i < names.length(); i++)
               {
                  ConversationNamesResult.ConversationNameEntry entry = names.get(i);
                  nameCache_.put(entry.getConversationId(), entry.getName());
               }
            }
            
            // Now load the conversations
            loadConversationsList(menu);
         }
         
         @Override
         public void onError(ServerError error)
         {
            // Even if we can't load names, still try to load conversations
            loadConversationsList(menu);
         }
      });
   }
   
   /**
    * Load the list of available conversations
    * @param menu The menu to populate
    */
   private void loadConversationsList(final AiToolbarLinkMenu menu)
   {
      server_.listConversations(new ServerRequestCallback<JsArrayString>() {
         @Override
         public void onResponseReceived(JsArrayString response)
         {
            // Clear the menu even if there are no conversations
            menu.clearLinks();
            
            if (response == null || response.length() == 0)
            {
               // Don't clear the title when there are no conversations
               return;
            }
            
            // Add links for each conversation
            for (int i = 0; i < response.length(); i++)
            {
               final String index = response.get(i);
               final int conversationId = Integer.parseInt(index);
               
               // Use the custom name if available, otherwise default to "Conversation X"
               String title = getConversationName(conversationId);
                              
               MenuItem item = new MenuItem(title, new Command() {
                  @Override
                  public void execute()
                  {
                     switchToConversation(conversationId);
                  }
               });
               
               Link link = new Link(index, title);
               menu.addLink(link);
            }
            
            // Update the title if we're already viewing a conversation
            getCurrentConversationIndex(new ServerRequestCallback<Double>() {
               @Override
               public void onResponseReceived(Double currentIndex) {
                  if (currentIndex != null && currentIndex.intValue() > 0) {
                     String currentTitle = getConversationName(currentIndex.intValue());
                     if (currentTitle != null && !currentTitle.isEmpty()) {
                        pane_.updateTitle(currentTitle);
                     }
                  }
               }
               
               @Override
               public void onError(ServerError error) {
                  // Handle error silently
               }
            });
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Error", "Failed: " + error.getMessage());
         }
      });
   }
   
   /**
    * Get the name of a conversation by its ID
    * @param conversationId The conversation ID
    * @return The conversation name
    */
   public String getConversationName(int conversationId)
   {
      if (nameCache_.containsKey(conversationId))
      {
         return nameCache_.get(conversationId);
      }
      return "New conversation";
   }
   
   /**
    * Set the name of a conversation and update the cache
    * @param conversationId The conversation ID
    * @param name The new name
    */
   public void setConversationName(int conversationId, String name)
   {
      nameCache_.put(conversationId, name);
      server_.setConversationName(conversationId, name, new ServerRequestCallback<Void>() {
         @Override
         public void onResponseReceived(Void v)
         {
            // Reload the menu with the new name
            LinkMenu menu = pane_.getHistory();
            if (menu instanceof AiToolbarLinkMenu)
            {
               loadConversations((AiToolbarLinkMenu)menu);
            }
            
            // Update the title if this is the currently displayed conversation
            getCurrentConversationIndex(new ServerRequestCallback<Double>() {
               @Override
               public void onResponseReceived(Double currentIndex) {
                  if (currentIndex != null && currentIndex.intValue() == conversationId) {
                     // Update the title in the pane
                     pane_.updateTitle(name);
                  }
               }
               
               @Override
               public void onError(ServerError error) {
                  // Handle error silently
               }
            });
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Error", "Failed to set conversation name: " + error.getMessage());
         }
      });
   }
   
   /**
    * Get the current conversation index from the server via RPC
    * @param callback Callback to receive the conversation index
    */
   public void getCurrentConversationIndex(ServerRequestCallback<Double> callback)
   {
      server_.getCurrentConversationIndex(callback);
   }
   
   /**
    * Check if we should prompt for a conversation name
    * @param callback Callback with the result
    */
   public void checkShouldPromptForName(ServerRequestCallback<Boolean> callback)
   {
      server_.shouldPromptForName(callback);
   }
   
   /**
    * Switch to a different conversation by index
    * @param index The conversation index to switch to
    */
   public void switchToConversation(int index)
   {
      switchToConversation(index, true);
   }
   
   /**
    * Switch to a different conversation by index  
    * @param index The conversation index to switch to
    * @param shouldLoadLog Whether to load conversation history (false for new/empty conversations)
    */
   public void switchToConversation(int index, boolean shouldLoadLog)
   {
      // Reset path variables first
      resetConversationPaths();
      
      // Get the name before switching
      final String title = getConversationName(index);
      
      // Set the title immediately before any navigation starts
      if (title != null && !title.isEmpty()) {
         pane_.updateTitle(title);
      }
      
      // Use the view manager to load the conversation history
      if (pane_.getToolbars() != null && pane_.getToolbars().getViewManager() != null) {
         pane_.getToolbars().getViewManager().loadConversationHistory(index, shouldLoadLog);
      } else {
         Debug.log("DEBUG: AiPaneConversations - ERROR: toolbars or viewManager is null!");
         Debug.log("DEBUG: AiPaneConversations - pane_.getToolbars(): " + (pane_.getToolbars() != null ? "not null" : "null"));
         if (pane_.getToolbars() != null) {
            Debug.log("DEBUG: AiPaneConversations - pane_.getToolbars().getViewManager(): " + (pane_.getToolbars().getViewManager() != null ? "not null" : "null"));
         }
      }
      
      // Switch to the conversation on the server side
      server_.switchConversation(index);
      
      // Load context items to synchronize UI with R session state
      if (pane_.getAiContext() != null) {
         FlowPanel selectedFilesPanel = pane_.getToolbars() != null ? 
            pane_.getToolbars().getSelectedFilesPanel() : null;
         pane_.getAiContext().loadContextItems(selectedFilesPanel);
      }
      
      // Refresh the attachments menu to reflect the current conversation's attachments
      pane_.refreshAttachmentsList();
      // Refresh the images menu to reflect the current conversation's images
      pane_.refreshImagesList();
   }
   
   /**
    * Delete a conversation by its ID
    * @param conversationId The conversation ID to delete
    */
   public void deleteConversation(final int conversationId)
   {
      // User confirmed, proceed with deletion
      String path = "conversation_" + conversationId;
      
      // Check if this is the currently displayed conversation
      getCurrentConversationIndex(new ServerRequestCallback<Double>() {
         @Override
         public void onResponseReceived(Double currentIndex) {
            final boolean isCurrentConversation = (currentIndex != null && currentIndex.intValue() == conversationId);
            
            // Continue with deletion logic
            deleteConversationInternal(conversationId, isCurrentConversation);
         }
         
         @Override
         public void onError(ServerError error) {
            // If we can't get current index, assume it's not current and proceed
            deleteConversationInternal(conversationId, false);
         }
      });
   }
   
   private void deleteConversationInternal(final int conversationId, final boolean isCurrentConversation)
   {
      String path = "conversation_" + conversationId;
      
      // First, clean up attachments for this conversation
      server_.cleanupConversationAttachments(conversationId, new ServerRequestCallback<Void>() {
         @Override
         public void onResponseReceived(Void response) {
            
            // Attachments cleaned up, now delete the conversation folder
            server_.deleteFolder(path, new ServerRequestCallback<Void>() {
               @Override
               public void onResponseReceived(Void response) {
                  
                  // Folder deleted successfully, now delete the conversation name
                  server_.deleteConversationName(conversationId, new ServerRequestCallback<Void>() {
                     @Override
                     public void onResponseReceived(Void response) {
                        
                        // Remove from the name cache
                        nameCache_.remove(conversationId);
                        
                        // Reload the menu to reflect changes
                        LinkMenu menu = pane_.getHistory();
                        if (menu instanceof AiToolbarLinkMenu) {
                           loadConversations((AiToolbarLinkMenu)menu);
                        }
                        
                        // Only navigate to API key management page if this was the current conversation
                        if (isCurrentConversation) {
                           // Use the proper method to navigate to API key management
                           server_.getApiKeyManagement(new ServerRequestCallback<ApiKeyManagementResult>() {
                              @Override
                              public void onResponseReceived(ApiKeyManagementResult result) {
                                 if (result.getSuccess()) {
                                    pane_.showAi(result.getPath());
                                 } else {
                                    // Fallback to static page if dynamic generation fails
                                    pane_.showAi("ai/doc/html/api_key_management.html");
                                 }
                              }
                              
                              @Override
                              public void onError(ServerError error) {
                                 // Fallback to static page if there's an error
                                 pane_.showAi("ai/doc/html/api_key_management.html");
                              }
                           });
                        }
                     }
                     
                     @Override
                     public void onError(ServerError error) {
                        // Non-critical error, just log it
                        
                        // Still reload menu
                        LinkMenu menu = pane_.getHistory();
                        if (menu instanceof AiToolbarLinkMenu) {
                           loadConversations((AiToolbarLinkMenu)menu);
                        }
                        
                        // Only navigate if this was the current conversation
                        if (isCurrentConversation) {
                           // Use the proper method to navigate to API key management
                           server_.getApiKeyManagement(new ServerRequestCallback<ApiKeyManagementResult>() {
                              @Override
                              public void onResponseReceived(ApiKeyManagementResult result) {
                                 if (result.getSuccess()) {
                                    pane_.showAi(result.getPath());
                                 } else {
                                    // Fallback to static page if dynamic generation fails
                                    pane_.showAi("ai/doc/html/api_key_management.html");
                                 }
                              }
                              
                              @Override
                              public void onError(ServerError error) {
                                 // Fallback to static page if there's an error
                                 pane_.showAi("ai/doc/html/api_key_management.html");
                              }
                           });
                        }
                     }
                  });
               }
               
               @Override
               public void onError(ServerError error) {
                  globalDisplay_.showErrorMessage("Error deleting conversation", 
                        "Failed to delete conversation: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            // Log error but continue with deletion - attachments cleanup failed but we still want to delete the conversation
            
            // Continue with conversation deletion even if attachment cleanup failed
            server_.deleteFolder(path, new ServerRequestCallback<Void>() {
               @Override
               public void onResponseReceived(Void response) {
                  
                  // Folder deleted successfully, now delete the conversation name
                  server_.deleteConversationName(conversationId, new ServerRequestCallback<Void>() {
                     @Override
                     public void onResponseReceived(Void response) {
                        
                        // Remove from the name cache
                        nameCache_.remove(conversationId);
                        
                        // Reload the menu to reflect changes
                        LinkMenu menu = pane_.getHistory();
                        if (menu instanceof AiToolbarLinkMenu) {
                           loadConversations((AiToolbarLinkMenu)menu);
                        }
                        
                        // Only navigate to API key management page if this was the current conversation
                        if (isCurrentConversation) {
                           // Use the proper method to navigate to API key management
                           server_.getApiKeyManagement(new ServerRequestCallback<ApiKeyManagementResult>() {
                              @Override
                              public void onResponseReceived(ApiKeyManagementResult result) {
                                 if (result.getSuccess()) {
                                    pane_.showAi(result.getPath());
                                 } else {
                                    // Fallback to static page if dynamic generation fails
                                    pane_.showAi("ai/doc/html/api_key_management.html");
                                 }
                              }
                              
                              @Override
                              public void onError(ServerError error) {
                                 // Fallback to static page if there's an error
                                 pane_.showAi("ai/doc/html/api_key_management.html");
                              }
                           });
                        }
                     }
                     
                     @Override
                     public void onError(ServerError error) {
                        // Non-critical error, just log it
                        
                        // Still reload menu
                        LinkMenu menu = pane_.getHistory();
                        if (menu instanceof AiToolbarLinkMenu) {
                           loadConversations((AiToolbarLinkMenu)menu);
                        }
                        
                        // Only navigate if this was the current conversation
                        if (isCurrentConversation) {
                           // Use the proper method to navigate to API key management
                           server_.getApiKeyManagement(new ServerRequestCallback<ApiKeyManagementResult>() {
                              @Override
                              public void onResponseReceived(ApiKeyManagementResult result) {
                                 if (result.getSuccess()) {
                                    pane_.showAi(result.getPath());
                                 } else {
                                    // Fallback to static page if dynamic generation fails
                                    pane_.showAi("ai/doc/html/api_key_management.html");
                                 }
                              }
                              
                              @Override
                              public void onError(ServerError error) {
                                 // Fallback to static page if there's an error
                                 pane_.showAi("ai/doc/html/api_key_management.html");
                              }
                           });
                        }
                     }
                  });
               }
               
               @Override
               public void onError(ServerError error) {
                  globalDisplay_.showErrorMessage("Error deleting conversation", 
                        "Failed to delete conversation: " + error.getMessage());
               }
            });
         }
      });
   }
   
   /**
    * Creates a new conversation by calling the server and updating the UI
    */
   public void createNewConversation() {      
      // Reset path variables to prevent using old conversation paths
      resetConversationPaths();
      
      // First, get a list of all conversations
      server_.listConversations(new ServerRequestCallback<JsArrayString>() {
         @Override
         public void onResponseReceived(JsArrayString conversations) {
            // If there are no conversations, create a new one
            if (conversations == null || conversations.length() == 0) {
               createBrandNewConversation();
               return;
            }
            
            // Find the maximum (most recent) index
            int maxIndex = -1;
            for (int i = 0; i < conversations.length(); i++) {
               try {
                  int index = Integer.parseInt(conversations.get(i));
                  if (index > maxIndex) {
                     maxIndex = index;
                  }
               } catch (NumberFormatException e) {
                  // Skip invalid indices
               }
            }
            
            // If we didn't find any valid indices, create a new one
            if (maxIndex == -1) {
               createBrandNewConversation();
               return;
            }
            
            // Check if the most recent conversation is empty
            final int conversationId = maxIndex;
            server_.isConversationEmpty(conversationId, new ServerRequestCallback<Boolean>() {
               @Override
               public void onResponseReceived(Boolean isEmpty) {
                  if (isEmpty) {
                     // The most recent conversation is empty, reuse it
                     switchToConversation(conversationId, false);
                     pane_.refreshAttachmentsList();
                     pane_.refreshImagesList();
                  } else {
                     // The conversation is not empty, create a new one
                     createBrandNewConversation();
                  }
               }
               
               @Override
               public void onError(ServerError error) {
                  // On error, fall back to creating a new conversation
                  createBrandNewConversation();
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            // On error, fall back to creating a new conversation
            createBrandNewConversation();
         }
      });
   }
   
   /**
    * Creates a brand new conversation without checking for existing empty ones
    */
   public void createBrandNewConversation() {      
      // CRITICAL: Clear UI context items but preserve attachment files
      // This ensures files are preserved when users start a new conversation
      AiContext aiContext = pane_.getAiContext();
      if (aiContext != null) {
         aiContext.clearAllContextItems();
      }
      
      // IMPORTANT: Do NOT delete attachment files when creating new conversations
      // Files should only be deleted when manually removed or conversation is deleted
      // AiPaneAttachments attachments = pane_.getAttachmentsManager();
      // if (attachments != null) {
      //    attachments.deleteAllAttachments(); // REMOVED - preserve files
      // }
      
      // Call the server to create a new conversation
      server_.createNewConversation(new ServerRequestCallback<CreateAiConversationResult>() {
         @Override
         public void onResponseReceived(CreateAiConversationResult result) {

            // Directly access properties instead of using getter methods
            boolean success = result.success;
            if (success) {
               // Set a default title for the new conversation
               pane_.updateTitle("New conversation");
               
               // Add the new conversation to the name cache
               nameCache_.put(result.index, "New conversation");
               
               // Update the conversation identifier for navigation tracking
               String conversationId = String.valueOf(result.index);
               // For new conversations, we should explicitly set navigated to false
               // so the handleAiSearchFromJS method works correctly with "Thinking..." message
               pane_.setNavigated(false);
               pane_.setLocation(conversationId, org.rstudio.core.client.Point.create(0, 0));
               
               // Refresh the conversations menu
               LinkMenu menu = pane_.getHistory();
               if (menu instanceof AiToolbarLinkMenu) {
                  loadConversations((AiToolbarLinkMenu)menu);
               }
               
               // CRITICAL: Refresh attachments list to show only attachments for the new conversation
               // Add slight delay to ensure R backend conversation index is synchronized
               com.google.gwt.user.client.Timer refreshTimer = new com.google.gwt.user.client.Timer() {
                  @Override
                  public void run() {
                     pane_.refreshAttachmentsList();
                     pane_.refreshImagesList();
                  }
               };
               refreshTimer.schedule(100); // 100ms delay
               
               // Load context items to synchronize UI with R session state
               if (pane_.getAiContext() != null) {
                  FlowPanel selectedFilesPanel = pane_.getToolbars() != null ? 
                     pane_.getToolbars().getSelectedFilesPanel() : null;
                  pane_.getAiContext().loadContextItems(selectedFilesPanel);
               }
            }
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to create a new conversation: " + error.getMessage());
         }
      });
   }
   
   /**
    * Stores the conversation title in localStorage for preservation
    * @param title The title to store
    */
   public native void storeConversationTitle(String title) /*-{
      try {
         // Store the title in localStorage so it can be accessed during page load
         $wnd.localStorage.setItem('ai_current_conversation_title', title);
      } catch(e) {
         // Ignore localStorage errors
         console.error("Error storing conversation title:", e);
      }
   }-*/;
   
   /**
    * Updates the conversation title in the pane and stores it
    * @param title The new title to set
    * @param conversationId The conversation ID to update (if -1, uses current conversation)
    */
   public void updateConversationTitle(String title, int conversationId) {
      if (title == null || title.isEmpty()) {
         return;
      }
      
      // If no conversation ID specified, use current
      if (conversationId == -1) {
         getCurrentConversationIndex(new ServerRequestCallback<Double>() {
            @Override
            public void onResponseReceived(Double currentIndex) {
               if (currentIndex != null && currentIndex.intValue() > 0) {
                  updateConversationTitleInternal(title, currentIndex.intValue());
               }
            }
            
            @Override
            public void onError(ServerError error) {
               // Handle error silently
            }
         });
      } else {
         updateConversationTitleInternal(title, conversationId);
      }
   }
   
   private void updateConversationTitleInternal(String title, int conversationId) {
      // Only proceed if we have a valid conversation
      if (conversationId > 0) {
         // Update the title in the UI
         pane_.updateTitle(title);
         
         // Store the title for session preservation
         storeConversationTitle(title);
         
         // Update the name in the cache
         nameCache_.put(conversationId, title);
         
         // Save to server
         setConversationName(conversationId, title);
      }
   }
   
   /**
    * Reset conversation state to prevent using data from previous conversations
    */
   private void resetConversationPaths() {
      // No longer needed - paths have been removed from the system
   }
   
   // OLD SCROLL SAVING METHODS REMOVED - scroll positions now saved by AiStreamingPanel
   
   /**
    * Get the pane instance
    * @return The AiPane instance
    */
      public AiPane getPane()
   {
      return pane_;
   }



   private final AiPane pane_;
   private final AiServerOperations server_;
   private final GlobalDisplay globalDisplay_;
   private final Map<Integer, String> nameCache_;
} 