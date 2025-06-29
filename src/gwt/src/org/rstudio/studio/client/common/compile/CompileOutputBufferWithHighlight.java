/*
 * CompileOutputBufferWithHighlight.java
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


package org.rstudio.studio.client.common.compile;

import com.google.gwt.core.client.GWT;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.VirtualConsole;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.core.client.widget.BottomScrollPanel;
import org.rstudio.core.client.widget.FontSizer;
import org.rstudio.core.client.widget.PreWidget;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.common.StudioClientCommonConstants;
import org.rstudio.studio.client.workbench.views.console.ConsoleResources;
import org.rstudio.studio.client.workbench.views.source.editors.text.themes.AceTheme;

import com.google.gwt.user.client.ui.Composite;

public class CompileOutputBufferWithHighlight extends Composite 
                                implements CompileOutputDisplay
{
   public static enum PanelState { OK, OVERLOADED };
   public static enum OutputType { Command, Output, Error };
   
   public CompileOutputBufferWithHighlight()
   {
      styles_ = ConsoleResources.INSTANCE.consoleStyles();
      
      output_ = new PreWidget();
      output_.setStylePrimaryName(styles_.output());
      output_.addStyleName(styles_.paddedOutput());
      FontSizer.applyNormalFontSize(output_);
      console_ = RStudioGinjector.INSTANCE.getVirtualConsoleFactory().create(output_.getElement());

      scrollPanel_ = new BottomScrollPanel();
      scrollPanel_.setSize("100%", "100%");
      scrollPanel_.addStyleName("ace_editor");
      scrollPanel_.addStyleName("ace_scroller");
      scrollPanel_.setWidget(output_);
      
      initWidget(scrollPanel_);
   }
   
   @Override
   public void writeCommand(String command)
   {
      write(command, OutputType.Command, styles_.command() + ConsoleResources.KEYWORD_CLASS_NAME);
   }
   
   @Override
   public void writeOutput(String output)
   {
      write(output, OutputType.Output, styles_.output());
   }

   @Override
   public void writeError(String error)
   {
      write(error, OutputType.Error, getErrorClass());
   }
   
   @Override
   public void scrollToBottom()
   {
      scrollPanel_.scrollToBottom();
   }

   @Override
   public void clear()
   {
      console_.clear();
      state_ = PanelState.OK;
      totalSubmittedLines_ = 0;
      numDisplayedLines_ = 0;
      output_.setText("");
      savedOutput_ = "";
      numLinesSaved_ = 0;
   }
   
   @Override
   public void onCompileCompleted()
   {
      state_ = PanelState.OK;
      if (savedOutput_.isEmpty())
         return;
      
      String outputClass = styles_.output();
      console_.submit("\n", outputClass);

      flushOutput();
   }

   @Override
   public void flushOutput()
   {
      String[] lines = savedOutput_.split("\n");
      int end = lines.length - 1;
      int start = Math.max(0, end - 100);
      for (int i = start; i < end; i++)
      {
         console_.submit(lines[i] + "\n", styles_.output());
         totalSubmittedLines_++;
      }

      savedOutput_ = "";
      numLinesSaved_ = 0;
   }

   private void write(String output, OutputType outputType, String className)
   {
      switch (state_)
      {
      
      case OK:
      {
         console_.submit(output, className);
         int numNewlines = StringUtil.newlineCount(output);
         totalSubmittedLines_ += numNewlines;
         numDisplayedLines_ += numNewlines;

         if (numDisplayedLines_ > MAX_LINES_DISPLAY)
         {
            DomUtils.trimLines(output_.getElement(), numDisplayedLines_ - MAX_LINES_DISPLAY);
            numDisplayedLines_ = MAX_LINES_DISPLAY;
         }

         if (totalSubmittedLines_ > MAX_LINES_OVERLOAD_BUFFER)
         {
            state_ = PanelState.OVERLOADED;
            console_.submit(constants_.consoleBufferedMessage(MAX_LINES_OVERLOAD_BUFFER), styles_.warning());
         }

         scrollPanel_.onContentSizeChanged();
         return;
      }

      case OVERLOADED:
      {
         numLinesSaved_++;
         savedOutput_ += output;

         if (numLinesSaved_ > MAX_LINES_OVERLOAD_BUFFER)
         {
            flushOutput();
            console_.submit(constants_.consoleBufferedMessage(MAX_LINES_OVERLOAD_BUFFER), styles_.warning());
         }
         return;
      }
      
      }
   }
   
   private String getErrorClass()
   {
      return styles_.output() + " " + 
             AceTheme.getThemeErrorClass(
                RStudioGinjector.INSTANCE.getUserState().theme().getValue().cast());
   }
 
   PreWidget output_;
   VirtualConsole console_;
   PanelState state_ = PanelState.OK;
   private int numDisplayedLines_;
   private int totalSubmittedLines_;
   private String savedOutput_ = "";
   private int numLinesSaved_ = 0;
   private BottomScrollPanel scrollPanel_;
   private ConsoleResources.ConsoleStyles styles_;
   
   private static final int MAX_LINES_DISPLAY = 500;
   private static final int MAX_LINES_OVERLOAD_BUFFER = 5000;

   private static final StudioClientCommonConstants constants_ = GWT.create(StudioClientCommonConstants.class);
}
