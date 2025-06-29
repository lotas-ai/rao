/*
 * DataTable.java
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

package org.rstudio.studio.client.dataviewer;

import java.util.ArrayList;

import org.rstudio.core.client.BrowseCap;
import org.rstudio.core.client.ClassIds;
import org.rstudio.core.client.CommandWith2Args;
import org.rstudio.core.client.command.KeyboardShortcut;
import org.rstudio.core.client.command.ShortcutManager;
import org.rstudio.core.client.dom.IFrameElementEx;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.core.client.events.NativeKeyDownEvent;
import org.rstudio.core.client.resources.ImageResource2x;
import org.rstudio.core.client.theme.res.ThemeStyles;
import org.rstudio.core.client.widget.DataTableColumnWidget;
import org.rstudio.core.client.widget.LatchingToolbarButton;
import org.rstudio.core.client.widget.RStudioFrame;
import org.rstudio.core.client.widget.SearchWidget;
import org.rstudio.core.client.widget.SimpleButton;
import org.rstudio.core.client.widget.Toolbar;
import org.rstudio.core.client.widget.ToolbarButton;
import org.rstudio.core.client.widget.ToolbarLabel;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.workbench.views.source.editors.data.DataEditingTargetWidget.DataViewerCallback;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwt.user.client.ui.Widget;

public class DataTable
{
   public interface Host 
   {
      RStudioFrame getDataTableFrame();
   }

   public DataTable(Host host)
   {
      host_ = host;
   }
   
   public void initToolbar(Toolbar toolbar, boolean isPreview)
   {
      filterButton_ = new LatchingToolbarButton(
              constants_.filterButtonText(),
              ToolbarButton.NoTitle,
              false, /* textIndicatesState */
              ClassIds.DATA_TABLE_FILTER_TOGGLE,
              new ImageResource2x(DataViewerResources.INSTANCE.filterIcon2x()),
              new ClickHandler() {
                 public void onClick(ClickEvent event)
                 {
                    boolean newFilterState = !filtered_;

                    // attempt to apply the new filter state, and update state
                    // if we succeed (might fail if the filter UI is not
                    // ready/table is not initialized)
                    if (setFilterUIVisible(newFilterState))
                    {
                       filtered_ = newFilterState;
                       filterButton_.setLatched(filtered_);
                    }
                 }
              });
      toolbar.addLeftWidget(filterButton_);
      filterButton_.setVisible(!isPreview);

      colsSeparator_ = toolbar.addLeftSeparator();
      colsSeparator_.setVisible(false);
      addColumnControls(toolbar);

      searchWidget_ = new SearchWidget(constants_.searchWidgetLabel(), new SuggestOracle() {
         @Override
         public void requestSuggestions(Request request, Callback callback)
         {
            // no suggestions
            callback.onSuggestionsReady(
                  request,
                  new Response(new ArrayList<>()));
         }
      });
      searchWidget_.addValueChangeHandler(new ValueChangeHandler<String>() {
         @Override
         public void onValueChange(ValueChangeEvent<String> event)
         {
            applySearch(getWindow(), event.getValue());
         }
      });

      toolbar.addRightWidget(searchWidget_);
      searchWidget_.setVisible(!isPreview);
      
      if (isPreview)
      {
         ToolbarLabel label = 
            new ToolbarLabel(constants_.toolbarLabel());
         label.addStyleName(ThemeStyles.INSTANCE.toolbarInfoLabel());
         toolbar.addRightWidget(label);
      }
   }

   private ClickHandler[] getColumnViewClickHandlers() {
      ClickHandler handlers[] = {
         new ClickHandler()
         {
            @Override
            public void onClick(ClickEvent event)
            {
               firstColumnPage();
            }
         },
         new ClickHandler()
         {
            @Override
            public void onClick(ClickEvent event)
            {
               prevColumnPage();
            }
         },
         new ClickHandler()
         {
            @Override
            public void onClick(ClickEvent event)
            {
               nextColumnPage();
            }
         },
         new ClickHandler()
         {
            @Override
            public void onClick(ClickEvent event)
            {
               lastColumnPage();
            }
         }
      };

      return handlers;
   }
   private void addColumnControls(Toolbar toolbar)
   {
      colsLabel_ = new ToolbarLabel(constants_.colsLabel());
      colsLabel_.addStyleName(ThemeStyles.INSTANCE.toolbarInfoLabel());
      colsLabel_.setVisible(false);
      toolbar.addLeftWidget(colsLabel_);

      ClickHandler[] clickHandlers = getColumnViewClickHandlers();
      SimpleButton columnButton;
      columnViewButtons_ = new ArrayList<>();

      for (int i = 0; i < COLUMN_VIEW_BUTTONS.length; i++)
      {
         columnButton = new SimpleButton(COLUMN_VIEW_BUTTONS[i], true);
         columnButton.addClickHandler(clickHandlers[i]);
         columnButton.setVisible(false);
         toolbar.addLeftWidget(columnButton);
         columnViewButtons_.add(columnButton);

         if (i == 1)
         {
            columnTextWidget_ = new DataTableColumnWidget(this::setOffsetAndMaxColumns);
            columnTextWidget_.getElement().setId("data-viewer-column-input");
            columnTextWidget_.setVisible(false);
            toolbar.addLeftWidget(columnTextWidget_);
         }
      }
   }

   private void setColumnControlVisibility(boolean visible)
   {
      colsSeparator_.setVisible(visible);
      colsLabel_.setVisible(visible);
      for (int i = 0; i < COLUMN_VIEW_BUTTONS.length; i++)
      {
         columnViewButtons_.get(i).setVisible(visible);
      }
      columnTextWidget_.setVisible(visible);
   }

   private CommandWith2Args<Double, Double> getDataTableColumnCallback()
   {
      return (offset, max) ->
      {
         columnTextWidget_.setValue((offset + 1) + " - " + (offset + max));
         setColumnControlVisibility(isLimitedColumnFrame());
      };
   }

   private WindowEx getWindow()
   {
      IFrameElementEx frameEl = (IFrameElementEx) host_.getDataTableFrame().getElement().cast();
      return frameEl.getContentWindow();
   }
   
   public void addKeyDownHandler()
   {
      addKeyDownHandlerImpl(getWindow().getDocument().getBody());
   }
   
   private final native void addKeyDownHandlerImpl(Element body)
   /*-{
      var self = this;
      body.addEventListener("keydown", $entry(function(event) {
         self.@org.rstudio.studio.client.dataviewer.DataTable::onKeyDown(*)(event);
      }));
   }-*/;
   
   private void onKeyDown(NativeEvent event)
   {
      // Electron seems to handle keypresses in the main window even if
      // a sub-frame has focus, so this handler is not necessary there.
      //
      // On macOS, we do need to handle Ctrl + W to close the window, though.
      // 
      // https://github.com/rstudio/rstudio/issues/12029
      if (BrowseCap.isElectron())
      {
         if (BrowseCap.isMacintoshDesktop())
         {
            int modifiers = KeyboardShortcut.getModifierValue(event);
            if (modifiers == KeyboardShortcut.CTRL && event.getKeyCode() == KeyCodes.KEY_W)
            {
               RStudioGinjector.INSTANCE.getCommands().closeSourceDoc().execute();
            }
         }
         else
         {
            // Intentionally do nothing -- see notes above
         }
      }
      else
      {
         ShortcutManager.INSTANCE.onKeyDown(new NativeKeyDownEvent(event));
      }
   }

   public boolean setFilterUIVisible(boolean visible)
   {
      return setFilterUIVisible(getWindow(), visible);
   }
   
   public void setDataViewerCallback(DataViewerCallback dataCallback)
   {
      setDataViewerCallback(getWindow(), dataCallback);
   }
   
   public void setListViewerCallback(DataViewerCallback listCallback)
   {
      setListViewerCallback(getWindow(), listCallback);
   }

   public void setColumnFrameCallback()
   {
      setColumnFrameCallback(getWindow(), getDataTableColumnCallback());
   }

   public void refreshData()
   {
      filtered_= false;
      if (searchWidget_ != null)
         searchWidget_.setText("", false);
      if (filterButton_ != null)
         filterButton_.setLatched(false);

      refreshData(getWindow());
   }
   
   public void onActivate()
   {
      onActivate(getWindow());
   }
   
   public void onDeactivate()
   {
      try
      {
         onDeactivate(getWindow());
      }
      catch(Exception e)
      {
         // swallow exceptions occurring when deactivating, as they'll keep
         // users from switching tabs
      }
   }

   private boolean isLimitedColumnFrame() { return isLimitedColumnFrame(getWindow()); }

   private static final native boolean setFilterUIVisible (WindowEx frame, boolean visible) /*-{
      if (frame && frame.setFilterUIVisible)
         return frame.setFilterUIVisible(visible);
      return false;
   }-*/;

   private static final native void refreshData(WindowEx frame) /*-{
      if (frame && frame.refreshData)
         frame.refreshData();
   }-*/;

   private static final native void applySearch(WindowEx frame, String text) /*-{
      if (frame && frame.applySearch)
         frame.applySearch(text);
   }-*/;
   
   private static final native void onActivate(WindowEx frame) /*-{
      if (frame && frame.onActivate)
         frame.onActivate();
   }-*/;

   private static final native void onDeactivate(WindowEx frame) /*-{
      if (frame && frame.onDeactivate)
         frame.onDeactivate();
   }-*/;

   private static final native boolean isLimitedColumnFrame(WindowEx frame) /*-{
       if (frame && frame.isLimitedColumnFrame)
           return frame.isLimitedColumnFrame();
       return false;
   }-*/;

   private void nextColumnPage()
   {
      nextColumnPage(getWindow());
   }
   private void prevColumnPage()
   {
      prevColumnPage(getWindow());
   }
   private void firstColumnPage()
   {
      firstColumnPage(getWindow());
   }
   private void lastColumnPage()
   {
      lastColumnPage(getWindow());
   }
   private void setOffsetAndMaxColumns(int offset, int max)
   {
      setOffsetAndMaxColumns(getWindow(), offset, max);
   }

   private static final native void nextColumnPage(WindowEx frame) /*-{
      if (frame && frame.nextColumnPage)
          frame.nextColumnPage();
   }-*/;
   private static final native void prevColumnPage(WindowEx frame) /*-{
       if (frame && frame.prevColumnPage)
           frame.prevColumnPage();
   }-*/;
   private static final native void firstColumnPage(WindowEx frame) /*-{
       if (frame && frame.firstColumnPage)
           frame.firstColumnPage();
   }-*/;
   private static final native void lastColumnPage(WindowEx frame) /*-{
       if (frame && frame.lastColumnPage)
           frame.lastColumnPage();
   }-*/;

   private static final native void setOffsetAndMaxColumns(WindowEx frame, int offset, int max) /*-{
       if (frame && frame.setOffsetAndMaxColumns) {
           frame.setOffsetAndMaxColumns(offset, max);
       }
   }-*/;
   private static final native void setDataViewerCallback(WindowEx frame, DataViewerCallback dataCallback) /*-{
      frame.setOption(
         "dataViewerCallback", 
         $entry(function(row, col) {
            dataCallback.@org.rstudio.studio.client.workbench.views.source.editors.data.DataEditingTargetWidget.DataViewerCallback::execute(*)(row, col);
         }));
   }-*/;
   
   private static final native void setListViewerCallback(WindowEx frame, DataViewerCallback listCallback) /*-{
      frame.setOption(
         "listViewerCallback", 
         $entry(function(row, col) {
            listCallback.@org.rstudio.studio.client.workbench.views.source.editors.data.DataEditingTargetWidget.DataViewerCallback::execute(*)(row, col);
         }));
   }-*/;

   private static final native void setColumnFrameCallback(WindowEx frame, CommandWith2Args<Double, Double> columnFrameCallback) /*-{
      frame.setOption(
         "columnFrameCallback",
         $entry(function(offset, max) {
            columnFrameCallback.@org.rstudio.core.client.CommandWith2Args::execute(*)(offset, max);
         }));
   }-*/;
   private Host host_;
   private LatchingToolbarButton filterButton_;
   private DataTableColumnWidget columnTextWidget_;
   private Widget colsSeparator_;
   private ToolbarLabel colsLabel_;
   private ArrayList<SimpleButton> columnViewButtons_;
   private SearchWidget searchWidget_;
   private boolean filtered_ = false;

   private static String COLUMN_VIEW_BUTTONS[] = {
      "<i class=\"icon-angle-double-left \"></i>",
      "<i class=\"icon-angle-left \"></i>",
      "<i class=\"icon-angle-right \"></i>",
      "<i class=\"icon-angle-double-right \"></i>"
   };

   private static final DataViewerConstants constants_ = GWT.create(DataViewerConstants.class);
}
