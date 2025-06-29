/*
 * TextMatchResult.java
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

public class TextMatchResult extends JavaScriptObject
{
   protected TextMatchResult() {}
   
   public final native boolean hasMatch() /*-{
      return this.match || false;
   }-*/;
   
   public final native String getFilePath() /*-{
      return this.filePath || "";
   }-*/;
   
   public final native int getStartLine() /*-{
      return this.startLine || 0;
   }-*/;
   
   public final native int getEndLine() /*-{
      return this.endLine || 0;
   }-*/;
   
   public final native String getDocId() /*-{
      return this.docId || "";
   }-*/;
} 