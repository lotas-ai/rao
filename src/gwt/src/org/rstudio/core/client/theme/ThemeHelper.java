/*
 * ThemeHelper.java
 *
 * Copyright (C) 2022 by Lotas Inc.
 *
 * Unless you have received this program directly from Lotas Inc. pursuant
 * to the terms of a commercial license agreement with Lotas Inc., then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.core.client.theme;

import com.google.gwt.dom.client.Document;

public class ThemeHelper
{
   /**
    * Get the current theme name by checking the rstudio_container element class
    */
   public static String getCurrentTheme()
   {
      // Check the rstudio_container element first (where theme classes are actually applied)
      com.google.gwt.dom.client.Element container = Document.get().getElementById("rstudio_container");
      if (container != null)
      {
         String containerClass = container.getClassName();
         if (containerClass.contains("rstudio-themes-dark-grey"))
            return "dark-grey";
         else if (containerClass.contains("rstudio-themes-alternate"))
            return "alternate";
         else
            return "default";
      }
      
      // Fallback to checking body class (for compatibility)
      String bodyClass = Document.get().getBody().getClassName();
      if (bodyClass.contains("rstudio-themes-dark-grey"))
         return "dark-grey";
      else if (bodyClass.contains("rstudio-themes-alternate"))
         return "alternate";
      else
         return "default";
   }
   
   /**
    * Get theme-appropriate header background color
    */
   public static String getHeaderBackground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyHeaderBackground;
         case "alternate":
            return ThemeColors.alternateHeaderBackground;
         default:
            return ThemeColors.defaultHeaderBackground;
      }
   }
   
   /**
    * Get theme-appropriate header foreground color
    */
   public static String getHeaderForeground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyHeaderForeground;
         case "alternate":
            return ThemeColors.alternateHeaderForeground;
         default:
            return ThemeColors.defaultHeaderForeground;
      }
   }
   
   /**
    * Get theme-appropriate border color
    */
   public static String getBorderColor()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyBorderColor;
         case "alternate":
            return ThemeColors.alternateBorderColor;
         default:
            return ThemeColors.defaultBorderColor;
      }
   }
   
   /**
    * Get theme-appropriate border color with good contrast
    * Uses light gray borders for light themes and lighter borders for dark theme
    */
   public static String getVisibleBorder()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreySubtleText; // #ccc for visibility in dark mode
         case "alternate":
            return ThemeColors.alternateBorder; // rgb(181, 210, 226)
         default:
            return ThemeColors.defaultBorder; // rgb(214,218,220)
      }
   }
   
   /**
    * Get theme-appropriate foreground color
    */
   public static String getForeground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyForeground;
         case "alternate":
            return ThemeColors.alternateForeground;
         default:
            return ThemeColors.defaultForeground;
      }
   }
   
   /**
    * Get theme-appropriate subtle text color
    */
   public static String getSubtleText()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreySubtleText;
         case "alternate":
            return ThemeColors.alternateSubtleText;
         default:
            return ThemeColors.defaultSubtleText;
      }
   }
   
   /**
    * Get theme-appropriate disabled background color
    */
   public static String getDisabledBackground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyDisabledBackground;
         case "alternate":
            return ThemeColors.alternateDisabledBackground;
         default:
            return ThemeColors.defaultDisabledBackground;
      }
   }
   
   /**
    * Get theme-appropriate icon color
    */
   public static String getIconColor()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyIconColor;
         case "alternate":
            return ThemeColors.alternateIconColor;
         default:
            return ThemeColors.defaultIconColor;
      }
   }
   
   /**
    * Get theme-appropriate error color
    */
   public static String getErrorColor()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyErrorColor;
         case "alternate":
            return ThemeColors.alternateErrorColor;
         default:
            return ThemeColors.defaultErrorColor;
      }
   }
   
   /**
    * Get theme-appropriate success color
    */
   public static String getSuccessColor()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreySuccessColor;
         case "alternate":
            return ThemeColors.alternateSuccessColor;
         default:
            return ThemeColors.defaultSuccessColor;
      }
   }
   
   /**
    * Get theme-appropriate success border color (darker green)
    */
   public static String getSuccessBorderColor()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreySuccessBorder;
         case "alternate":
            return ThemeColors.alternateSuccessBorder;
         default:
            return ThemeColors.defaultSuccessBorder;
      }
   }
   
   /**
    * Get theme-appropriate warning color
    */
   public static String getWarningColor()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyWarningColor;
         case "alternate":
            return ThemeColors.alternateWarningColor;
         default:
            return ThemeColors.defaultWarningColor;
      }
   }
   
   /**
    * Get theme-appropriate divider color
    */
   public static String getDividerColor()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyDividerColor;
         case "alternate":
            return ThemeColors.alternateDividerColor;
         default:
            return ThemeColors.defaultDividerColor;
      }
   }
   
   /**
    * Get theme-appropriate toggle background color
    */
   public static String getToggleBackground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyToggleBackground;
         case "alternate":
            return ThemeColors.alternateToggleBackground;
         default:
            return ThemeColors.defaultToggleBackground;
      }
   }
   
   /**
    * Get theme-appropriate toggle active color
    */
   public static String getToggleActive()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyToggleActive;
         case "alternate":
            return ThemeColors.alternateToggleActive;
         default:
            return ThemeColors.defaultToggleActive;
      }
   }
   
   /**
    * Get theme-appropriate shadow color
    */
   public static String getShadowColor()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyShadowColor;
         case "alternate":
            return ThemeColors.alternateShadowColor;
         default:
            return ThemeColors.defaultShadowColor;
      }
   }
   
   /**
    * Get theme-appropriate user message background color
    */
   public static String getUserMessageBackground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyUserMessageBackground;
         case "alternate":
            return ThemeColors.alternateUserMessageBackground;
         default:
            return ThemeColors.defaultUserMessageBackground;
      }
   }
   
   /**
    * Get theme-appropriate code background color
    */
   public static String getCodeBackground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyCodeBackground;
         case "alternate":
            return ThemeColors.alternateCodeBackground;
         default:
            return ThemeColors.defaultCodeBackground;
      }
   }
   
   /**
    * Get theme-appropriate pre background color
    */
   public static String getPreBackground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyPreBackground;
         case "alternate":
            return ThemeColors.alternatePreBackground;
         default:
            return ThemeColors.defaultPreBackground;
      }
   }
   
   /**
    * Get theme-appropriate pre border color
    */
   public static String getPreBorder()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyPreBorder;
         case "alternate":
            return ThemeColors.alternatePreBorder;
         default:
            return ThemeColors.defaultPreBorder;
      }
   }
   
   /**
    * Get theme-appropriate background color
    */
   public static String getBackground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyBackground;
         case "alternate":
            return ThemeColors.alternateBackground;
         default:
            return ThemeColors.defaultBackground;
      }
   }
   
   /**
    * Get theme-appropriate button background color
    */
   public static String getButtonBackground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyButtonBackground;
         case "alternate":
            return ThemeColors.alternateButtonBackground;
         default:
            return ThemeColors.defaultButtonBackground;
      }
   }
   
   /**
    * Get theme-appropriate inactive background color
    */
   public static String getInactiveBackground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyInactiveBackground;
         case "alternate":
            return ThemeColors.alternateInactiveBackground;
         default:
            return ThemeColors.defaultInactiveBackground;
      }
   }
   
   /**
    * Get theme-appropriate most inactive background color
    */
   public static String getMostInactiveBackground()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyMostInactiveBackground;
         case "alternate":
            return ThemeColors.alternateMostInactiveBackground;
         default:
            return ThemeColors.defaultMostInactiveBackground;
      }
   }
   
   /**
    * Get theme-appropriate scrollbar thumb color
    */
   public static String getScrollbarThumbColor()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreySubtleText;
         case "alternate":
            return ThemeColors.alternateSubtleText;
         default:
            return ThemeColors.defaultSubtleText;
      }
   }
   
   /**
    * Get theme-appropriate scrollbar track color
    */
   public static String getScrollbarTrackColor()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreyButtonBackground;
         case "alternate":
            return ThemeColors.alternateButtonBackground;
         default:
            return ThemeColors.defaultButtonBackground;
      }
   }
   
   /**
    * Get theme-appropriate section border color
    */
   public static String getSectionBorder()
   {
      String theme = getCurrentTheme();
      switch (theme)
      {
         case "dark-grey":
            return ThemeColors.darkGreySectionBorder;
         case "alternate":
            return ThemeColors.alternateSectionBorder;
         default:
            return ThemeColors.defaultSectionBorder;
      }
   }
}
