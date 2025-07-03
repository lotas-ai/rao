/*
 * Ai.java
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
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.inject.Inject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.GWT;

import org.rstudio.core.client.CsvReader;
import org.rstudio.core.client.CsvWriter;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.command.CommandBinder;
import org.rstudio.core.client.command.Handler;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.WorkbenchList;
import org.rstudio.studio.client.workbench.WorkbenchListManager;
import org.rstudio.studio.client.workbench.WorkbenchView;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.events.ActivatePaneEvent;
import org.rstudio.studio.client.workbench.model.Session;
import org.rstudio.studio.client.workbench.ui.PaneManager;
import org.rstudio.studio.client.workbench.views.BasePresenter;
import org.rstudio.studio.client.workbench.views.ai.events.ActivateAiEvent;
import org.rstudio.studio.client.workbench.views.ai.events.HasAiNavigateHandlers;
import org.rstudio.studio.client.workbench.views.ai.events.ShowAiEvent;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.studio.client.workbench.views.ai.model.Link;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.widget.Operation;
import org.rstudio.studio.client.workbench.views.ai.model.ApiKeyManagementResult;
import org.rstudio.studio.client.workbench.views.ai.model.CreateAiConversationResult;
import org.rstudio.studio.client.workbench.views.ai.AiPane;
import org.rstudio.studio.client.workbench.views.ai.AiPaneImages;
import org.rstudio.studio.client.workbench.views.ai.AiViewManager;
import org.rstudio.studio.client.common.FileDialogs;
import org.rstudio.studio.client.workbench.model.RemoteFileSystemContext;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.core.client.widget.ProgressIndicator;
import org.rstudio.core.client.widget.ProgressOperationWithInput;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.workbench.views.ai.events.AiRefreshEvent;

import java.util.ArrayList;
import java.util.Iterator;

public class Ai extends BasePresenter implements ShowAiEvent.Handler
{
   public interface Binder extends CommandBinder<Commands, Ai> {}

   public interface Display extends WorkbenchView,
      HasAiNavigateHandlers
   {
      String getUrl();
      String getDocTitle();
      void showAi(String aiURL);
      void print();
      void popout();
      void refresh();
      void focus();
      void focusSearchAi();

      LinkMenu getHistory();
      
      /**
       * Returns the menu used for managing attachments
       */
      LinkMenu getAttachments();

      /**
       * Returns true if this Ai pane has ever been navigated.
       */
      boolean navigated();

      // Add new refresh method to reload the iframe
      void refreshIframe();
   }

   public interface LinkMenu
   {
      void addLink(Link link);
      void removeLink(Link link);
      boolean containsLink(Link link);
      void clearLinks();
      ArrayList<Link> getLinks();
      HandlerRegistration addLinkSelectionHandler(SelectionHandler<String> handler);
   }

   @Inject
   public Ai(Display view,
               GlobalDisplay globalDisplay,
               AiServerOperations server,
               WorkbenchListManager listManager,
               Commands commands,
               final Session session,
               final EventBus events,
               FileDialogs fileDialogs,
               RemoteFileSystemContext fsContext)
   {
      super(view);
      server_ = server;
      aiHistoryList_ = listManager.getAiHistoryList();
      view_ = view;
      globalDisplay_ = globalDisplay;
      events_ = events;
      session_ = session;
      fileDialogs_ = fileDialogs;
      fsContext_ = fsContext;

      // Use GWT.create for Binder instead of injection
      ((Binder)GWT.create(Binder.class)).bind(commands, this);

      view_.addAiNavigateHandler(aiNavigateEvent ->
      {
         if (!historyInitialized_)
            return;

         CsvWriter csvWriter = new CsvWriter();
         csvWriter.writeValue(getApplicationRelativeAiUrl(aiNavigateEvent.getUrl()));
         csvWriter.writeValue(aiNavigateEvent.getTitle());
         aiHistoryList_.append(csvWriter.getValue());
         
         // Then refresh the history from available conversations
         refreshHistoryFromConversations();
      });
      
      SelectionHandler<String> navigator = new SelectionHandler<String>() {
         public void onSelection(SelectionEvent<String> selectionEvent)
         {
            // The selected item is now just a conversation ID, not a URL
            // Use proper conversation switching instead of trying to navigate to it as a URL
            String conversationId = selectionEvent.getSelectedItem();
            try {
               int index = Integer.parseInt(conversationId);
               AiPane pane = (AiPane)view_;
               AiPaneConversations conversationsManager = pane.getConversationsManager();
               if (conversationsManager != null) {
                  conversationsManager.switchToConversation(index, false);
               }
            } catch (NumberFormatException e) {
               Debug.log("Ai.java: Error parsing conversation ID: " + conversationId);
            }
         }
      };
      view_.getHistory().addLinkSelectionHandler(navigator);

      // initialize ai history by loading available conversations
      loadConversationsFromServer();
      
      // register for history list changes
      aiHistoryList_.addListChangedHandler(listChangedEvent -> {
         refreshHistoryFromConversations();
      });
   }
   
   private void loadConversationsFromServer() {
      server_.listConversations(new ServerRequestCallback<JsArrayString>() {
         @Override
         public void onResponseReceived(JsArrayString conversations) {
            updateHistoryFromConversations(conversations);
         }
         
         @Override
         public void onError(ServerError error) {
            // Initialize with empty history if we can't load conversations
            if (!historyInitialized_) {
               historyInitialized_ = true;
               if (view_.getHistory().getLinks().size() == 0 && !view_.navigated()) {
                  home();
               }
            }
         }
      });
   }
   
   private void refreshHistoryFromConversations() {
      loadConversationsFromServer();
   }
   
   private void updateHistoryFromConversations(JsArrayString conversations) {
      // clear existing
      final LinkMenu history = view_.getHistory();
      history.clearLinks();
      
      // Get the AiPaneConversations instance to access conversation names
      AiPane pane = (AiPane)view_;
      AiPaneConversations conversationsManager = pane.getConversationsManager();
      
      // add conversations to history
      for (int i = 0; i < conversations.length(); i++) {
         String conversationIndex = conversations.get(i);
         int index = Integer.parseInt(conversationIndex);
         
         // System uses DOM/GWT/streaming - just use conversation ID as identifier
         String conversationId = conversationIndex;
         
         // Get the custom name if available
         String title = conversationsManager.getConversationName(index);
         
         // add the link
         Link link = new Link(conversationId, title);
         history.addLink(link);
      }
      
      // one time init
      if (!historyInitialized_) {
         // mark us initialized
         historyInitialized_ = true;
         
         // Always show the API key management page on startup
         home();
      }
   }

   // Commands handled by Shim for activation from main menu context
   public void onAiHome() { 
      bringToFront(); 
      
      // Use view manager to switch to API management
      AiPane pane = (AiPane)view_;
      AiViewManager viewManager = pane.getToolbars().getViewManager();
      viewManager.showApiManagement();
   }
   public void onAiSearch() { bringToFront(); view_.focusSearchAi(); }

   public void onAiAttach() { 
      AiPane pane = (AiPane)view_;
            
      // Use the injected FileSystemContext or get one from RStudioGinjector if null
      RemoteFileSystemContext context = fsContext_;
      if (context == null) {
         context = RStudioGinjector.INSTANCE.getRemoteFileSystemContext();
      }
      
      // Open a file selection dialog
      fileDialogs_.openFile(
         "Choose File to Attach",
         context,
         FileSystemItem.createDir(session_.getSessionInfo().getInitialWorkingDir()),
         "",
         false,
         new ProgressOperationWithInput<FileSystemItem>() {
            public void execute(FileSystemItem input, ProgressIndicator indicator)
            {
               if (input == null)
                  return;
               
               indicator.onCompleted();
               
               // Get the selected file path
               final String filePath = input.getPath();
               
               // Call the server to save the attachment information
               server_.saveAiAttachment(filePath, new ServerRequestCallback<Void>() {
                  @Override
                  public void onResponseReceived(Void response) {
                     // Refresh the attachments list
                     AiPane pane = (AiPane)view_;
                     pane.refreshAttachmentsList();
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     // Show error to user
                     globalDisplay_.showErrorMessage("Error Attaching File", 
                                                   "Could not attach file: " + error.getMessage());
                  }
               });
            }
         });
   }

   public void onAiAttachImage() { 
      AiPane pane = (AiPane)view_;
            
      // Use the injected FileSystemContext or get one from RStudioGinjector if null
      RemoteFileSystemContext context = fsContext_;
      if (context == null) {
         context = RStudioGinjector.INSTANCE.getRemoteFileSystemContext();
      }
      
      // Open a file selection dialog for images
      fileDialogs_.openFile(
         "Choose Image to Attach",
         context,
         FileSystemItem.createDir(session_.getSessionInfo().getInitialWorkingDir()),
         "Image Files (*.png;*.jpg;*.jpeg;*.gif;*.bmp;*.svg)|*.png;*.jpg;*.jpeg;*.gif;*.bmp;*.svg",
         false,
         new ProgressOperationWithInput<FileSystemItem>() {
            public void execute(FileSystemItem input, ProgressIndicator indicator)
            {
               if (input == null)
                  return;
               
               indicator.onCompleted();
               
               // Get the selected file path
               final String imagePath = input.getPath();
                              
               // Use the images manager to attach the image (with limit checking)
               AiPaneImages imagesManager = pane.getImagesManager();
               
               if (imagesManager == null) {
                  globalDisplay_.showErrorMessage("Error", "Images manager is not initialized. This is a bug.");
                  return;
               }
               
               imagesManager.attachImage(imagePath);
            }
         });
   }

   @Handler public void onPrintAi() { view_.print(); }
   @Handler public void onAiPopout() { view_.popout(); }
   @Handler public void onRefreshAi() { 
      // Use view manager to create a new conversation and show conversation view
      AiPane pane = (AiPane)view_;
      AiViewManager viewManager = pane.getToolbars().getViewManager();
      
      // Force switch to conversation view and create new conversation
      viewManager.forceShowConversations();
      
      // Create new conversation via server
      server_.createNewConversation(new ServerRequestCallback<CreateAiConversationResult>() {
         @Override
         public void onResponseReceived(CreateAiConversationResult result) {
            if (result.success) {
               // Refresh conversation list and show the new conversation
               refreshHistoryFromConversations();
               // For new conversations, set up UI but don't try to load conversation log
               viewManager.loadConversationHistory(result.index, false);
            }
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to create new conversation: " + error.getMessage());
         }
      });
   }
   @Handler
   public void onClearAiHistory()
   {
      if (!historyInitialized_)
         return;

      // Show confirmation dialog before proceeding
      globalDisplay_.showYesNoMessage(
         GlobalDisplay.MSG_WARNING,
         "Clear AI History",
         "Are you sure you want to delete all AI conversations? This cannot be undone.",
         new Operation() {
            @Override
            public void execute() {
               // User confirmed, proceed with deletion
               server_.listConversations(new ServerRequestCallback<JsArrayString>() {
                  @Override
                  public void onResponseReceived(JsArrayString conversations) {
                     // If there are no conversations, nothing to do
                     if (conversations == null || conversations.length() == 0) {
                        aiHistoryList_.clear();
                        refreshHistoryFromConversations();
                        // Navigate to API key management page
                        home();
                        return;
                     }
                     
                     // Delete each conversation directory with attachment cleanup
                     for (int i = 0; i < conversations.length(); i++) {
                        String index = conversations.get(i);
                        final int conversationId = Integer.parseInt(index);
                        final String path = "conversation_" + index;
                        
                        // First cleanup attachments for this conversation
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
                                          // All cleanup completed for this conversation
                                       }
                                       
                                       @Override
                                       public void onError(ServerError error) {
                                          // Non-critical error, just log it
                                          Debug.log("Failed to delete conversation name: " + error.getMessage());
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
                              Debug.log("Failed to cleanup attachments for conversation " + conversationId + ": " + error.getMessage());
                              
                              // Continue with conversation deletion even if attachment cleanup failed
                              server_.deleteFolder(path, new ServerRequestCallback<Void>() {
                                 @Override
                                 public void onResponseReceived(Void response) {
                                    // Folder deleted successfully, now delete the conversation name
                                    server_.deleteConversationName(conversationId, new ServerRequestCallback<Void>() {
                                       @Override
                                       public void onResponseReceived(Void response) {
                                          // All cleanup completed for this conversation
                                       }
                                       
                                       @Override
                                       public void onError(ServerError error) {
                                          // Non-critical error, just log it
                                          Debug.log("Failed to delete conversation name: " + error.getMessage());
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
                     
                     // Clear the history list
                     aiHistoryList_.clear();
                     refreshHistoryFromConversations();
                     
                     // Navigate to API key management page after deletion
                     home();
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     globalDisplay_.showErrorMessage("Error", 
                           "Failed to list conversations: " + error.getMessage());
                  }
               });
            }
         },
         false  // Default is No
      );
   }

   public void onShowAi(ShowAiEvent event)
   {      
      showAi(event.getTopicUrl());
      bringToFront();
   }

   public void onActivateAi(ActivateAiEvent event)
   {
      bringToFront();
      view_.focus();
   }

   public void bringToFront()
   {
      events_.fireEvent(new ActivatePaneEvent(PaneManager.AI_PANE));
   }

   private void home()
   {
      AiPane pane = (AiPane)view_;
      AiViewManager viewManager = pane.getToolbars().getViewManager();
      viewManager.showApiManagement();
   }

   public Display getDisplay()
   {
      return view_;
   }

   private void showAi(String topicUrl)
   {
      String fullUrl = server_.getApplicationURL(topicUrl);
      view_.showAi(fullUrl);
   }

   private String getApplicationRelativeAiUrl(String aiUrl)
   {
      String appUrl = server_.getApplicationURL("");
      if (aiUrl.startsWith(appUrl) && !aiUrl.equals(appUrl))
         return StringUtil.substring(aiUrl, appUrl.length());
      else
         return aiUrl;
   }

   private Display view_;
   private AiServerOperations server_;
   private WorkbenchList aiHistoryList_;
   private boolean historyInitialized_;
   private GlobalDisplay globalDisplay_;
   private EventBus events_;
   private Session session_;
   private FileDialogs fileDialogs_;
   private RemoteFileSystemContext fsContext_;
}
