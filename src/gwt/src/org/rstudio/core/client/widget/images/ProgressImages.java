/*
 * ProgressImages.java
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
package org.rstudio.core.client.widget.images;

import com.google.gwt.user.client.ui.Image;
import org.rstudio.core.client.resources.CoreResources;

public class ProgressImages
{
   public static Image createSmall()
   {
      return new Image(CoreResources.INSTANCE.progress());
   }
   
   public static Image createSmallGray()
   {
      return new Image(CoreResources.INSTANCE.progress_gray());
   }
   
   public static Image createLarge()
   {
      return new Image(CoreResources.INSTANCE.progress_large()); 
   }

   public static Image createLargeGray()
   {
      return new Image(CoreResources.INSTANCE.progress_large_gray()); 
   }
}
