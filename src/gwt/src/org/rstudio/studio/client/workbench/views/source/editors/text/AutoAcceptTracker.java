/*
 * AutoAcceptTracker.java
 *
 * Copyright (C) 2025 by Lotas Inc.
 *
 * In-memory tracking system for auto-accepted AI file edits
 */
package org.rstudio.studio.client.workbench.views.source.editors.text;

import com.google.gwt.core.client.GWT;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.files.FileSystemItem;

import java.util.*;

public class AutoAcceptTracker
{
   private static AutoAcceptTracker instance_;
   
   private final Map<String, FileTrackingInfo> trackedFiles_ = new HashMap<>();
   
   public static class FileTrackingInfo
   {
      private final String filePath;
      private final String acceptedContent;
      private final List<String> conversationIndexes;
      
      public FileTrackingInfo(String filePath, String acceptedContent, String conversationIndex)
      {
         this.filePath = filePath;
         this.acceptedContent = acceptedContent;
         this.conversationIndexes = new ArrayList<>();
         this.conversationIndexes.add(conversationIndex);
      }
      
      public String getAcceptedContent() { return acceptedContent; }
      
      public FileTrackingInfo withUpdatedContent(String newAcceptedContent)
      {
         FileTrackingInfo updated = new FileTrackingInfo(filePath, newAcceptedContent, conversationIndexes.get(0));
         updated.conversationIndexes.clear();
         updated.conversationIndexes.addAll(this.conversationIndexes);
         return updated;
      }
      
      public FileTrackingInfo withAddedConversation(String conversationIndex)
      {
         if (!conversationIndexes.contains(conversationIndex))
         {
            conversationIndexes.add(conversationIndex);
         }
         return this;
      }
   }
   
   private AutoAcceptTracker()
   {
   }
   
   public static AutoAcceptTracker getInstance()
   {
      if (instance_ == null)
      {
         instance_ = new AutoAcceptTracker();
      }
      return instance_;
   }
   
   public void trackEdit(String filePath, String acceptedContent, String conversationIndex)
   {
      String normalizedPath = normalizePath(filePath);
      
      FileTrackingInfo existing = trackedFiles_.get(normalizedPath);
      if (existing != null)
      {
         existing.withAddedConversation(conversationIndex);
      }
      else
      {
         trackedFiles_.put(normalizedPath, new FileTrackingInfo(normalizedPath, acceptedContent, conversationIndex));
      }
   }
   
   public FileTrackingInfo getTrackingInfo(String filePath)
   {
      String normalizedPath = normalizePath(filePath);
      return trackedFiles_.get(normalizedPath);
   }
   
   public boolean isTracking(String filePath)
   {
      String normalizedPath = normalizePath(filePath);
      return trackedFiles_.containsKey(normalizedPath);
   }
   
   public void updateAcceptedContent(String filePath, String newAcceptedContent)
   {
      String normalizedPath = normalizePath(filePath);
      FileTrackingInfo existing = trackedFiles_.get(normalizedPath);
      if (existing != null)
      {
         trackedFiles_.put(normalizedPath, existing.withUpdatedContent(newAcceptedContent));
      }
   }
   
   public void removeTracking(String filePath)
   {
      String normalizedPath = normalizePath(filePath);
      trackedFiles_.remove(normalizedPath);
   }
   
   public void clearAll()
   {
      trackedFiles_.clear();
   }
   
   public Set<String> getTrackedFiles()
   {
      // Return as TreeSet for consistent alphabetical ordering
      return new TreeSet<>(trackedFiles_.keySet());
   }
   
   /**
    * Validate and clean up tracking for a specific file.
    * Removes tracking if content matches (having currentContent proves file exists).
    */
   public void validateTracking(String filePath, String currentContent)
   {
      String normalizedPath = normalizePath(filePath);
      FileTrackingInfo trackingInfo = trackedFiles_.get(normalizedPath);
      
      if (trackingInfo == null)
      {
         return;
      }
      
      // If we have currentContent, the file exists and is open
      // Check if content now matches - if so, remove tracking
      if (currentContent != null && currentContent.equals(trackingInfo.getAcceptedContent()))
      {
         removeTracking(filePath);
      }
   }
   
   private String normalizePath(String path)
   {
      if (path == null || path.isEmpty())
      {
         return path;
      }
      
      // Just use FileSystemItem for basic separator normalization
      // Path expansion (tilde) will be handled when paths come from R
      FileSystemItem item = FileSystemItem.createFile(path);
      return item.getPath();
   }
}


