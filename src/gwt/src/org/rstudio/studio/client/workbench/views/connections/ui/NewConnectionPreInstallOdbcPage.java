/*
 * NewConnectionPreInstallOdbcPage.java
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
package org.rstudio.studio.client.workbench.views.connections.ui;

import com.google.gwt.core.client.GWT;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.resources.ImageResourceUrl;
import org.rstudio.core.client.widget.Operation;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.core.client.widget.ProgressIndicator;
import org.rstudio.core.client.widget.WizardIntermediatePage;
import org.rstudio.studio.client.workbench.views.connections.ConnectionsConstants;
import org.rstudio.studio.client.workbench.views.connections.model.ConnectionOptions;
import org.rstudio.studio.client.workbench.views.connections.model.NewConnectionContext;
import org.rstudio.studio.client.workbench.views.connections.model.NewConnectionInfo;

import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.user.client.ui.Widget;

public class NewConnectionPreInstallOdbcPage 
   extends WizardIntermediatePage<NewConnectionContext, ConnectionOptions>
{
   public NewConnectionPreInstallOdbcPage(final NewConnectionInfo info, String subTitle)
   {
      super(
         info.getName(),
         subTitle,
         constants_.installOdbcCaption(info.getName()),
         StringUtil.isNullOrEmpty(info.iconData()) ? null : new ImageResourceUrl(
            new SafeUri()
            {
               @Override
               public String asString()
               {
                  return info.iconData();
               }
            },
            16,
            16
         ),
         null,
         new NewConnectionInstallOdbcPage(info, subTitle)
      );
      
      info_ = info;
      options_ = ConnectionOptions.create("", "");
      contents_.setIntermediateResult(options_);
      contents_.initializeInfo(info_);
   }

   @Override
   public void focus()
   {
   }
   
   @Override
   public void onActivate(ProgressIndicator indicator)
   {
   }

   @Override
   public void onDeactivate(Operation operation)
   {
      contents_.onDeactivate(operation);
   }

   @Override
   protected ConnectionOptions collectInput()
   {
      return contents_.collectInput();
   }
   
   @Override
   protected Widget createWidget()
   {
      contents_ = new NewConnectionPreInstallOdbcHost();

      return contents_;
   }

   @Override
   protected void initialize(NewConnectionContext initData)
   {
   }

   @Override
   protected boolean validate(ConnectionOptions input)
   {
      return true;
   }

   @Override
   public void collectIntermediateInput(
         final ProgressIndicator indicator, 
         final OperationWithInput<ConnectionOptions> onResult) 
   {
      options_ = contents_.collectInput();
      onResult.execute(options_);
   }

   @Override
   protected String getWizardPageBackgroundStyle()
   {
      return NewConnectionWizard.RES.styles().newConnectionWizardBackground();
   }
   
   private NewConnectionPreInstallOdbcHost contents_;
   private NewConnectionInfo info_;
   private ConnectionOptions options_;
   private static final ConnectionsConstants constants_ = GWT.create(ConnectionsConstants.class);
}
