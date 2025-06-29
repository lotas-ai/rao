/*
 * ApiKeyManagementResult.java
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

public class ApiKeyManagementResult extends JavaScriptObject
{
   protected ApiKeyManagementResult()
   {
   }
   
   public final native boolean getSuccess() /*-{
      return this.success;
   }-*/;
   
   public final native String getPath() /*-{
      return this.path;
   }-*/;
} 