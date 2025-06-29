/*
 * AiSearchWidget.java
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


import com.google.gwt.core.client.GWT;
import org.rstudio.core.client.ElementIds;
import org.rstudio.core.client.widget.SearchDisplay;
import org.rstudio.core.client.widget.PreAiSearchWidget;

import com.google.inject.Inject;
import org.rstudio.studio.client.workbench.views.ai.AiConstants;


public class AiSearchWidget extends PreAiSearchWidget 
                              implements AiSearch.Display
{
   @Inject
   public AiSearchWidget(AiSearchOracle oracle)
   {
      super(constants_.searchAiLabel(), oracle);
      ElementIds.assignElementId(this, ElementIds.SW_AI);
   }

   @Override
   public SearchDisplay getSearchDisplay()
   {
      return this;
   }
   private static final AiConstants constants_ = GWT.create(AiConstants.class);
}
