/*
 * NullProgressIndicator.java
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

package org.rstudio.core.client.widget;

import com.google.gwt.core.client.GWT;
import org.rstudio.core.client.CoreClientConstants;
import org.rstudio.studio.client.RStudioGinjector;

public class NullProgressIndicator implements ProgressIndicator
{

   @Override
   public void onProgress(String message)
   {
   }

   @Override
   public void onProgress(String message, Operation onCancel)
   {
   }

   @Override
   public void onCompleted()
   {
   }

   @Override
   public void onError(String message)
   {
      RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(constants_.errorCaption(),
                                                                    message);
   }

   @Override
   public void clearProgress()
   {
   }
   private static final CoreClientConstants constants_ = GWT.create(CoreClientConstants.class);
}
