/*
 * CompletionPopupPanel.java
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
package org.rstudio.studio.client.workbench.views.console.shell.assist;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rstudio.core.client.ElementIds;
import org.rstudio.core.client.Rectangle;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.command.KeyCombination;
import org.rstudio.core.client.command.KeyboardShortcut;
import org.rstudio.core.client.command.ShortcutManager;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.core.client.events.SelectionCommitEvent;
import org.rstudio.core.client.widget.ThemedPopupPanel;
import org.rstudio.studio.client.workbench.views.console.ConsoleConstants;
import org.rstudio.studio.client.workbench.views.console.ConsoleResources;
import org.rstudio.studio.client.workbench.views.console.shell.assist.CompletionRequester.QualifiedName;
import org.rstudio.studio.client.workbench.views.help.model.HelpInfo.ParsedInfo;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

public class CompletionPopupPanel extends ThemedPopupPanel
      implements CompletionPopupDisplay
{
   public CompletionPopupPanel()
   {
      super();
      autoConstrain_ = false;
      styles_ = ConsoleResources.INSTANCE.consoleStyles();

      help_ = new HelpInfoPopupPanel();
      help_.setWidth("400px");

      truncated_ = new Label(constants_.notAllItemsShownText());
      truncated_.setStylePrimaryName(styles_.truncatedLabel());

      setStylePrimaryName(styles_.completionPopup());

      addCloseHandler(new CloseHandler<PopupPanel>() {

         @Override
         public void onClose(CloseEvent<PopupPanel> event)
         {
            hideAll();
         }
      });

      WindowEx.addBlurHandler(new BlurHandler()
      {
         @Override
         public void onBlur(BlurEvent event)
         {
            hideAll();
         }
      });
   }

   private void hideAll()
   {
      hide();
      help_.hide();
   }

   public void placeOffscreen()
   {
      setPopupPosition(-10000, -10000);
      help_.setPopupPosition(-10000, -10000);
   }

   public boolean isOffscreen()
   {
      return getAbsoluteLeft() + getOffsetWidth() < 0 &&
             getAbsoluteTop() + getOffsetHeight() < 0;
   }

   public void showProgress(String progress, PositionCallback callback)
   {
      setText(progress);
      show(callback);
   }

   public void showErrorMessage(String error, PositionCallback callback)
   {
      setText(error);
      show(callback);
   }

   @Override
   public void clearCompletions()
   {
      list_ = null;
   }

   @Override
   public void showCompletionValues(QualifiedName[] values,
                                    PositionCallback callback,
                                    boolean truncated)
   {
      registerIgnoredKeys();
      
      // If we're going to display completions on top, then reverse
      // the completion list, and select the last values instead of the first.
      boolean isShowingOnBottom = false;
      if (callback instanceof PopupPositioner)
      {
         PopupPositioner positioner = (PopupPositioner) callback;
         isShowingOnBottom = positioner.getPreferBottom();
      }
      
      if (!isShowingOnBottom)
      {
         List<QualifiedName> names = Arrays.asList(values);
         Collections.reverse(names);
         values = names.toArray(new QualifiedName[0]);
      }
      
      // Since completion popups are usually displayed from the Source pane,
      // if we're trying to display completions on top of the cursor location,
      // we'll have relatively less space available. In that case, try to display
      // fewer items, so we can fit the visible space.
      int numVisibleItems = isShowingOnBottom ? 9 : 6;
      CompletionList<QualifiedName> list = new CompletionList<>(values, numVisibleItems, true, true);

      list.addSelectionCommitHandler((SelectionCommitEvent<QualifiedName> event) ->
      {
         lastSelectedValue_ = event.getSelectedItem();
         SelectionCommitEvent.fire(CompletionPopupPanel.this,
                                   event.getSelectedItem());
      });

      list.addSelectionHandler(new SelectionHandler<QualifiedName>() {
         public void onSelection(SelectionEvent<QualifiedName> event)
         {
            lastSelectedValue_ = event.getSelectedItem();
            SelectionEvent.fire(CompletionPopupPanel.this,
                                event.getSelectedItem());
         }
      });

      list_ = list;

      container_ = new VerticalPanel();
      container_.add(list_);
      if (truncated)
         container_.add(truncated_);

      setWidget(container_);
      ElementIds.assignElementId(list_.getElement(), ElementIds.POPUP_COMPLETIONS);

      show(callback);
      
      if (!isShowingOnBottom)
         selectLast();
   }

   public boolean hasCompletions()
   {
      if (list_ == null)
         return false;
      return list_.getItemCount() > 0;
   }

   public int numAvailableCompletions()
   {
      return list_.getItemCount();
   }

   private void show(PositionCallback callback)
   {
      registerNativeFocusHandler();

      if (callback != null)
         setPopupPositionAndShow(callback);
      else
         show();


      // Fudge the completion width when the scrollbar is visible. This has
      // to be done before help is shown since the help display is offset
      // from the completion list. This also (implicitly) adds padding between
      // the completion item and the package name (if it exists)
      if (list_ != null && list_.getOffsetWidth() > list_.getElement().getClientWidth())
         list_.setWidth(list_.getOffsetWidth() + 30 + "px");

      // Show help.
      if (help_ != null)
      {
         if (completionListIsOnScreen())
            resolveHelpPosition(help_.isVisible());
         else
            help_.hide();
      }
   }

   @Override
   public void hide()
   {
      unregisterIgnoredKeys();
      unregisterNativeHandler();
      super.hide();
   }

   public QualifiedName getSelectedValue()
   {
      if (list_ == null || !list_.isAttached())
         return null;

      return list_.getSelectedItem();
   }

   public QualifiedName getLastSelectedValue()
   {
      return lastSelectedValue_;
   }

   public Rectangle getSelectionRect()
   {
      return list_.getSelectionRect();
   }

   public boolean selectNext()
   {
      return list_.selectNext();
   }

   public boolean selectPrev()
   {
      return list_.selectPrev();
   }

   public boolean selectPrevPage()
   {
      return list_.selectPrevPage();
   }

   public boolean selectNextPage()
   {
      return list_.selectNextPage();
   }

   public boolean selectFirst()
   {
      return list_.selectFirst();
   }

   public boolean selectLast()
   {
      return list_.selectLast();
   }

   public void setHelpVisible(boolean visible)
   {
      help_.setVisible(visible);
   }

   private boolean completionListIsOnScreen()
   {
      return list_ != null && list_.isAttached() &&
             getAbsoluteLeft() >= 0 && getAbsoluteTop() >= 0;
   }

   @Override
   public void displayHelp(ParsedInfo help)
   {
      if (!completionListIsOnScreen())
         return;

      help_.displayHelp(help);
      resolveHelpPosition(help.hasInfo());
   }

   @Override
   public void displayParameterHelp(Map<String, String> map, String parameterName)
   {
      if (!completionListIsOnScreen())
         return;

      help_.displayParameterHelp(map, parameterName);
      resolveHelpPosition(map.get(parameterName) != null);
   }

   @Override
   public void displayPackageHelp(ParsedInfo help)
   {
      if (!completionListIsOnScreen())
         return;

      help_.displayPackageHelp(help);
      resolveHelpPosition(help.hasInfo());

   }

   private void resolveHelpPosition(boolean setVisible)
   {
      int top = getAbsoluteTop();
      int left = getAbsoluteLeft();
      int bottom = top + getOffsetHeight() + 9;
      int width = getWidget().getOffsetWidth();

      if (!help_.isShowing())
         help_.show();

      // If displaying the help with the top aligned to the completion list
      // would place the help offscreen, then re-align it so that the bottom of the
      // help is aligned with the completion popup.
      //
      // NOTE: Help has not been positioned yet so what we're really asking is,
      // 'if we align the top of help with the top of the completion list, will
      // it flow offscreen?'

      if (top + help_.getOffsetHeight() + 20 > Window.getClientHeight())
         top = bottom - help_.getOffsetHeight() - 9;

      // It is also possible that the help could flow offscreen to the right,
      // e.g. if the user has decided to place the source / console windows on
      // the right. Check for this and position help to the left if it prevents
      // overflow.
      //
      // It's a bit strange having help to the left of the completion list but
      // it's better than the alternative (having help overflow on the right)
      int destinationLeft = left + width - 4;
      int destinationTop = top + 3;

      if (destinationLeft + help_.getOffsetWidth() > Window.getClientWidth())
      {
         int potentialLeft = left - help_.getOffsetWidth();
         if (potentialLeft > 0)
            destinationLeft = potentialLeft;
      }

      help_.setPopupPosition(destinationLeft, destinationTop);
      help_.setVisible(setVisible);
   }

   @Override
   public void displaySnippetHelp(String contents)
   {
      if (!completionListIsOnScreen())
         return;

      help_.displaySnippetHelp(contents);
      resolveHelpPosition(!StringUtil.isNullOrEmpty(contents));
   }

   @Override
   public void displayRoxygenHelp(String title, String description, boolean hasVignette)
   {
      if (!completionListIsOnScreen())
         return;
      
      help_.displayRoxygenHelp(title, description, hasVignette);
      resolveHelpPosition(true);
   }
   
   @Override
   public void displayYAMLHelp(String value, String description)
   {
      if (!completionListIsOnScreen())
         return;
      
      // don't show if there's no description
      if (StringUtil.isNullOrEmpty(description))
         return;

      // strip ": " off the end
      value = value.replace(": ", "");
      
      // add <p> around description (use an inline style b/c we aren't
      // sure how other users of the help tooltip construct their 
      // elements/css and don't want our tweaks to regress them)
      description = "<p style='padding-left: 3px;'>" + description + "</p>";
      
      help_.displayParameterHelp(value, description, false);
      resolveHelpPosition(!StringUtil.isNullOrEmpty(description));
   }

   public void clearHelp(boolean downloadOperationPending)
   {
      help_.clearHelp(downloadOperationPending);
   }

   public HandlerRegistration addSelectionHandler(
         SelectionHandler<QualifiedName> handler)
   {
      return addHandler(handler, SelectionEvent.getType());
   }

   public HandlerRegistration addSelectionCommitHandler(
         SelectionCommitEvent.Handler<QualifiedName> handler)
   {
      return addHandler(handler, SelectionCommitEvent.getType());
   }

   public HandlerRegistration addMouseDownHandler(MouseDownHandler handler)
   {
      return addDomHandler(handler, MouseDownEvent.getType());
   }

   public boolean isHelpVisible()
   {
      return help_.isVisible() && help_.isShowing() &&
            !isOffscreen();
   }

   private HTML setText(String text)
   {
      HTML contents = new HTML();
      contents.setText(text);
      setWidget(contents);
      return contents;
   }

   public QualifiedName[] getItems()
   {
      return list_.getItems();
   }
   
   private void registerNativeFocusHandler()
   {
      focusHandler_ = registerNativeFocusHandlerImpl();
   }
   
   private final native JavaScriptObject registerNativeFocusHandlerImpl()
   /*-{
      var self = this;
      var handler = $entry(function(event) {
         self.@org.rstudio.studio.client.workbench.views.console.shell.assist.CompletionPopupPanel::onFocusIn(*)(event);
      });
      $doc.addEventListener("focusin", handler);
      $doc.addEventListener("mousedown", handler);
      return handler;
   }-*/;

   private void onFocusIn(NativeEvent event)
   {
      Element targetEl = event.getEventTarget().cast();
      boolean isActiveClickTarget =
            (container_ != null && container_.getElement().isOrHasChild(targetEl)) ||
            (help_ != null && help_.getElement().isOrHasChild(targetEl));

      if (!isActiveClickTarget)
         hideAll();
   }

   private void unregisterNativeHandler()
   {
      unregisterNativeFocusHandlerImpl(focusHandler_);
   }
   
   private final native void unregisterNativeFocusHandlerImpl(JavaScriptObject focusHandler)
   /*-{
      $doc.removeEventListener("focusin", focusHandler);
      $doc.removeEventListener("mousedown", focusHandler);
   }-*/;

   private void resetIgnoredKeysHandle()
   {
      if (handle_ != null)
      {
         handle_.close();
         handle_ = null;
      }
   }

   private void registerIgnoredKeys()
   {
      resetIgnoredKeysHandle();
      Set<KeyCombination> keySet = new HashSet<>();
      keySet.add(new KeyCombination("n", KeyCodes.KEY_N, KeyboardShortcut.CTRL));
      keySet.add(new KeyCombination("p", KeyCodes.KEY_P, KeyboardShortcut.CTRL));
      handle_ = ShortcutManager.INSTANCE.addIgnoredKeys(keySet);
   }

   private void unregisterIgnoredKeys()
   {
      resetIgnoredKeysHandle();
   }

   private CompletionList<QualifiedName> list_;
   private HelpInfoPopupPanel help_;
   private final ConsoleResources.ConsoleStyles styles_;
   private static QualifiedName lastSelectedValue_;
   private VerticalPanel container_;
   private final Label truncated_;
   private JavaScriptObject focusHandler_;
   private ShortcutManager.Handle handle_;
   private static final ConsoleConstants constants_ = GWT.create(ConsoleConstants.class);

}
