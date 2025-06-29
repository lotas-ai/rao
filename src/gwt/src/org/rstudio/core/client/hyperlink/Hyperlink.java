/*
 * Hyperlink.java
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
package org.rstudio.core.client.hyperlink;

import java.util.Map;
import java.util.TreeMap;

import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.Timer;

import org.rstudio.core.client.CommandWithArg;
import org.rstudio.core.client.Rectangle;

public abstract class Hyperlink implements HelpPageShower
{
    public Hyperlink(String url, Map<String, String> params, String text, String clazz)
    {
        this.url = url;
        this.text = text;
        this.clazz = clazz;
        this.params = params;

        anchor_ = Document.get().createAnchorElement();
        styles_ = RES.hyperlinkStyles();
        popup_ = new HyperlinkPopupPanel(this);

        anchor_.setInnerText(text);
        anchor_.setClassName(getAnchorClass());
        
        if (clazz != null)
            anchor_.addClassName(clazz);
        
        cancelPopup_ = false;
        timer_ = new Timer()
        {
            @Override
            public void run()
            {
                getPopupContent((content) -> {
                    if (!cancelPopup_)
                    {
                        popup_.setContent(content);

                        Rectangle bounds = new Rectangle(anchor_.getAbsoluteLeft(), anchor_.getAbsoluteBottom(), anchor_.getClientWidth(), anchor_.getClientHeight());
                        HyperlinkPopupPositioner positioner = new HyperlinkPopupPositioner(bounds, popup_);
                        popup_.setPopupPositionAndShow(positioner);
                    }
                    
                });
            }
        };

        Event.sinkEvents(anchor_, Event.ONMOUSEOVER | Event.ONMOUSEOUT | Event.ONCLICK);
        Event.setEventListener(anchor_, event ->
        {
            if (event.getTypeInt() == Event.ONMOUSEOVER)
            {   
                // cancel previous timer
                timer_.cancel();

                // the link was just hovered: setting cancelPopup_ to false
                // to signal that the popup should be shown
                cancelPopup_ = false;

                // but with some delay
                timer_.schedule(400);
            } 
            else if (event.getTypeInt() == Event.ONCLICK) 
            {
                // link was clicked. Various cleanup before calling onClick():

                // - cancel the timer if possible so that the popup is not shown
                timer_.cancel();

                // - also set cancelPopup_ to true in case the timer has finished
                //   but the getPopupContent() call has not
                cancelPopup_ = true;

                // - if the popup was visible: hide it
                popup_.hide();

                onClick();
            }
            else if (event.getTypeInt() == Event.ONMOUSEOUT)
            {
                // hide the popup if it is visible, or 
                // attempt to prevent it from showing
                timer_.cancel();
                cancelPopup_ = true;

                popup_.hide();
            }
        });
    
    }

    public Element getElement()
    {
        return anchor_;
    }

    public String getAnchorClass()
    {
        return styles_.hyperlink();
    }

    public abstract void onClick();
    
    public void getPopupContent(CommandWithArg<Widget> onReady){}
    
    @Override
    public void showHelp(){}

    public static Hyperlink create(String url, String paramsTxt, String text, String clazz)
    {
        // [params] of the form key1=value1:key2=value2
        Map<String, String> params = new TreeMap<>();
        if (paramsTxt.length() > 0)
        {
            for (String param: paramsTxt.split(":"))
            {
                String[] bits = param.split("=");
                String key = bits[0].trim();
                String value = bits[1].trim();
                params.put(key, value);
            }
        }
        if (FileHyperlink.handles(url))
        {
            return new FileHyperlink(url, params, text, clazz);
        }
        else if (WebHyperlink.handles(url))
        {
            return new WebHyperlink(url, params, text, clazz);
        }
        else if (HelpHyperlink.handles(url, params))
        {
            return new HelpHyperlink(url, params, text, clazz);
        }
        else if (VignetteHyperlink.handles(url, params))
        {
            return new VignetteHyperlink(url, params, text, clazz);
        }
        else if (LibraryHyperlink.handles(url))
        {
            return new LibraryHyperlink(url, params, text, clazz);
        }
        else if (RunHyperlink.handles(url))
        {
            return new RunHyperlink(url, params, text, clazz);
        }
        else 
        {
            return new UnsupportedHyperlink(url, params, text, clazz);
        }    
    }

    public String url;
    public String text;
    public String clazz;
    public Map<String, String> params;
    protected AnchorElement anchor_;
    private Timer timer_;
    private boolean cancelPopup_;
    
    protected final HyperlinkResources.HyperlinkStyles styles_;
    private final HyperlinkPopupPanel popup_;
    
    private static HyperlinkResources RES = HyperlinkResources.INSTANCE;
}