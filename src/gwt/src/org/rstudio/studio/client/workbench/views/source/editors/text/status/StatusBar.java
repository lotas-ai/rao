/*
 * StatusBar.java
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
package org.rstudio.studio.client.workbench.views.source.editors.text.status;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Event.NativePreviewEvent;

public interface StatusBar
{
   public static enum StatusBarIconType
   {
      TYPE_OK,
      TYPE_LOADING,
      TYPE_INFO,
      TYPE_WARNING,
      TYPE_ERROR,
   }
   
   interface HideMessageHandler
   {
      // return 'true' to indicate message should be hidden
      boolean onNativePreviewEvent(NativePreviewEvent preview);
   }

   int SCOPE_FUNCTION   = 1;
   int SCOPE_CHUNK      = 2;
   int SCOPE_SECTION    = 3;
   int SCOPE_SLIDE      = 4;
   int SCOPE_CLASS      = 5;
   int SCOPE_NAMESPACE  = 6;
   int SCOPE_LAMBDA     = 7;
   int SCOPE_ANON       = 8;
   int SCOPE_TOP_LEVEL  = 9;
   int SCOPE_TEST       = 10;

   StatusBarElement getPosition();
   StatusBarElement getScope();
   StatusBarElement getLanguage();
   void setPositionVisible(boolean visible);
   void setScopeVisible(boolean visible);
   void setScopeType(int type);
   
   // TODO: Add a 'ShowStatus' API that lets the user provide an icon (button),
   // that also has some kind of click handler for displaying status information.
   
   // NOTE: Uses the same widget as the 'showMessage()' APIs.
   void showStatus(StatusBarIconType type, String message);
   void hideStatus();

   void showMessage(String message);
   void showMessage(String message, boolean hideScopeWidget);
   void showMessage(String message, int timeMs);
   void showMessage(String message, boolean hideScopeWidget, int timeMs);
   void showMessage(String message, HideMessageHandler handler);
   void hideMessage();

   void showNotebookProgress(String label);
   void updateNotebookProgress(int percent);
   void hideNotebookProgress(boolean immediately);
   HandlerRegistration addProgressClickHandler(ClickHandler handler);
   HandlerRegistration addProgressCancelHandler(Command onCanceled);
}
