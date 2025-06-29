/*
 * ProjectRMarkdownPreferencesPane.java
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
package org.rstudio.studio.client.projects.ui.prefs;

import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.prefs.PreferencesDialogBaseResources;
import org.rstudio.core.client.prefs.RestartRequirement;
import org.rstudio.core.client.resources.ImageResource2x;
import org.rstudio.core.client.widget.FormLabel;
import org.rstudio.core.client.widget.LayoutGrid;
import org.rstudio.core.client.widget.NumericValueWidget;
import org.rstudio.studio.client.common.HelpLink;
import org.rstudio.studio.client.panmirror.server.PanmirrorZoteroServerOperations;
import org.rstudio.studio.client.projects.StudioClientProjectConstants;
import org.rstudio.studio.client.projects.model.RProjectConfig;
import org.rstudio.studio.client.projects.model.RProjectOptions;
import org.rstudio.studio.client.workbench.prefs.model.UserPrefs;
import org.rstudio.studio.client.workbench.prefs.views.zotero.ZoteroLibrariesWidget;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.inject.Inject;

public class ProjectRMarkdownPreferencesPane extends ProjectPreferencesPane
{
   @Inject
   public ProjectRMarkdownPreferencesPane(PanmirrorZoteroServerOperations zoteroServer)
   {
   
   
      addHeader(constants_.visualModeCaption());
      
      Label infoLabel = new Label(constants_.rMarkdownInfoLabel());
      infoLabel.addStyleName(PreferencesDialogBaseResources.INSTANCE.styles().infoLabel());
      add(nudgeRightPlus(infoLabel));
      
      LayoutGrid grid = new LayoutGrid(3, 2);
      grid.addStyleName(RESOURCES.styles().workspaceGrid());
      grid.addStyleName(RES.styles().grid());
      
      // wrap mode
      wrap_ = new ListBox();
      wrap_.addStyleName(RES.styles().listBox());
      wrap_.addItem(constants_.projectTypeDefault(), RProjectConfig.MARKDOWN_WRAP_DEFAULT);
      wrap_.addItem(RProjectConfig.MARKDOWN_WRAP_NONE.toLowerCase(), RProjectConfig.MARKDOWN_WRAP_NONE);
      wrap_.addItem(RProjectConfig.MARKDOWN_WRAP_COLUMN.toLowerCase(), RProjectConfig.MARKDOWN_WRAP_COLUMN);
      wrap_.addItem(RProjectConfig.MARKDOWN_WRAP_SENTENCE.toLowerCase(), RProjectConfig.MARKDOWN_WRAP_SENTENCE);
      
      wrapColumn_ =  new NumericValueWidget(constants_.wrapColumnLabel(), 1, UserPrefs.MAX_WRAP_COLUMN);
      wrapColumn_.addStyleName(RES.styles().wrapAtColumn());
      wrapColumn_.setVisible(false);
      wrap_.addChangeHandler((value) -> {
         wrapColumn_.setVisible(wrap_.getSelectedValue().equals(RProjectConfig.MARKDOWN_WRAP_COLUMN));
      });
     
      VerticalPanel wrapPanel = new VerticalPanel();
      wrapPanel.add(new FormLabel(constants_.wrapPanelText(), wrap_));
      wrapPanel.add(wrapColumn_);
      
      // references
      references_ = new ListBox();
      references_.addStyleName(RES.styles().listBox());
      references_.addItem(constants_.referencesDefaultItem(), RProjectConfig.MARKDOWN_REFERENCES_DEFAULT);
      references_.addItem(RProjectConfig.MARKDOWN_REFERENCES_BLOCK.toLowerCase(), RProjectConfig.MARKDOWN_REFERENCES_BLOCK);
      references_.addItem(RProjectConfig.MARKDOWN_REFERENCES_SECTION.toLowerCase(), RProjectConfig.MARKDOWN_REFERENCES_SECTION);
      references_.addItem(RProjectConfig.MARKDOWN_REFERENCES_DOCUMENT.toLowerCase(), RProjectConfig.MARKDOWN_REFERENCES_DOCUMENT);
      grid.setWidget(0, 0, new FormLabel(constants_.referencesFormLabel(), references_));
      grid.setWidget(0, 1, references_);
      
      // canonical mode
      canonical_ = new ListBox();
      canonical_.addItem(constants_.canonicalDefaultItem());
      canonical_.addItem(constants_.canonicalTrueItem());
      canonical_.addItem(constants_.canonicalFalseItem());
      canonical_.addStyleName(RES.styles().listBox());
      grid.setWidget(1, 0, new FormLabel(constants_.canonicalFormLabel(), canonical_));
      grid.setWidget(1, 1, canonical_);
      
      // wrapping
      grid.setWidget(2, 0, wrapPanel);
      grid.setWidget(2, 1, wrap_);
      
      add(grid);
      
      // help on per-file markdown options
      HelpLink markdownPerFileOptions = new HelpLink(
            constants_.markdownPerFileOptionsCaption(),
            "visual_markdown_editing-writer-options",
            false // no version info
      );
      add(markdownPerFileOptions);
      
      addHeader(constants_.visualModeZoteroCaption());
      mediumSpaced(markdownPerFileOptions);
      
      zoteroLibs_ = new ZoteroLibrariesWidget(zoteroServer, true);
      add(zoteroLibs_);
   }

   @Override
   public ImageResource getIcon()
   {
      return new ImageResource2x(ProjectPreferencesDialogResources.INSTANCE.iconRMarkdown2x());
   }

   @Override
   public String getName()
   {
      return constants_.rMarkdownText();
   }

   @Override
   protected void initialize(RProjectOptions options)
   {
      RProjectConfig config = options.getConfig();
      
      setListBoxValue(wrap_, config.getMarkdownWrap());
      wrapColumn_.setValue(Integer.toString(config.getMarkdownWrapAtColumn()));
      wrapColumn_.setVisible(wrap_.getSelectedValue().equals(RProjectConfig.MARKDOWN_WRAP_COLUMN));
      setListBoxValue(references_, config.getMarkdownReferences());
      canonical_.setSelectedIndex(config.getMarkdownCanonical());
      
      zoteroLibs_.setLibraries(config.getZoteroLibraries());
      zoteroLibs_.addAvailableLibraries();
   }
   
   @Override
   public boolean validate()
   {
      return zoteroLibs_.validate();
   }

  

   @Override
   public RestartRequirement onApply(RProjectOptions options)
   {
      RProjectConfig config = options.getConfig();

      config.setMarkdownWrap(wrap_.getSelectedValue());
      config.setMarkdownWrapAtColumn(StringUtil.parseInt(wrapColumn_.getValue(), 72));
      config.setMarkdownReferences(references_.getSelectedValue());
      config.setMarkdownCanonical(canonical_.getSelectedIndex());
      
      if (zoteroLibs_.getLibraries() != null)
         config.setZoteroLibraries(zoteroLibs_.getLibraries());
      
      return new RestartRequirement();
   }
   
   private void setListBoxValue(ListBox listBox, String value)
   {
      listBox.setSelectedIndex(0);
      for (int i=0; i<wrap_.getItemCount(); i++) 
      {
         if (value.equals(listBox.getValue(i)))
         {
            listBox.setSelectedIndex(i);
            break;
         }
      }
   }


   interface Resources extends ClientBundle
   {
      @Source("ProjectRMarkdownPreferencesPane.css")
      Styles styles();
   }

   private static Resources RES = GWT.create(Resources.class);

   public interface Styles extends CssResource
   {
      String grid();
      String listBox();
      String wrapAtColumn();
   }

   static
   {
      RES.styles().ensureInjected();
   }

   private ListBox wrap_;
   private NumericValueWidget wrapColumn_;
   private ListBox references_;
   private ListBox canonical_;
   private ZoteroLibrariesWidget zoteroLibs_;
   private static final StudioClientProjectConstants constants_ = com.google.gwt.core.client.GWT.create(StudioClientProjectConstants.class);


}
