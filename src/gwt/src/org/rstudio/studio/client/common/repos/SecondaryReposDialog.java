/*
 * SecondaryReposDialog.java
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
package org.rstudio.studio.client.common.repos;

import java.util.ArrayList;

import org.rstudio.core.client.Debug;
import org.rstudio.core.client.DialogOptions;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.widget.FocusHelper;
import org.rstudio.core.client.widget.FormLabel;
import org.rstudio.core.client.widget.ModalDialog;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.core.client.widget.ProgressIndicator;
import org.rstudio.core.client.widget.SimplePanelWithProgress;
import org.rstudio.core.client.widget.images.ProgressImages;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.common.mirrors.model.CRANMirror;
import org.rstudio.studio.client.common.mirrors.model.MirrorsServerOperations;
import org.rstudio.studio.client.common.mirrors.model.RepoValidationResult;
import org.rstudio.studio.client.common.repos.model.SecondaryReposResult;
import org.rstudio.studio.client.common.repos.model.SecondaryReposServerOperations;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.prefs.PrefsConstants;

import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class SecondaryReposDialog extends ModalDialog<CRANMirror>
{
   public SecondaryReposDialog(OperationWithInput<CRANMirror> operation,
                               ArrayList<String> excluded,
                               String cranRepoUrl,
                               boolean cranIsCustom)
   {
      super(constants_.secondaryReposDialog(),
            Roles.getDialogRole(), operation);

      excluded_ = excluded;
      cranRepoUrl_ = cranRepoUrl;
      cranIsCustom_ = cranIsCustom;

      DialogOptions options = new DialogOptions();
      options.width = "600px";
      progressIndicator_ = addProgressIndicator(false, true, options);

      RStudioGinjector.INSTANCE.injectMembers(this);
   }

   @Override
   protected CRANMirror collectInput()
   {
      if (!StringUtil.isNullOrEmpty(nameTextBox_.getText()) &&
          !StringUtil.isNullOrEmpty(urlTextBox_.getText()))
      {
         CRANMirror cranMirror = CRANMirror.empty();

         cranMirror.setName(nameTextBox_.getText());
         cranMirror.setURL(urlTextBox_.getText());

         cranMirror.setHost(CRANMirror.getCustomEnumValue());

         return cranMirror;
      }
      else if (listBox_ != null && listBox_.getSelectedIndex() >= 0)
      {
         CRANMirror cranMirror = repos_.get(listBox_.getSelectedIndex());
         cranMirror.setHost(CRANMirror.getSecondaryEnumValue());
         return cranMirror;
      }
      else
      {
         return null;
      }
   }

   @Inject
   void initialize(GlobalDisplay globalDisplay,
                   SecondaryReposServerOperations server,
                   MirrorsServerOperations mirrorOperations)
   {
      globalDisplay_ = globalDisplay;
      secondaryReposServer_ = server;
      mirrorOperations_ = mirrorOperations;
   }

   protected boolean validateSync(CRANMirror input)
   {
      if (input == null)
      {
         globalDisplay_.showErrorMessage(constants_.showErrorCaption(),
                                         constants_.validateSyncLabel());
         return false;
      }

      if (excluded_.contains(input.getName()))
      {
         globalDisplay_.showErrorMessage(constants_.showErrorCaption(),
               constants_.showErrorRepoMessage() + input.getName() + " " + constants_.alreadyIncludedMessage());
         return false;
      }

      return true;
   }

   @Override
   protected void validateAsync(CRANMirror input,
                                OperationWithInput<Boolean> onValidated)
   {
      if (!validateSync(input))
      {
         onValidated.execute(false);
         return;
      }

      if (input.isCustom())
      {
         progressIndicator_.onProgress(constants_.validateAsyncProgress());

         mirrorOperations_.validateCranRepo(input.getURL(), new ServerRequestCallback<RepoValidationResult>()
         {
            @Override
            public void onResponseReceived(RepoValidationResult result)
            {
               progressIndicator_.onCompleted();

               if (result.isValid())
               {
                  onValidated.execute(true);
               }
               else
               {
                  String message = constants_.onResponseReceived();
                  String errorMessage = result.getErrorMessage();
                  if (!StringUtil.isNullOrEmpty(errorMessage))
                     message = message + "\n\n" + errorMessage;
                  
                  progressIndicator_.onError(message);
                  onValidated.execute(false);
               }
            }

            @Override
            public void onError(ServerError error)
            {
               progressIndicator_.onCompleted();

               Debug.logError(error);
               progressIndicator_.onError(error.getMessage());

               onValidated.execute(false);
            }
         });
      }
      else
      {
         onValidated.execute(true);
      }
   }

   @Override
   protected Widget createMainWidget()
   {
      VerticalPanel root = new VerticalPanel();

      HorizontalPanel customPanel = new HorizontalPanel();
      customPanel.setStylePrimaryName(RESOURCES.styles().customPanel());
      root.add(customPanel);

      VerticalPanel namePanel = new VerticalPanel();
      namePanel.setStylePrimaryName(RESOURCES.styles().namePanel());
      nameTextBox_ = new TextBox();
      nameTextBox_.setStylePrimaryName(RESOURCES.styles().nameTextBox());
      FormLabel nameLabel = new FormLabel(constants_.nameLabel(), nameTextBox_);
      namePanel.add(nameLabel);
      namePanel.add(nameTextBox_);
      customPanel.add(namePanel);

      VerticalPanel urlPanel = new VerticalPanel();
      urlTextBox_ = new TextBox();
      urlTextBox_.setStylePrimaryName(RESOURCES.styles().urlTextBox());
      FormLabel urlLabel = new FormLabel(constants_.urlLabel(), urlTextBox_);
      urlPanel.add(urlLabel);
      urlPanel.add(urlTextBox_);
      customPanel.add(urlPanel);

      reposLabel_ = new FormLabel(constants_.reposLabel());
      reposLabel_.getElement().getStyle().setMarginTop(8, Unit.PX);
      root.add(reposLabel_);

      panel_ = new SimplePanelWithProgress(ProgressImages.createLargeGray());
      root.add(panel_);

      panel_.setStylePrimaryName(RESOURCES.styles().mainWidget());

      // show progress (with delay)
      panel_.showProgress(200);
      showPanel(false);

      // query data source for packages
      secondaryReposServer_.getSecondaryRepos(new SimpleRequestCallback<SecondaryReposResult>()
      {

         @Override
         public void onResponseReceived(SecondaryReposResult result)
         {
            if (!StringUtil.isNullOrEmpty(result.getError()))
            {
               globalDisplay_.showErrorMessage(constants_.showErrorCaption(),
                     result.getError());
               setText(constants_.secondaryRepoLabel());
               return;
            }

            JsArray<CRANMirror> repos = result.getRepos();
            // keep internal list of mirrors
            repos_ = new ArrayList<>(repos.length());

            // create list box and select default item
            listBox_ = new ListBox();
            listBox_.setMultipleSelect(false);
            listBox_.setVisibleItemCount(10);
            listBox_.setWidth("100%");
            reposLabel_.setFor(listBox_);
            if (repos.length() > 0)
            {
               for(int i=0; i<repos.length(); i++)
               {
                  CRANMirror repo = repos.get(i);
                  
                  String repoUrl = repo.getURL();
                  if (repoUrl.length() > 0 &&
                      cranRepoUrl_.length() > 0) {
                      char mainEnd = StringUtil.charAt(cranRepoUrl_, cranRepoUrl_.length() - 1);
                      char repoEnd = StringUtil.charAt(repo.getURL(), repoUrl.length() - 1);
                      if (mainEnd == '/' && repoEnd != '/')
                         repoUrl = repoUrl + "/";
                      else if (mainEnd != '/' && repoEnd == '/')
                         repoUrl = StringUtil.substring(repoUrl, 0, repoUrl.length() - 1);
                  }
                  
                  if (!StringUtil.isNullOrEmpty(repo.getName()) &&
                      !repo.getName().toLowerCase().equals("cran") &&
                      !repoUrl.equals(cranRepoUrl_))
                  {
                     repos_.add(repo);
                     listBox_.addItem(repo.getName(), repo.getURL());
                  }
               }
               
               listBox_.setSelectedIndex(0);
            }

            showPanel(listBox_.getItemCount() > 0);
            
            panel_.setWidget(listBox_);
            
            setText(constants_.secondaryRepoLabel());

            listBox_.addDoubleClickHandler(new DoubleClickHandler() {
               @Override
               public void onDoubleClick(DoubleClickEvent event)
               {
                  clickOkButton();
               }
            });
            
            final int kDefaultPanelHeight = 265;
            if (listBox_.getOffsetHeight() > kDefaultPanelHeight)
               panel_.setHeight(listBox_.getOffsetHeight() + "px");

            FocusHelper.setFocusDeferred(listBox_);
         }
         
         @Override
         public void onError(ServerError error)
         {
            closeDialog();
            super.onError(error);
         }
      }, cranRepoUrl_, cranIsCustom_);

      return root;
   }

   private void showPanel(boolean show)
   {
      reposLabel_.setVisible(show);
      panel_.setVisible(show);
   }
   
   static interface Styles extends CssResource
   {
      String mainWidget();
      String customPanel();
      String namePanel();
      String urlTextBox();
      String nameTextBox();
   }

   static interface Resources extends ClientBundle
   {
      @Source("SecondaryReposDialog.css")
      Styles styles();
   }
   
   static Resources RESOURCES = GWT.create(Resources.class);
   
   public static void ensureStylesInjected()
   {
      RESOURCES.styles().ensureInjected();
   }
   
   private SecondaryReposServerOperations secondaryReposServer_ = null;
   private GlobalDisplay globalDisplay_ = null;
   private ArrayList<CRANMirror> repos_ = null;
   private ListBox listBox_ = null;
   private TextBox nameTextBox_ = null;
   private TextBox urlTextBox_ = null;
   private ArrayList<String> excluded_;
   private String cranRepoUrl_;
   private boolean cranIsCustom_;

   private FormLabel reposLabel_;
   private SimplePanelWithProgress panel_;

   private MirrorsServerOperations mirrorOperations_;
   private ProgressIndicator progressIndicator_;
   private static final PrefsConstants constants_ = GWT.create(PrefsConstants.class);

}
