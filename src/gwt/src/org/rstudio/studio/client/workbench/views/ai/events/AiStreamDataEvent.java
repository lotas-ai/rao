/*
 * AiStreamDataEvent.java
 *
 * Copyright (C) 2025 by William Nickols
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 */

package org.rstudio.studio.client.workbench.views.ai.events;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class AiStreamDataEvent extends GwtEvent<AiStreamDataEvent.Handler>
{
   public static final Type<Handler> TYPE = new Type<>();

   public interface Handler extends EventHandler
   {
      void onAiStreamData(AiStreamDataEvent event);
   }

   public static class Data
   {
      private String messageId;
      private String delta;
      private boolean isComplete;
      private boolean isEditFile;
      private String filename;
      private String requestId;
      private int sequence;
      private boolean isCancelled;
      private boolean isFunctionCall;
      
      public Data(String messageId, String delta, boolean isComplete)
      {
         this.messageId = messageId != null ? messageId : "";
         this.delta = delta != null ? delta : "";
         this.isComplete = isComplete;
         this.isEditFile = false;
         this.filename = null;
         this.requestId = null;
         this.sequence = 0;
         this.isCancelled = false;
         this.isFunctionCall = false;
      }
      
      public Data(String messageId, String delta, boolean isComplete, boolean isEditFile, String filename)
      {
         this.messageId = messageId != null ? messageId : "";
         this.delta = delta != null ? delta : "";
         this.isComplete = isComplete;
         this.isEditFile = isEditFile;
         this.filename = filename;
         this.requestId = null;
         this.sequence = 0;
         this.isCancelled = false;
         this.isFunctionCall = false;
      }
      
      public Data(String messageId, String delta, boolean isComplete, boolean isEditFile, String filename, int sequence)
      {
         this.messageId = messageId != null ? messageId : "";
         this.delta = delta != null ? delta : "";
         this.isComplete = isComplete;
         this.isEditFile = isEditFile;
         this.filename = filename;
         this.requestId = null;
         this.sequence = sequence;
         this.isCancelled = false;
         this.isFunctionCall = false;
      }
      
      public Data(String messageId, String delta, boolean isComplete, boolean isEditFile, String filename, int sequence, boolean isCancelled)
      {
         this.messageId = messageId != null ? messageId : "";
         this.delta = delta != null ? delta : "";
         this.isComplete = isComplete;
         this.isEditFile = isEditFile;
         this.filename = filename;
         this.requestId = null;
         this.sequence = sequence;
         this.isCancelled = isCancelled;
         this.isFunctionCall = false;
      }
      
      // Constructor with function call flag
      public Data(String messageId, String delta, boolean isComplete, boolean isEditFile, String filename, int sequence, boolean isCancelled, boolean isFunctionCall)
      {
         this.messageId = messageId != null ? messageId : "";
         this.delta = delta != null ? delta : "";
         this.isComplete = isComplete;
         this.isEditFile = isEditFile;
         this.filename = filename;
         this.requestId = null;
         this.sequence = sequence;
         this.isCancelled = isCancelled;
         this.isFunctionCall = isFunctionCall;
      }

      public String getMessageId()
      {
         return messageId;
      }

      public String getDelta()
      {
         return delta;
      }

      public boolean isComplete()
      {
         return isComplete;
      }
      
      public boolean isEditFile()
      {
         return isEditFile;
      }
      
      public String getFilename()
      {
         return filename;
      }
      
      public int getSequence()
      {
         return sequence;
      }
      
      public boolean isCancelled()
      {
         return isCancelled;
      }

      public boolean isFunctionCall()
      {
         return isFunctionCall;
      }
      
      public String getRequestId()
      {
         return requestId;
      }
      
      public void setRequestId(String requestId)
      {
         this.requestId = requestId;
      }
   }

   public AiStreamDataEvent(Data data)
   {
      data_ = data;
   }
   
   public AiStreamDataEvent(String messageId, String delta, boolean isComplete)
   {
      data_ = new Data(messageId, delta, isComplete);
   }
   
   public AiStreamDataEvent(String messageId, String delta, boolean isComplete, boolean isEditFile, String filename)
   {
      data_ = new Data(messageId, delta, isComplete, isEditFile, filename);
   }
   
   public AiStreamDataEvent(String messageId, String delta, boolean isComplete, boolean isEditFile, String filename, int sequence)
   {
      data_ = new Data(messageId, delta, isComplete, isEditFile, filename, sequence);
   }
   
   public AiStreamDataEvent(String messageId, String delta, boolean isComplete, boolean isEditFile, String filename, int sequence, boolean isCancelled)
   {
      data_ = new Data(messageId, delta, isComplete, isEditFile, filename, sequence, isCancelled);
   }

   // Constructor with function call flag
   public AiStreamDataEvent(String messageId, String delta, boolean isComplete, boolean isEditFile, String filename, int sequence, boolean isCancelled, boolean isFunctionCall)
   {
      data_ = new Data(messageId, delta, isComplete, isEditFile, filename, sequence, isCancelled, isFunctionCall);
   }

   public String getMessageId()
   {
      return data_.getMessageId();
   }

   public String getDelta()
   {
      return data_.getDelta();
   }

   public boolean isComplete()
   {
      return data_.isComplete();
   }
   
   public boolean isEditFile()
   {
      return data_.isEditFile();
   }
   
   public String getFilename()
   {
      return data_.getFilename();
   }
   
   public int getSequence()
   {
      return data_.getSequence();
   }
   
   public boolean isCancelled()
   {
      return data_.isCancelled();
   }

   public boolean isFunctionCall()
   {
      return data_.isFunctionCall();
   }
   
   public String getRequestId()
   {
      return data_.getRequestId();
   }
   
   public void setRequestId(String requestId)
   {
      data_.setRequestId(requestId);
   }

   @Override
   public Type<Handler> getAssociatedType()
   {
      return TYPE;
   }

   @Override
   protected void dispatch(Handler handler)
   {
      handler.onAiStreamData(this);
   }

   private final Data data_;
} 