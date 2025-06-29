/*
 * DataEditingTarget.java
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.widget.SimplePanelWithProgress;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.common.filetypes.FileIcon;
import org.rstudio.studio.client.server.ErrorLoggingServerRequestCallback;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.Void;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.views.source.ViewsSourceConstants;
import org.rstudio.studio.client.workbench.views.source.editors.urlcontent.UrlContentEditingTarget;
import org.rstudio.studio.client.workbench.views.source.events.CloseDataEvent;
import org.rstudio.studio.client.workbench.views.source.events.DataViewChangedEvent;
import org.rstudio.studio.client.workbench.views.source.events.PopoutDocEvent;
import org.rstudio.studio.client.workbench.views.source.model.DataItem;
import org.rstudio.studio.client.workbench.views.source.model.SourceServerOperations;

import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class DataEditingTarget extends UrlContentEditingTarget
                               implements DataViewChangedEvent.Handler
{
   enum QueuedRefreshType
   {
      NoRefresh,
      StructureRefresh
   }

   @Inject
   public DataEditingTarget(SourceServerOperations server,
                            Commands commands,
                            GlobalDisplay globalDisplay,
                            EventBus events)
   {
      super(server, commands, globalDisplay, events);
      
      events_ = events;
      isActive_ = true;
      handlers_ = new ArrayList<>();
      
      handlers_.add(events.addHandler(DataViewChangedEvent.TYPE, this));
   }

   @Override
   protected Display createDisplay()
   {
      progressPanel_ = new SimplePanelWithProgress();
      progressPanel_.setSize("100%", "100%");
      Roles.getTabpanelRole().set(progressPanel_.getElement());
      setAccessibleName(null);
      reloadDisplay();
      return new Display()
      {
         public void print()
         {
            ((Display)progressPanel_.getWidget()).print();
         }

         public void setAccessibleName(String accessibleName)
         {
            DataEditingTarget.this.setAccessibleName(accessibleName);
         }

         public Widget asWidget()
         {
            return progressPanel_;
         }
      };
   }

   @Override
   public void onDataViewChanged(DataViewChangedEvent event)
   {
      // check whether this editing target is managing the changed data view
      DataViewChangedEvent.Data eventData = event.getData();
      DataItem item = getDataItem();
      if (!eventData.getCacheKey().equals(item.getCacheKey()))
         return;
      
      // close if the object no longer exists
      if (!eventData.getObjectExists())
      {
         events_.fireEvent(new CloseDataEvent(item));
         return;
      }

      // perform the refresh immediately if the tab is active; otherwise,
      // leave it in the queue and it'll be run when the tab is activated
      queuedRefresh_ = QueuedRefreshType.StructureRefresh;
      if (isActive_)
      {
         doQueuedRefresh();
      }
   }

   private void setAccessibleName(String accessibleName)
   {
      if (StringUtil.isNullOrEmpty(accessibleName))
         accessibleName = constants_.untitledDataBrowser();
      Roles.getTabpanelRole().setAriaLabelProperty(progressPanel_.getElement(),
              constants_.accessibleNameDataBrowser(accessibleName));
   }

   @Override
   public void onActivate()
   {
      super.onActivate();
      isActive_ = true;
      if (view_ != null)
      {
         // the data change while the window wasn't active, so refresh it,
         if (queuedRefresh_ != QueuedRefreshType.NoRefresh)
            doQueuedRefresh();
         else
            view_.onActivate();
      }
   }

   @Override
   public void onDeactivate()
   {
      super.onDeactivate();
      view_.onDeactivate();
      isActive_ = false;
   }

   @Override
   public void onDismiss(int dismissType)
   {
      // explicitly avoid calling super method as we don't
      // have an associated content URL to clean up
      for (HandlerRegistration handler : handlers_)
         handler.removeHandler();
      handlers_.clear();
   }
   
   private void doQueuedRefresh()
   {
      view_.refreshData();
      queuedRefresh_ = QueuedRefreshType.NoRefresh;
   }

   private void clearDisplay()
   {
      progressPanel_.showProgress(1);
   }

   private void reloadDisplay()
   {
      view_ = new DataEditingTargetWidget(
            constants_.dataBrowser(),
            commands_,
            events_,
            getDataItem(),
            column_);
      view_.setSize("100%", "100%");
      progressPanel_.setWidget(view_);
   }
   
   @Override
   public String getPath()
   {
      return getDataItem().getURI();
   }

   @Override
   public FileIcon getIcon()
   {
      return FileIcon.CSV_ICON;
   }

   private DataItem getDataItem()
   {
      return doc_.getProperties().cast();
   }

   @Override
   protected String getContentTitle()
   {
      return getDataItem().getCaption();
   }

   @Override
   protected String getContentUrl()
   {
      return getDataItem().getContentUrl();
   }

   @Override
   public void popoutDoc()
   {
      events_.fireEvent(new PopoutDocEvent(getId(), null, null));
   }

   @Override
   public String getCurrentStatus()
   {
      return constants_.dataBrowserDisplayed();
   }

   protected String getCacheKey()
   {
      return getDataItem().getCacheKey();
   }

   public void updateData(final DataItem data)
   {
      final Widget originalWidget = progressPanel_.getWidget();

      clearDisplay();
      
      final String oldCacheKey = getCacheKey();

      HashMap<String, String> props = new HashMap<>();
      data.fillProperties(props);
      server_.modifyDocumentProperties(
            doc_.getId(),
            props,
            new SimpleRequestCallback<Void>(constants_.errorCapitalized())
            {
               @Override
               public void onResponseReceived(Void response)
               {
                  server_.removeCachedData(
                        oldCacheKey,
                        new ErrorLoggingServerRequestCallback<>());

                  data.fillProperties(doc_.getProperties());
                  reloadDisplay();
               }

               @Override
               public void onError(ServerError error)
               {
                  super.onError(error);
                  progressPanel_.setWidget(originalWidget);
               }
            });
   }

   private SimplePanelWithProgress progressPanel_;
   private DataEditingTargetWidget view_;
   private final EventBus events_;
   private boolean isActive_;
   private QueuedRefreshType queuedRefresh_;
   private final List<HandlerRegistration> handlers_;
   private static final ViewsSourceConstants constants_ = GWT.create(ViewsSourceConstants.class);
}
