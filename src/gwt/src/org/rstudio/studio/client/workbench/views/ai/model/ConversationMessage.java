/*
 * ConversationMessage.java
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

public class ConversationMessage extends JavaScriptObject
{
   protected ConversationMessage()
   {
   }

   public final native int getId() /*-{
      return this.id || 0;
   }-*/;

   public final native String getRole() /*-{
      var role = this.role;
      if (role && typeof role === 'object' && role.length === 1) {
         return role[0];
      }
      return role || "";
   }-*/;

   public final native String getContent() /*-{
      var content = this.content;
      if (content && typeof content === 'object' && content.length === 1) {
         return content[0];
      }
      return content || "";
   }-*/;

   public final native String getType() /*-{
      return this.type || "";
   }-*/;

   public final native String getTimestamp() /*-{
      return this.timestamp || "";
   }-*/;

   public final native int getRelatedTo() /*-{
      return this.related_to || this.relatedTo || 0;
   }-*/;
} 