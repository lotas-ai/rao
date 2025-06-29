/*
 * ProjectSpellingPreferencesPane.java
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

import org.rstudio.studio.client.common.spelling.SpellingService;
import org.rstudio.studio.client.common.spelling.ui.SpellingLanguageSelectWidget;
import org.rstudio.studio.client.projects.StudioClientProjectConstants;
import org.rstudio.studio.client.projects.model.RProjectConfig;
import org.rstudio.studio.client.projects.model.RProjectOptions;
import org.rstudio.studio.client.workbench.prefs.model.SpellingPrefsContext;
import org.rstudio.studio.client.workbench.prefs.model.UserPrefs;

import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.Label;
import com.google.inject.Inject;

public class ProjectSpellingPreferencesPane extends ProjectPreferencesPane
{
   @Inject
   public ProjectSpellingPreferencesPane(SpellingService spellingService, UserPrefs uiPrefs)
   {
      uiPrefs_ = uiPrefs;
   

      addHeader(constants_.dictionariesCaption());
      
      Label infoLabel = new Label(constants_.dictionariesInfoLabel());
      infoLabel.addStyleName(PreferencesDialogBaseResources.INSTANCE.styles().infoLabel());
      spaced(infoLabel);
      add(infoLabel);
      
      languageWidget_ = new SpellingLanguageSelectWidget(spellingService, true);
      
      add(languageWidget_);
      
   
   }

   @Override
   public ImageResource getIcon()
   {
      return new ImageResource2x(PreferencesDialogBaseResources.INSTANCE.iconSpelling2x());
   }

   @Override
   public String getName()
   {
      return constants_.spellingText();
   }

   @Override
   protected void initialize(RProjectOptions options)
   {
      RProjectConfig config = options.getConfig();
      SpellingPrefsContext context = uiPrefs_.spellingPrefsContext().getValue();
      
      languageWidget_.setProgressIndicator(getProgressIndicator());
      languageWidget_.setLanguages(context.getAllLanguagesInstalled(),
                                   context.getAvailableLanguages());  
      languageWidget_.setSelectedLanguage(StringUtil.notNull(config.getSpellingDictionary()));
   }

   @Override
   public RestartRequirement onApply(RProjectOptions options)
   {
      RProjectConfig config = options.getConfig();
      config.setSpellingDictionary(languageWidget_.getSelectedLanguage());
      return new RestartRequirement();
   }

   private SpellingLanguageSelectWidget languageWidget_;
   
   private final UserPrefs uiPrefs_;
   private static final StudioClientProjectConstants constants_ = com.google.gwt.core.client.GWT.create(StudioClientProjectConstants.class);

}
