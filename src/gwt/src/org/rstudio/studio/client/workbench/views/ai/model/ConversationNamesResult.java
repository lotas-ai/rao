/*
 * ConversationNamesResult.java
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
package org.rstudio.studio.client.workbench.views.ai.model;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

public class ConversationNamesResult extends JavaScriptObject
{
   protected ConversationNamesResult()
   {
   }
   
   public final native JsArray<ConversationNameEntry> getNames() /*-{
      return this;
   }-*/;
   
   public static class ConversationNameEntry extends JavaScriptObject
   {
      protected ConversationNameEntry() 
      {
      }
      
      public final native int getConversationId() /*-{
         return this.conversation_id;
      }-*/;
      
      public final native String getName() /*-{
         return this.name;
      }-*/;
   }
} 