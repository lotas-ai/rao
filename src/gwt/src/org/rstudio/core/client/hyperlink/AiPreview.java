/*
 * AiPreview.java
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
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;

import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.common.codetools.RCompletionType;
import org.rstudio.studio.client.server.Server;
import org.rstudio.studio.client.workbench.views.ai.model.AiInfo;
import org.rstudio.studio.client.workbench.views.ai.model.AiInfo.ParsedInfo;

public class AiPreview extends Composite
{
    public AiPreview(String topic, String pkgName, Command onReady)
    {
        super();
        
        topic_ = topic;
        pkgName_ = pkgName;
        server_ = RStudioGinjector.INSTANCE.getServer();
        panel_ = new VerticalPanel();
        
        initWidget(panel_);

        server_.isPackageInstalled(pkgName_, null, new SimpleRequestCallback<Boolean>()
        {
            @Override
            public void onResponseReceived(Boolean installed)
            {
                if (installed)
                {
                    server_.getAi(topic_, pkgName_, RCompletionType.FUNCTION, new SimpleRequestCallback<AiInfo>()
                    {
                        @Override
                        public void onResponseReceived(AiInfo response)
                        {
                            if (response != null)
                            {
                                VerticalPanel previewPanel = new VerticalPanel();
                                previewPanel.setStyleName(HyperlinkResources.INSTANCE.hyperlinkStyles().aiPreview());
            
                                ParsedInfo parsed = response.parse(pkgName_ + "::" + topic_);
                                Label title = new Label(parsed.getTitle());
                                title.setStyleName(HyperlinkResources.INSTANCE.hyperlinkStyles().aiPreviewTitle());
            
                                HTML description = new HTML(parsed.getDescription());
                                description.setStyleName(HyperlinkResources.INSTANCE.hyperlinkStyles().aiPreviewDescription());
                                
                                previewPanel.add(title);
                                previewPanel.add(description);
                                panel_.add(previewPanel);
                            }
                            else 
                            {
                                panel_.add(new WarningLabel(constants_.noDocumentation()));
                            }
            
                            onReady.execute();
                        }            
                    });
                } 
                else 
                {
                    panel_.add(new WarningLabel(constants_.noPackage()));

                    onReady.execute();
                }
            }
        });
        
    }
   
    private VerticalPanel panel_;
    
    private String topic_;
    private String pkgName_;
    private Server server_;

    private static final HyperlinkConstants constants_ = GWT.create(HyperlinkConstants.class);
}
