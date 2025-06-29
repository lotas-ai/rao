/*
 * AceTheme.java
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
package org.rstudio.studio.client.workbench.views.source.editors.text.themes;

import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.core.client.regex.Pattern;
import org.rstudio.studio.client.workbench.prefs.model.UserStateAccessor;
import org.rstudio.studio.client.workbench.views.source.ViewsSourceConstants;

import com.google.gwt.core.client.GWT;

/**
 * Represents an editor theme.
 */
public class AceTheme extends UserStateAccessor.Theme
{
   protected AceTheme() {}

   public static final AceTheme createDefault()
   {
      return createDefault(false);
   }

   public static final AceTheme createDefault(boolean isDark)
   {
      // Always return Textmate (light theme) regardless of isDark parameter
      // This ensures AI pane compatibility by preventing dark theme defaults
      return create(constants_.textmateDefaultParentheses(), "theme/default/textmate.rstheme", false);
   }

   public static final native AceTheme create(String name, String url, Boolean isDark)
   /*-{
      return {
         name: name,
         url: url,
         isDark: isDark
      };
   }-*/;

   public native final Boolean isDark()
   /*-{
      return this.isDark;
   }-*/;

   public native final Boolean isSolarizedLight() /*-{
      return this.url.indexOf('solarized_light.rstheme') !== -1;
   }-*/;

   public final Boolean isDefaultTheme()
   {
      return Pattern.create("^theme/default/.+?\\.rstheme$").test(getUrl());
   }

   public final Boolean isLocalCustomTheme()
   {
      return Pattern.create("^theme/custom/local/.+?\\.rstheme$").test(getUrl());
   }

   public final Boolean isGlobalCustomTheme()
   {
      return Pattern.create("^theme/custom/global/.+?\\.rstheme$").test(getUrl());
   }

   public final String getFileStem()
   {
      return FileSystemItem.createFile(this.getUrl()).getStem();
   }

   public final Boolean isEqualTo(AceTheme other)
   {
      return StringUtil.equalsIgnoreCase(other.getName(), this.getName());
   }

   public final static String getThemeErrorClass(AceTheme theme)
   {
      if (theme == null || createDefault().isEqualTo(theme))
         return " ace_constant";
      else
         return " ace_constant ace_language";
   }
   private static final ViewsSourceConstants constants_ = GWT.create(ViewsSourceConstants.class);
}