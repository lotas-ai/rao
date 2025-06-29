/*
 * AiSearchOracle.java
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
package org.rstudio.studio.client.workbench.views.ai.search;

import java.util.ArrayList;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.inject.Inject;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;

public class AiSearchOracle extends SuggestOracle
{
   @Inject
   public AiSearchOracle(AiServerOperations server)
   {
      server_ = server;
   }

   @Override
   public void requestSuggestions(final Request request, 
                                  final Callback callback)
   {
   }
   
   private class SearchSuggestion implements Suggestion
   {
      public SearchSuggestion(String value)
      {
         value_ = value;
      }

      public String getDisplayString()
      {
         return value_;
      }

      public String getReplacementString()
      {
         return value_;
      }
      
      private final String value_;
   }

   private final AiServerOperations server_;
}
