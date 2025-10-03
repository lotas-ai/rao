/*
 * AuthSessionResult.java
 *
 * Copyright (C) 2025 by Lotas Inc.
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

public class AuthSessionResult extends JavaScriptObject
{
   protected AuthSessionResult()
   {
   }

   public final native boolean isComplete() /*-{
      return this.complete || false;
   }-*/;

   public final native String getApiKey() /*-{
      return this.apiKey || "";
   }-*/;

   public final native String getError() /*-{
      return this.error || "";
   }-*/;
}