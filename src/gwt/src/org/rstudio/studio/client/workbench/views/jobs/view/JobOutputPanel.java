/*
 * JobOutputPanel.java
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
package org.rstudio.studio.client.workbench.views.jobs.view;

import org.rstudio.core.client.ElementIds;
import org.rstudio.studio.client.common.compile.CompileOutput;
import org.rstudio.studio.client.common.compile.CompileOutputBufferWithHighlight;
import org.rstudio.studio.client.common.compile.CompilePanel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public class JobOutputPanel extends Composite
{
   private static JobOutputPanelUiBinder uiBinder = GWT.create(JobOutputPanelUiBinder.class);

   interface JobOutputPanelUiBinder extends UiBinder<Widget, JobOutputPanel>
   {
   }

   public JobOutputPanel()
   {
      output_ = new CompilePanel(new CompileOutputBufferWithHighlight());
      output_.setHeight("100%");
      ElementIds.assignElementId(output_.asWidget(), ElementIds.JOB_LAUNCHER_OUTPUT_PANEL);

      initWidget(uiBinder.createAndBindUi(this));
      
      // initially empty
      clearOutput();
   }
   
   public void clearOutput()
   {
      output_.clearOutput();
      output_.setVisible(false);
      empty_.setVisible(true);
   }
   
   public void scrollToBottom()
   {
      output_.scrollToBottom();
   }
   
   public void showOutput(CompileOutput output, boolean scrollToBottom)
   {
      if (output.getOutput().isEmpty())
         return;
      
      // make sure output is visible
      empty_.setVisible(false);
      output_.setVisible(true);

      output_.showOutput(output, scrollToBottom);
   }

   public void showBufferedOutput()
   {
      output_.showBufferedOutput();
   }
   
   @UiField(provided=true) CompilePanel output_;
   @UiField Label empty_;
}
