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
      private boolean isConsoleCmd;
      private boolean isTerminalCmd;
      private String filename;
      private String requestId;
      private int sequence;
      private boolean isCancelled;
      private boolean isFunctionCall;
      private boolean replaceContent;
      
      // Constructor with console and terminal command flags - the only Data constructor needed
      public Data(String messageId, String delta, boolean isComplete, boolean isEditFile, boolean isConsoleCmd, boolean isTerminalCmd, String filename, int sequence, boolean isCancelled, boolean isFunctionCall, boolean replaceContent)
      {
         this.messageId = messageId != null ? messageId : "";
         this.delta = delta != null ? delta : "";
         this.isComplete = isComplete;
         this.isEditFile = isEditFile;
         this.isConsoleCmd = isConsoleCmd;
         this.isTerminalCmd = isTerminalCmd;
         this.filename = filename;
         this.requestId = null;
         this.sequence = sequence;
         this.isCancelled = isCancelled;
         this.isFunctionCall = isFunctionCall;
         this.replaceContent = replaceContent;
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
      
      public boolean isConsoleCmd()
      {
         return isConsoleCmd;
      }
      
      public boolean isTerminalCmd()
      {
         return isTerminalCmd;
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

      public boolean getReplaceContent()
      {
         return replaceContent;
      }
   }

   // Constructor that takes a Data object directly - used by AiPane
   public AiStreamDataEvent(Data data)
   {
      data_ = data;
   }
   
   // Constructor with console and terminal command flags - used by ClientEventDispatcher
   public AiStreamDataEvent(String messageId, String delta, boolean isComplete, boolean isEditFile, boolean isConsoleCmd, boolean isTerminalCmd, String filename, int sequence, boolean isCancelled, boolean isFunctionCall, boolean replaceContent)
   {
      data_ = new Data(messageId, delta, isComplete, isEditFile, isConsoleCmd, isTerminalCmd, filename, sequence, isCancelled, isFunctionCall, replaceContent);
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
   
   public boolean isConsoleCmd()
   {
      return data_.isConsoleCmd();
   }
   
   public boolean isTerminalCmd()
   {
      return data_.isTerminalCmd();
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

   public boolean getReplaceContent()
   {
      return data_.getReplaceContent();
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