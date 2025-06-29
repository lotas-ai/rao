/*
 * SimpleRequestCallback.java
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
package org.rstudio.studio.client.common;

import com.google.gwt.core.client.GWT;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

import org.rstudio.core.client.Debug;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;

public class SimpleRequestCallback<T> extends ServerRequestCallback<T>
{
   public SimpleRequestCallback()
   {
      this(constants_.errorCaption(), false);
   }

   public SimpleRequestCallback(String caption)
   {
      this(caption, false);
   }
   
   public SimpleRequestCallback(String caption,
                                boolean useClientInfoMsg)
   {
      caption_ = caption;
      useClientInfoMsg_ = useClientInfoMsg;
   }
   
   @Override
   public void onResponseReceived(T response)
   {
   }

   @Override
   public void onError(ServerError error)
   {
      Debug.logError(error);
      
      String message = error.getUserMessage();

      // see if a special message was provided
      if (useClientInfoMsg_)
      {
         JSONValue errValue = error.getClientInfo();
         if (errValue != null)
         {
            JSONString errMsg = errValue.isString();
            if (errMsg != null)
               message = errMsg.stringValue();
         }
      }
      
      RStudioGinjector.INSTANCE.getGlobalDisplay().showErrorMessage(
            caption_,
            message);
   }

   private String caption_;
   private boolean useClientInfoMsg_;
   private static final StudioClientCommonConstants constants_ = GWT.create(StudioClientCommonConstants.class);
}
