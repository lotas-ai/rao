/*
 * DataImportDialog.java
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

package org.rstudio.studio.client.workbench.views.environment.dataimport;

import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.GWT;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.core.client.widget.ModalDialog;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.studio.client.RStudioGinjector;

import com.google.gwt.user.client.ui.Widget;
import org.rstudio.studio.client.workbench.views.environment.ViewEnvironmentConstants;

public class DataImportDialog extends ModalDialog<String>
{
   private DataImport dataImport_;
   
   public DataImportDialog(DataImportModes dataImportMode,
                           String caption,
                           String path,
                           OperationWithInput<String> operation)
   {
      super(caption, Roles.getDialogRole(), operation);
      
      dataImport_ = new DataImport(dataImportMode, addProgressIndicator(false), path);
      RStudioGinjector.INSTANCE.injectMembers(this);
      
      setOkButtonCaption(constants_.importCapitalized());
      setEnterDisabled(true);
      
      setHelpLink(dataImport_.getHelpLink());
   }
   
   @Override
   protected void onLoad()
   {
      super.onLoad();
      
      String zIndexStyle = DomUtils.getComputedStyles(getElement()).getZIndex();
      dataImport_.setZIndex(zIndexStyle != "" ? Integer.parseInt(zIndexStyle) : null);
   }

   @Override
   protected String collectInput()
   {
      return dataImport_.getCode();
   }

   @Override
   protected Widget createMainWidget()
   {
      return dataImport_;
   }
   private static final ViewEnvironmentConstants constants_ = GWT.create(ViewEnvironmentConstants.class);
}
