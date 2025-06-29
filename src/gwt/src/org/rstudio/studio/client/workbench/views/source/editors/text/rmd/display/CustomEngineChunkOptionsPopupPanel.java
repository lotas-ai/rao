/*
 * CustomEngineChunkOptionsPopupPanel.java
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
package org.rstudio.studio.client.workbench.views.source.editors.text.rmd.display;

import org.rstudio.studio.client.workbench.views.source.editors.text.rmd.display.ChunkOptionValue.OptionLocation;

public class CustomEngineChunkOptionsPopupPanel extends DefaultChunkOptionsPopupPanel
{
   public CustomEngineChunkOptionsPopupPanel(String engine, OptionLocation optionLocation, boolean isVisualEditor)
   {
      super(engine, optionLocation, isVisualEditor);
      
      showWarningsInOutputCb_.setVisible(false);
      showMessagesInOutputCb_.setVisible(false);
      useCustomFigureCheckbox_.setVisible(false);
      enginePanel_.setVisible(true);
   }
}
