/*
 * DataEditingTargetWidget.java
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

package org.rstudio.studio.client.workbench.views.source.editors.data;


import org.rstudio.core.client.ElementIds;
import org.rstudio.core.client.RegexUtil;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.dom.IFrameElementEx;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.core.client.js.JsUtil;
import org.rstudio.core.client.widget.GridViewerStyles;
import org.rstudio.core.client.widget.RStudioFrame;
import org.rstudio.core.client.widget.RStudioThemedFrame;
import org.rstudio.core.client.widget.Toolbar;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.AutoGlassPanel;
import org.rstudio.studio.client.dataviewer.DataTable;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.views.console.events.SendToConsoleEvent;
import org.rstudio.studio.client.workbench.views.source.PanelWithToolbars;
import org.rstudio.studio.client.workbench.views.source.SourceColumn;
import org.rstudio.studio.client.workbench.views.source.ViewsSourceConstants;
import org.rstudio.studio.client.workbench.views.source.editors.EditingTargetToolbar;
import org.rstudio.studio.client.workbench.views.source.editors.urlcontent.UrlContentEditingTarget;
import org.rstudio.studio.client.workbench.views.source.model.DataItem;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public class DataEditingTargetWidget extends Composite
   implements UrlContentEditingTarget.Display, 
              DataTable.Host
{
   public static interface DataViewerCallback
   {
      public void execute(JavaScriptObject row, JavaScriptObject col);
   }
   
   interface Resources extends ClientBundle
   {
      @Source("DataEditingTargetWidget.css")
      Styles styles();
   }
   private static Resources resources = GWT.create(Resources.class);

   public interface Styles extends CssResource
   {
      String description();

      String statusBar();
      String statusBarDisplayed();
      String statusBarOmitted();
   }

   static
   {
      resources.styles().ensureInjected();
   }
   
   private static final native String formatCallbackDimension(JavaScriptObject object)
   /*-{
      return typeof object === "string" ? JSON.stringify(object) : object.toString();
   }-*/;

   public DataEditingTargetWidget(String title,
                                  Commands commands, 
                                  EventBus events,
                                  DataItem dataItem,
                                  SourceColumn column)
   {
      Styles styles = resources.styles();

      commands_ = commands;

      frame_ = new RStudioThemedFrame(
         title,
         dataItem.getContentUrl(),
         GridViewerStyles.getCustomStyle(),
         null,
         false);
      frame_.setSize("100%", "100%");
      ElementIds.assignElementId(frame_, ElementIds.DATA_VIEWER_FRAME);
      table_ = new DataTable(this);
      column_ = column;
      
      // when loaded, hook up event handlers
      frame_.addLoadHandler((event) ->
      {
         DataViewerCallback callback = (rowObject, colObject) ->
         {
            String lho = dataItem.getExpression();
            String object = dataItem.getObject();
            if (StringUtil.isNullOrEmpty(object))
            {
               // we're viewing an expression -- wrap in parens before indexing
               lho = "(" + lho + ")";
            }
            else
            {
               // we're viewing a live variable
               if (RegexUtil.isSyntacticRIdentifier(object))
               {
                  // object is a valid identifier, use as-is
                  lho = object;
               }
               else
               {
                  // not a valid identifier; escape before indexing
                  lho = "`" + object + "`";
               }
            }
            
            String row = formatCallbackDimension(rowObject);
            String col = formatCallbackDimension(colObject);
            
            // objects with explicitly-set row names need to be indexed
            // using an alternate syntax. detect this case and handle
            String code;
            if (JsUtil.isTypeOf(rowObject, "string"))
            {
               code = "View(" + lho + "[" + row + ", " + col + "])";
            }
            else
            {
               code = "View(" + lho +  "[[" + col + "]][[" + row + "]])";
            }
            
            events.fireEvent(new SendToConsoleEvent(code, true));
         };
         
         table_.addKeyDownHandler();
         table_.setDataViewerCallback(callback);
         table_.setListViewerCallback(callback);
         table_.setColumnFrameCallback();
         
      });

      Widget mainWidget;

      if (dataItem.getDisplayedObservations() != dataItem.getTotalObservations())
      {
         FlowPanel statusBar = new FlowPanel();
         statusBar.setStylePrimaryName(styles.statusBar());
         statusBar.setSize("100%", "100%");
         Label label1 = new Label(
               constants_.dataEditingTargetWidgetLabel1(StringUtil.formatGeneralNumber(dataItem.getDisplayedObservations()),
                       StringUtil.formatGeneralNumber(dataItem.getTotalObservations())));
         int omitted = dataItem.getTotalObservations()
                       - dataItem.getDisplayedObservations();
         Label label2 = new Label(constants_.dataEditingTargetWidgetLabel2(
                                  StringUtil.formatGeneralNumber(omitted)));

         label1.addStyleName(styles.statusBarDisplayed());
         label2.addStyleName(styles.statusBarOmitted());

         statusBar.add(label1);
         statusBar.add(label2);

         DockLayoutPanel dockPanel = new DockLayoutPanel(Unit.PX);
         dockPanel.addSouth(statusBar, 20);
         dockPanel.add(new AutoGlassPanel(frame_));
         dockPanel.setSize("100%", "100%");
         mainWidget = dockPanel;
      }
      else
      {
         mainWidget = new AutoGlassPanel(frame_);
      }
      

      PanelWithToolbars panel = new PanelWithToolbars(
            createToolbar(dataItem, styles),
            mainWidget);

      initWidget(panel);
   }

   private Toolbar createToolbar(DataItem dataItem, Styles styles)
   {
      Toolbar toolbar = new EditingTargetToolbar(commands_, true, column_);
      toolbar.getElement().setId(ElementIds.DATA_EDITING_TOOLBAR);
      table_.initToolbar(toolbar, dataItem.isPreview());
      return toolbar;
   }
   
   private WindowEx getWindow()
   {
      IFrameElementEx frameEl = (IFrameElementEx) frame_.getElement().cast();
      return frameEl.getContentWindow();
   }

   @Override
   public void print()
   {
      getWindow().print();
   }

   @Override
   public void setAccessibleName(String name)
   {
      // Accessible name is set on container of this widget
   }

   public void setFilterUIVisible(boolean visible)
   {
      if (table_ != null)
         table_.setFilterUIVisible(visible);
   }
   
   public void refreshData()
   {
      if (table_ != null)
         table_.refreshData();
   }
   
   public void onActivate()
   {
      if (table_ != null)
         table_.onActivate();
   }
   
   public void onDeactivate()
   {
      if (table_ != null)
         table_.onDeactivate();
   }
   
   @Override
   public RStudioFrame getDataTableFrame()
   {
      return frame_;
   }

   public Widget asWidget()
   {
      return this;
   }

   private final Commands commands_;
   private RStudioThemedFrame frame_;
   private DataTable table_;
   private SourceColumn column_;
   private static final ViewsSourceConstants constants_ = GWT.create(ViewsSourceConstants.class);
}
