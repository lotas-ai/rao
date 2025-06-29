/*
 * AiContext.java
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

import org.rstudio.core.client.Debug;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;

public class AiContext
{
   @Inject
   public AiContext(AiServerOperations server,
                   GlobalDisplay globalDisplay)
   {
      server_ = server;
      globalDisplay_ = globalDisplay;      
   }

   // Add this method to load existing context items
   public void loadContextItems(final FlowPanel selectedFilesPanel) {
      
      // Prevent concurrent loads to avoid duplicates
      if (isLoading_) {
         return;
      }
      isLoading_ = true;
      
      // If selectedFilesPanel is null, find it in the DOM
      final FlowPanel effectivePanel;
      if (selectedFilesPanel == null) {
         effectivePanel = findSelectedFilesPanel();
         if (effectivePanel == null) {
            isLoading_ = false;
            return;
         }
      } else {
         effectivePanel = selectedFilesPanel;
      }
      
      server_.getContextItems(new ServerRequestCallback<JsArrayString>() {
         @Override
         public void onResponseReceived(JsArrayString contextItems) {
            effectivePanel.clear();
            
            if (contextItems != null && contextItems.length() > 0) {
               for (int i = 0; i < contextItems.length(); i++) {
                  String pathWithDisplay = contextItems.get(i);
                  
                  // Check if this has line number information (format: path|displayName)
                  String filePath;
                  String displayName;
                  
                  if (pathWithDisplay.contains("|")) {
                     String[] parts = pathWithDisplay.split("\\|", 2);
                     filePath = parts[0];
                     displayName = parts[1]; // This includes line numbers like "file.R (1-8)"
                  } else {
                     filePath = pathWithDisplay;
                     displayName = null; // Will use basename from FileSystemItem
                  }
                  
                  FileSystemItem fileItem = FileSystemItem.createFile(filePath);
                  if (fileItem != null) {
                     createFileItemElement(fileItem, displayName, effectivePanel);
                  }
               }
               
               // Update button text after loading items
               updateAttachButtonText();
            }
            
            isLoading_ = false;
         }

         @Override
         public void onError(ServerError error) {
            try {
               // Update button text even on error to ensure it's in the correct state
               updateAttachButtonText();
            } finally {
               isLoading_ = false;
            }
         }
      });
   }
   
   /**
    * Helper method to find the selected files panel in the DOM
    * This method is more robust and can handle different DOM structures
    */
   private FlowPanel findSelectedFilesPanel() {
      
      // Search through existing widgets in RootPanel to find the selected files panel
      int rootPanelWidgetCount = RootPanel.get().getWidgetCount();
      
      for (int i = 0; i < rootPanelWidgetCount; i++) {
         Widget widget = RootPanel.get().getWidget(i);
         if (widget instanceof FlowPanel) {
            Element element = widget.getElement();
            String className = element.getClassName();
            
            if (className != null && className.contains("ai-selected-files-panel")) {
               return (FlowPanel)widget;
            }
            
            // Also check children of this widget
            if (widget instanceof FlowPanel) {
               FlowPanel panel = (FlowPanel)widget;
               for (int j = 0; j < panel.getWidgetCount(); j++) {
                  Widget child = panel.getWidget(j);
                  if (child instanceof FlowPanel) {
                     Element childElement = child.getElement();
                     String childClassName = childElement.getClassName();
                     if (childClassName != null && childClassName.contains("ai-selected-files-panel")) {
                        return (FlowPanel)child;
                     }
                  }
               }
            }
         }
      }
      
      // If not found in RootPanel, try to get it from the current AiPane instance
      AiPane currentPane = AiPane.getCurrentInstance();
      if (currentPane != null) {
         AiToolbars toolbars = currentPane.getToolbars();
         if (toolbars != null) {
            FlowPanel selectedFilesPanel = toolbars.getSelectedFilesPanel();
            if (selectedFilesPanel != null) {
               return selectedFilesPanel;
            }
         }
      }
      
      return null;
   }

   // Method for JS callback to call
   public void handleBrowseForFile() {
      // Find the selected files panel in the DOM
      FlowPanel selectedFilesPanel = findSelectedFilesPanel();
      
      if (selectedFilesPanel != null) {
         handleBrowseForFile(selectedFilesPanel);
      } else {
         globalDisplay_.showMessage(GlobalDisplay.MSG_INFO, 
                                  "Select File", 
                                  "Could not locate the files panel. Please try again.");
      }
   }

   public void handleBrowseForFile(final FlowPanel selectedFilesPanel) {
      
      // Store panel reference for later use in callback
      final FlowPanel effectivePanel = selectedFilesPanel != null ? selectedFilesPanel : findSelectedFilesPanel();
      
      if (effectivePanel == null) {
         globalDisplay_.showMessage(GlobalDisplay.MSG_WARNING, 
                                  "Error", 
                                  "Could not locate the files panel. Please reload the AI pane and try again.");
         return;
      }
      
      server_.browseForFile(new ServerRequestCallback<FileSystemItem>() {
         @Override
         public void onResponseReceived(FileSystemItem item) {
            if (item != null) {
               
               // Add the file to the server-side context list
               final String itemPath = item.getPath();
               
               server_.addContextItem(itemPath, new ServerRequestCallback<Boolean>() {
                  @Override
                  public void onResponseReceived(Boolean success) {
                     
                     if (success) {
                        // Reload all context items to reflect server state
                        // This ensures the UI matches what's in the server
                        loadContextItems(effectivePanel);
                     } else {
                        // Update button text even on failure to ensure correct state
                        updateAttachButtonText();
                     }
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     // Update button text even on error to ensure correct state
                     updateAttachButtonText();
                  }
               });
            }
         }

         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to select file: " + error.getMessage());
         }
      });
   }
   
   /**
    * Handles a dropped file by adding it to the context
    * This is called from the drag and drop functionality in AiToolbars
    * @param filePath The path of the dropped file
    * @param selectedFilesPanel The panel to update with the new file
    */
   public void handleDroppedFile(String filePath, FlowPanel selectedFilesPanel) {
      if (filePath == null || filePath.isEmpty()) {
         return;
      }
      
      // Store panel reference for later use in callback
      final FlowPanel effectivePanel = selectedFilesPanel != null ? selectedFilesPanel : findSelectedFilesPanel();
      
      if (effectivePanel == null) {
         return;
      }
      
      // Add the file to the server-side context list
      server_.addContextItem(filePath, new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean success) {
            if (success) {
               // Reload all context items to reflect server state
               // This ensures the UI matches what's in the server
               loadContextItems(effectivePanel);
            } else {
               // Update button text even on failure to ensure correct state
               updateAttachButtonText();
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // Update button text even on error to ensure correct state
            updateAttachButtonText();
         }
      });
   }
   
   // Add context item to R backend
   private void addContextItem(FileSystemItem item) {
      if (item == null) {
         return;
      }
      
      String path = item.getPath();
      if (path == null || path.isEmpty()) {
         return;
      }
            
      // Check if file exists locally
      if (!item.exists()) {
         // Warning: File does not exist locally: path
      }
      
      // Save a reference to the original file item for later use
      final FileSystemItem fileItem = item;
      
      // First check if this file is already in the context
      server_.getContextItems(new ServerRequestCallback<JsArrayString>() {
         @Override
         public void onResponseReceived(JsArrayString items) {
            // Check if the file is already in the context
            boolean isDuplicate = false;
            for (int i = 0; i < items.length(); i++) {
               String existingItem = items.get(i);
               
               // Check if this is an exact duplicate
               // We should only consider it a duplicate if:
               // 1. Both are regular files with same path, OR
               // 2. Both are files with line numbers and have same path AND same line range
               
               if (existingItem.contains("|")) {
                  // This existing item has line numbers - cannot be duplicate of regular file
                  // Regular files and files with line numbers are always different items
                  continue;
               } else {
                  // This existing item is a regular file, check for exact path match
                  if (existingItem.equals(path)) {
                     isDuplicate = true;
                     break;
                  }
               }
            }
            
            if (isDuplicate) {
               // Silently ignore duplicates - no warning message
               // Ignoring duplicate context item: path
               return;
            }
            
            // If not a duplicate, proceed with adding
            server_.addContextItem(path, new ServerRequestCallback<Boolean>() {
               @Override
               public void onResponseReceived(Boolean success) {
                  if (!success) {
                     // Show error to user
                     globalDisplay_.showMessage(GlobalDisplay.MSG_WARNING,
                                              "Context Error",
                                              "Failed to add file to context: " + fileItem.getName());
                  } else {
                     // No need to reload the UI since we've already added the item to the UI
                     // in the handleBrowseForFile method. This avoids duplication issues.
                  }
               }

               @Override
               public void onError(ServerError error) {            
                  // Show error to user
                  globalDisplay_.showErrorMessage("Context Error", 
                                               "Failed to add file to context: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            // On error, proceed with add attempt
            server_.addContextItem(path, new ServerRequestCallback<Boolean>() {
               @Override
               public void onResponseReceived(Boolean success) {
                  if (!success) {
                     globalDisplay_.showMessage(GlobalDisplay.MSG_WARNING, 
                                              "Context Error",
                                              "Failed to add file to context: " + fileItem.getName());
                  }
               }
               
               @Override
               public void onError(ServerError error) {
                  globalDisplay_.showErrorMessage("Context Error", 
                                               "Failed to add file to context: " + error.getMessage());
               }
            });
         }
      });
   }
   
   // Remove context item from R backend
   private void removeContextItem(String pathOrUniqueId) {
      server_.removeContextItem(pathOrUniqueId, new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean success) {
            if (!success) {
               // Error: Failed to remove context item - item may not exist in R backend
               // This could happen if the UI is out of sync with the R backend
               // Try reloading context items to sync the UI
               FlowPanel panel = findSelectedFilesPanel();
               if (panel != null) {
                  loadContextItems(panel);
               }
            }
         }

         @Override
         public void onError(ServerError error) {
            // Error: Error removing context item: error.getMessage()
            // On error, try reloading context items to sync the UI
            FlowPanel panel = findSelectedFilesPanel();
            if (panel != null) {
               loadContextItems(panel);
            }
         }
      });
   }
   
   /**
    * Handle adding lines from a file as context with option to remove specific pasted text
    * This method is called when text is matched in an open document
    */
   public void handleAddLinesContext(String filePath, int startLine, int endLine, FlowPanel selectedFilesPanel, String pastedText) {
      // Get the file item for display
      FileSystemItem fileItem = FileSystemItem.createFile(filePath);
      
      // Add the file to context with line numbers
      if (fileItem != null) {
         // Add to server first, then add to UI when successful
         server_.addContextLines(filePath, startLine, endLine, new ServerRequestCallback<Boolean>() {
            @Override
            public void onResponseReceived(Boolean success) {
               if (success) {
                  // Call addSelectedFile with line numbers
                  addSelectedFile(fileItem, selectedFilesPanel, startLine, endLine);
               }
               
               // Remove the pasted text from the search box if provided
               if (pastedText != null && !pastedText.isEmpty()) {
                  removeTextFromSearchBox(pastedText);
               }
            }
            
            @Override
            public void onError(ServerError error) {
               // Even on error, try to remove pasted text if provided
               if (pastedText != null && !pastedText.isEmpty()) {
                  removeTextFromSearchBox(pastedText);
               }
            }
         });
      } else {
         // Even if file doesn't exist, try to remove pasted text if provided
         if (pastedText != null && !pastedText.isEmpty()) {
            removeTextFromSearchBox(pastedText);
         }
      }
   }
   
   // Helper method to add a selected file to the panel
   public void addSelectedFile(FileSystemItem item, FlowPanel selectedFilesPanel) {
      if (selectedFilesPanel == null || item == null) {
         return;
      }
      
      addSelectedFile(item, selectedFilesPanel, -1, -1);
   }
   
   // Helper method to add a selected file to the panel with optional line numbers
   public void addSelectedFile(FileSystemItem item, FlowPanel selectedFilesPanel, int startLine, int endLine) {
      if (selectedFilesPanel == null || item == null) {
         return;
      }
      
      // Check if this file is already in the UI panel
      String itemPath = item.getPath();
      boolean isDuplicate = false;
      
      // Determine if we're adding with line numbers
      boolean hasLineNumbers = (startLine > 0 && endLine >= startLine);
      
      // Generate the unique identifier for this item
      String uniqueId = hasLineNumbers ? 
            itemPath + "|" + item.getName() + " (" + (startLine == endLine ? startLine : startLine + "-" + endLine) + ")" : 
            itemPath;
      
      // Check for duplicates using unique ID
      for (int i = 0; i < selectedFilesPanel.getWidgetCount(); i++) {
         Widget widget = selectedFilesPanel.getWidget(i);
         Element element = widget.getElement();
         String existingId = element.getAttribute("data-unique-id");
         if (existingId != null && existingId.equals(uniqueId)) {
            isDuplicate = true;
            break;
         }
      }
      
      // If it's a duplicate, don't add it again
      if (isDuplicate) {
         return;
      }
      
      // Determine if this is a directory
      boolean isDirectory = item.isDirectory();
      
      // Create a container for the file item
      FlowPanel fileItemContainer = new FlowPanel();
      fileItemContainer.setStyleName("ai-context-item");
      fileItemContainer.getElement().setAttribute("data-path", itemPath);
      fileItemContainer.getElement().setAttribute("data-unique-id", uniqueId);
      
      // Use explicit styles for better visibility but more compact
      Element containerElement = fileItemContainer.getElement();
      containerElement.getStyle().setProperty("display", "inline-flex");
      containerElement.getStyle().setProperty("alignItems", "center");
      containerElement.getStyle().setProperty("backgroundColor", "white");
      containerElement.getStyle().setProperty("border", "1px solid #cccccc");
      containerElement.getStyle().setProperty("borderRadius", "3px");
      containerElement.getStyle().setProperty("padding", "1px 4px");
      containerElement.getStyle().setProperty("margin", "0 4px 3px 0");
      containerElement.getStyle().setProperty("maxWidth", "175px");
      containerElement.getStyle().setProperty("height", "18px");
      containerElement.getStyle().setProperty("overflow", "hidden");
      containerElement.getStyle().setProperty("whiteSpace", "nowrap");
      containerElement.getStyle().setProperty("fontSize", "11px");
      containerElement.getStyle().setProperty("verticalAlign", "middle");
      containerElement.getStyle().setProperty("flexShrink", "0"); // Prevent file items from shrinking in the scroll container
      
      // Create label for file name - with line numbers if provided
      String displayText;
      if (hasLineNumbers) {
         // Format with line numbers
         if (startLine == endLine) {
            displayText = item.getName() + " (" + startLine + ")";
         } else {
            displayText = item.getName() + " (" + startLine + "-" + endLine + ")";
         }
      } else {
         // Just the file name
         displayText = item.getName();
      }
      
      Label fileNameLabel = new Label(displayText);
      fileNameLabel.setStyleName("ai-context-filename");
      
      // Style the filename label
      Element fileNameElement = fileNameLabel.getElement();
      fileNameElement.getStyle().setProperty("overflow", "hidden");
      fileNameElement.getStyle().setProperty("textOverflow", "ellipsis");
      fileNameElement.getStyle().setProperty("whiteSpace", "nowrap");
      fileNameElement.getStyle().setProperty("maxWidth", "150px");
      fileNameElement.getStyle().setProperty("lineHeight", "16px");
      fileNameElement.getStyle().setProperty("paddingTop", "0");
      fileNameElement.getStyle().setProperty("fontSize", "11px");
      
      fileItemContainer.add(fileNameLabel);
      
      // Create remove button - make it smaller
      Label removeButton = new Label("×");
      removeButton.setStyleName("ai-context-remove-button");
      
      // Style the remove button
      Element removeElement = removeButton.getElement();
      removeElement.getStyle().setProperty("marginLeft", "3px");
      removeElement.getStyle().setProperty("cursor", "pointer");
      removeElement.getStyle().setProperty("color", "#999999");
      removeElement.getStyle().setProperty("fontWeight", "bold");
      removeElement.getStyle().setProperty("fontSize", "12px");
      removeElement.getStyle().setProperty("lineHeight", "16px");
      removeElement.getStyle().setProperty("width", "12px");
      removeElement.getStyle().setProperty("textAlign", "center");
      
      // Add click handler to remove the file item
      final String finalUniqueId = uniqueId;
      removeButton.addClickHandler(new ClickHandler() {
         @Override
         public void onClick(ClickEvent event) {
            fileItemContainer.removeFromParent();
            removeContextItem(finalUniqueId);
            // Update button text after removing item
            updateAttachButtonText();
         }
      });
      
      fileItemContainer.add(removeButton);
      
      // Add the file item to the selected files panel
      selectedFilesPanel.add(fileItemContainer);
      
      // Scroll to show the newly added item
      scrollToShowNewItem(selectedFilesPanel);
      
      // Update the @ button text
      updateAttachButtonText();
   }
   
   /**
    * Scrolls the selected files panel to ensure the most recently added item is visible
    * @param selectedFilesPanel The panel containing the context items
    */
   private void scrollToShowNewItem(final FlowPanel selectedFilesPanel) {
      if (selectedFilesPanel == null || selectedFilesPanel.getWidgetCount() == 0) {
         return;
      }
      
      // Schedule a deferred command to run after the UI has been updated
      Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
         @Override
         public void execute() {
            // Use native JS to scroll the container to show the latest item
            scrollContainerToEnd(selectedFilesPanel.getElement());
         }
      });
   }
   
   /**
    * Native method to scroll a container to show its rightmost content
    */
   private native void scrollContainerToEnd(Element container) /*-{
      if (container) {
         // Scroll to the right end of the container
         container.scrollLeft = container.scrollWidth;
      }
   }-*/;

   /**
    * Creates a file item UI element without duplicate checking
    * This is used by loadContextItems to directly create items from the server list
    */
   private void createFileItemElement(FileSystemItem item, String displayName, FlowPanel selectedFilesPanel) {
      
      String itemPath = item.getPath();
      String effectiveDisplayName = displayName != null ? displayName : item.getName();
      
      // Create unique ID: if displayName is provided, it means this has line numbers
      String uniqueId;
      if (displayName != null) {
         uniqueId = itemPath + "|" + displayName; // Include display name to distinguish line ranges
      } else {
         uniqueId = itemPath; // For regular files, just use the path
      }
      
      // Check if an item with this exact unique ID already exists to prevent duplicates
      for (int i = 0; i < selectedFilesPanel.getWidgetCount(); i++) {
         Widget widget = selectedFilesPanel.getWidget(i);
         Element element = widget.getElement();
         String existingId = element.getAttribute("data-unique-id");
         if (existingId != null && existingId.equals(uniqueId)) {
            // Exact item already exists, don't add duplicate
            return;
         }
      }
      
      // Determine if this is a directory
      boolean isDirectory = item.isDirectory();
      
      // Create a container for the file item
      FlowPanel fileItemContainer = new FlowPanel();
      fileItemContainer.setStyleName("ai-context-item");
      fileItemContainer.getElement().setAttribute("data-path", itemPath);
      fileItemContainer.getElement().setAttribute("data-unique-id", uniqueId);
      
      // Use explicit styles for better visibility but more compact
      Element containerElement = fileItemContainer.getElement();
      containerElement.getStyle().setProperty("display", "inline-flex");
      containerElement.getStyle().setProperty("alignItems", "center");
      containerElement.getStyle().setProperty("backgroundColor", isDirectory ? "#F0F5FF" : "white");
      containerElement.getStyle().setProperty("border", "1px solid #cccccc");
      containerElement.getStyle().setProperty("borderRadius", "3px");
      containerElement.getStyle().setProperty("padding", "1px 4px");
      containerElement.getStyle().setProperty("margin", "0 4px 3px 0");
      containerElement.getStyle().setProperty("maxWidth", "175px");
      containerElement.getStyle().setProperty("height", "18px");
      containerElement.getStyle().setProperty("overflow", "hidden");
      containerElement.getStyle().setProperty("whiteSpace", "nowrap");
      containerElement.getStyle().setProperty("fontSize", "11px");
      containerElement.getStyle().setProperty("verticalAlign", "middle");
      containerElement.getStyle().setProperty("flexShrink", "0"); // Prevent file items from shrinking in the scroll container
            
      // Create label for file name
      Label fileNameLabel = new Label(effectiveDisplayName);
      fileNameLabel.setStyleName("ai-context-filename");
      
      // Style the filename label
      Element fileNameElement = fileNameLabel.getElement();
      fileNameElement.getStyle().setProperty("overflow", "hidden");
      fileNameElement.getStyle().setProperty("textOverflow", "ellipsis");
      fileNameElement.getStyle().setProperty("whiteSpace", "nowrap");
      fileNameElement.getStyle().setProperty("maxWidth", isDirectory ? "130px" : "150px");
      fileNameElement.getStyle().setProperty("lineHeight", "16px");
      fileNameElement.getStyle().setProperty("paddingTop", "0");
      fileNameElement.getStyle().setProperty("fontSize", "11px");
      
      fileItemContainer.add(fileNameLabel);
      
      // Create remove button - make it smaller
      Label removeButton = new Label("×");
      removeButton.setStyleName("ai-context-remove-button");
      
      // Style the remove button
      Element removeElement = removeButton.getElement();
      removeElement.getStyle().setProperty("marginLeft", "3px");
      removeElement.getStyle().setProperty("cursor", "pointer");
      removeElement.getStyle().setProperty("color", "#999999");
      removeElement.getStyle().setProperty("fontWeight", "bold");
      removeElement.getStyle().setProperty("fontSize", "12px");
      removeElement.getStyle().setProperty("lineHeight", "16px");
      removeElement.getStyle().setProperty("width", "12px");
      removeElement.getStyle().setProperty("textAlign", "center");
      
      // Add click handler to remove the file item
      final String finalUniqueId = uniqueId; // Use the unique ID instead of just path
      removeButton.addClickHandler(new ClickHandler() {
         @Override
         public void onClick(ClickEvent event) {
            fileItemContainer.removeFromParent();
            removeContextItem(finalUniqueId); // Pass unique ID instead of path
            // Update button text after removing item
            updateAttachButtonText();
         }
      });
      
      fileItemContainer.add(removeButton);
      
      // Add the file item to the selected files panel
      selectedFilesPanel.add(fileItemContainer);
   }

   /**
    * Updates the attach button text based on whether there are context items
    */
   private void updateAttachButtonText() {
      // Get the current AiPane instance
      AiPane currentPane = AiPane.getCurrentInstance();
      if (currentPane != null) {
         AiToolbars toolbars = currentPane.getToolbars();
         if (toolbars != null) {
            toolbars.updateAttachButtonText();
         }
      }
   }

   /**
    * Removes specific text from the AI search box
    */
   private void removeTextFromSearchBox(String textToRemove) {
      // Find the search input element with "Ask anything" placeholder
      Element searchBox = findSearchInputElement();
      if (searchBox != null) {
         // Remove only the specific text
         removeSpecificTextFromElement(searchBox, textToRemove);
      }
   }
   
   /**
    * Removes specific text from an input or textarea element
    */
   private native void removeSpecificTextFromElement(Element element, String textToRemove) /*-{
      if (element) {
         var currentValue = element.value || "";
         
         // Only remove the exact text if it exists
         var newValue = currentValue.replace(textToRemove, "");
         
         // Only update if there was a change
         if (newValue !== currentValue) {
            element.value = newValue;
            
            // Trigger input event to ensure UI is updated properly
            var event = new Event('input', {
               bubbles: true,
               cancelable: true
            });
            element.dispatchEvent(event);
         }
      }
   }-*/;

   /**
    * Clears the text in the AI search box
    */
   private void clearSearchBoxText() {
      // Find the search input element with "Ask anything" placeholder
      Element searchBox = findSearchInputElement();
      if (searchBox != null) {
         // Clear the input value
         setElementValue(searchBox, "");
      }
   }
   
   /**
    * Finds the search input element in the DOM
    */
   private native Element findSearchInputElement() /*-{
      // First try to find an input with the "Ask anything" placeholder
      var inputs = $doc.getElementsByTagName("input");
      for (var i = 0; i < inputs.length; i++) {
         if (inputs[i].placeholder === "Ask anything") {
            return inputs[i];
         }
      }
      
      // If input not found, also check for textarea (in case it's been converted)
      var textareas = $doc.getElementsByTagName("textarea");
      for (var i = 0; i < textareas.length; i++) {
         if (textareas[i].placeholder === "Ask anything") {
            return textareas[i];
         }
      }
      
      return null;
   }-*/;
   
   /**
    * Sets the value of an input or textarea element
    */
   private native void setElementValue(Element element, String value) /*-{
      if (element) {
         element.value = value;
         
         // Trigger input event to ensure UI is updated properly
         var event = new Event('input', {
            bubbles: true,
            cancelable: true
         });
         element.dispatchEvent(event);
      }
   }-*/;

   /**
    * Clears all context items from the UI and triggers backend cleanup
    * This should be called when starting new conversations
    */
   public void clearAllContextItems() {
      
      // Find the selected files panel
      FlowPanel selectedFilesPanel = findSelectedFilesPanel();
      
      if (selectedFilesPanel != null) {
         selectedFilesPanel.clear();
         
         // Force update the attach button text immediately after clearing
         updateAttachButtonText();
      }
      
      // CRITICAL: Also clear server-side context items to ensure consistency
      server_.clearContextItems(new ServerRequestCallback<Void>() {
         @Override
         public void onResponseReceived(Void success) {
            
            // After server clear, reload the UI to ensure it's in sync
            FlowPanel panel = findSelectedFilesPanel();
            if (panel != null) {
               loadContextItems(panel);
            }
         }
         
         @Override
         public void onError(ServerError error) {
         }
      });
      
      // NOTE: Context items are different from attachments
      // We should NOT delete attachment files when clearing context items
      // Attachments should only be deleted when:
      // 1. User manually clicks X next to each file
      // 2. User explicitly clicks "delete all attachments"
      // 3. User deletes a conversation
   }

   private final AiServerOperations server_;
   private final GlobalDisplay globalDisplay_;
   private boolean isLoading_ = false;
} 