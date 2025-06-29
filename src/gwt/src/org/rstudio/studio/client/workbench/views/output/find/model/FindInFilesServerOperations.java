/*
 * FindInFilesServerOperations.java
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
package org.rstudio.studio.client.workbench.views.output.find.model;

import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.Void;

import com.google.gwt.core.client.JsArrayString;

public interface FindInFilesServerOperations
{
   void beginFind(String handle,
                  String searchString,
                  boolean regex,
                  boolean isWholeWord,
                  boolean ignoreCase,
                  FileSystemItem directory,
                  JsArrayString includeFilePatterns,
                  JsArrayString excludeFilePatterns,
                  boolean useGitGrep, 
                  boolean excludeGitIgnore,
                  ServerRequestCallback<String> requestCallback);

   void stopFind(String findOperationHandle,
                 ServerRequestCallback<Void> requestCallback);

   void clearFindResults(ServerRequestCallback<Void> requestCallback);

   void previewReplace(String handle,
                       String searchString,
                       boolean regex,
                       boolean isWholeWord,
                       boolean searchIgnoreCase,
                       FileSystemItem dictionary,
                       JsArrayString includeFilePatterns,
                       JsArrayString excludeFilePatterns,
                       boolean useGitGrep,
                       boolean excludeGitIgnore,
                       String replaceString,
                       ServerRequestCallback<String> requestCallback);

   void completeReplace(String handle,
                        String searchString,
                        boolean regex,
                        boolean isWholeWord,
                        boolean searchIgnoreCase,
                        FileSystemItem dictionary,
                        JsArrayString includeFilePatterns,
                        JsArrayString excludeFilePatterns,
                        boolean useGitGrep,
                        boolean excludeGitIgnore,
                        int searchResults,
                        String replaceString,
                        ServerRequestCallback<String> requestCallback);

   void stopReplace(String handle,
                    ServerRequestCallback<Void> requestCallback);
}
