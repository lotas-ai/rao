/*
 * ErrorFrame.java
 *
 * Copyright (C) 2022 by Posit Software, PBC
 *
 * Unless you have received this program directly from Posit Software pursuant
 * to the terms of a commercial license agreement with Posit Software, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

package org.rstudio.studio.client.common.debugging.model;

import org.rstudio.studio.client.workbench.views.environment.events.LineData;

public class ErrorFrame extends LineData
{
   protected ErrorFrame() {}
   
   public final native String getFunctionName() /*-{
      return this.func;
   }-*/;

   public final native String getFileName() /*-{
      return this.file.trim();
   }-*/;
}
