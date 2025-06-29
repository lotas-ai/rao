/*
 * HyperlinkResources.java
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;

public interface HyperlinkResources extends ClientBundle
{
    public static final HyperlinkResources INSTANCE = GWT.create(HyperlinkResources.class);

    @CssResource.NotStrict
    HyperlinkStyles hyperlinkStyles();

    public interface HyperlinkStyles extends CssResource
    {
        String hyperlink();
        String hyperlinkUnsupported();

        String hyperlinkPopup();

        String warning();

        String hyperlinkPopupHeader();
        String hyperlinkPopupHeaderLeft();
        String hyperlinkPopupHeaderRight();
        String hyperlinkPopupHeaderRun();
        String hyperlinkPopupHeaderHelp();
        String hyperlinkPopupHeaderAi();
        
        String helpPreview();
        String helpPreviewTitle();
        String helpPreviewDescription();

        String aiPreview();
        String aiPreviewTitle();
        String aiPreviewDescription();

    }
}
