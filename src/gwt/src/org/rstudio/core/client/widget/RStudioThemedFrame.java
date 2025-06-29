/*
 * RStudioThemedFrame.java
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

package org.rstudio.core.client.widget;

import org.rstudio.core.client.BrowseCap;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.application.events.ThemeChangedEvent;
import org.rstudio.studio.client.application.ui.RStudioThemes;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BodyElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.LinkElement;
import com.google.gwt.dom.client.StyleElement;
import com.google.gwt.event.dom.client.LoadEvent;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.inject.Inject;

public class RStudioThemedFrame extends RStudioFrame
                                implements ThemeChangedEvent.Handler
{
   public RStudioThemedFrame(String title)
   {
      this(title, null, null, null, false);
   }

   public RStudioThemedFrame(
      String title,
      String url,
      String customStyle,
      String urlStyle,
      boolean removeBodyStyle)
   {
      this(title,
           url,
           customStyle,
           urlStyle,
           removeBodyStyle,
           true);
   }

   public RStudioThemedFrame(
      String title,
      String url,
      String customStyle,
      String urlStyle,
      boolean removeBodyStyle,
      boolean enableThemes)
   {
      this(title,
           url,
           false,
           null,
           customStyle,
           urlStyle,
           removeBodyStyle,
           enableThemes);
   }

   public RStudioThemedFrame(
      String title,
      String url,
      boolean sandbox,
      String sandboxAllow,
      String customStyle,
      String urlStyle,
      boolean removeBodyStyle,
      boolean enableThemes)
   {
      super(title, url, sandbox, sandboxAllow);
      
      customStyle_ = customStyle;
      urlStyle_ = urlStyle;
      removeBodyStyle_ = removeBodyStyle;
      enableThemes_ = enableThemes;
      
      RStudioGinjector.INSTANCE.injectMembers(this);

      setAceThemeAndCustomStyle(customStyle_, urlStyle_, removeBodyStyle_);
   }

   @Inject
   public void initialize(EventBus events)
   {
      events_ = events;

      events_.addHandler(ThemeChangedEvent.TYPE, this);
   }

   @Override
   public void onThemeChanged(ThemeChangedEvent event)
   {
      setAceThemeAndCustomStyle(customStyle_, urlStyle_, removeBodyStyle_);
   }
   
   private void addThemesStyle(String customStyle, String urlStyle, boolean removeBodyStyle)
   {
      if (getWindow() != null && getWindow().getDocument() != null)
      {
         Document document = getWindow().getDocument();
         if (!isEligibleForCustomStyles(document))
            return;
         
         if (customStyle == null)
            customStyle = "";
         
         customStyle += RES.styles().getText();
         StyleElement style = document.createStyleElement();
         style.setInnerHTML(customStyle);
         document.getHead().appendChild(style);
         
         if (urlStyle != null)
         {
            LinkElement styleLink = document.createLinkElement();
            styleLink.setHref(urlStyle);
            styleLink.setRel("stylesheet");
            document.getHead().appendChild(styleLink);
         }
         
         RStudioGinjector.INSTANCE.getAceThemes().applyTheme(document);
         
         BodyElement body = document.getBody();
         if (body != null)
         {
            if (removeBodyStyle)
               body.removeAttribute("style");
            
            RStudioThemes.initializeThemes(
               RStudioGinjector.INSTANCE.getUserPrefs(),
               RStudioGinjector.INSTANCE.getUserState(),
               document, document.getBody());
            
            body.addClassName("ace_editor_theme");
            
            // Add OS tag to the frame so that it can apply OS-specific CSS if
            // needed.
            body.addClassName(BrowseCap.operatingSystem());
            
            // Add flat theme class. None of our own CSS should use this, but many
            // third party themes developed against earlier versions of RStudio
            // (2021.09 and older) use it extensively in selectors.
            body.addClassName("rstudio-themes-flat");
         }
      }
   }

   private void setAceThemeAndCustomStyle(
      final String customStyle,
      final String urlStyle,
      final boolean removeBodyStyle)
   {
      if (!enableThemes_) return;

      addThemesStyle(customStyle, urlStyle, removeBodyStyle);
      
      this.addLoadHandler(new LoadHandler()
      {      
         @Override
         public void onLoad(LoadEvent arg0)
         {
            addThemesStyle(customStyle, urlStyle, removeBodyStyle);
         }
      });
   }
   
   private static final native boolean isEligibleForCustomStyles(Document document)
   /*-{
      // We disable custom styling for most vignettes, as we cannot guarantee
      // the vignette will remain legible after attempting to re-style with
      // a dark theme.
      
      // If the document contains an 'article', avoid custom styling.
      var articles = document.getElementsByTagName("article");
      if (articles.length !== 0)
         return false;
         
      // If the document uses hljs, avoid custom styling.
      // https://github.com/rstudio/rstudio/issues/11022
      var hljs = document.defaultView.hljs;
      if (hljs != null)
         return false;
         
      return true;
   }-*/;
   
   // Resources ----
   public interface Resources extends ClientBundle
   {
      @Source("RStudioThemedFrame.css")
      Styles styles();
   }

   public interface Styles extends CssResource
   {
   }

   private static Resources RES = GWT.create(Resources.class);
   static
   {
      RES.styles().ensureInjected();
   }

   // Private members ----
   
   private EventBus events_;
   private String customStyle_;
   private String urlStyle_;
   private boolean removeBodyStyle_;
   private boolean enableThemes_;
}
