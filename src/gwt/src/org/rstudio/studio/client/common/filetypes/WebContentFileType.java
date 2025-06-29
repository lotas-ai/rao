/*
 * WebContentFileType.java
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
package org.rstudio.studio.client.common.filetypes;

import java.util.HashSet;

import org.rstudio.core.client.command.AppCommand;
import org.rstudio.studio.client.common.reditor.EditorLanguage;
import org.rstudio.studio.client.workbench.commands.Commands;

import com.google.gwt.resources.client.ImageResource;

public class WebContentFileType extends TextFileType
{
   WebContentFileType(String id,
                      String label,
                      EditorLanguage editorLanguage,
                      String defaultExtension,
                      ImageResource icon,
                      boolean isMarkdown,
                      boolean canSourceOnSave)
   {
      super(id, 
            label, 
            editorLanguage, 
            defaultExtension,
            icon,
            WordWrap.DEFAULT,    // word-wrap
            canSourceOnSave, 
            isMarkdown, // allow code execution in markdown 
            false, 
            false,
            true,    // preview-html
            false,
            false, 
            false,
            false,
            true,
            false,
            false);
   }
   
   @Override
   public HashSet<AppCommand> getSupportedCommands(Commands commands)
   {
      HashSet<AppCommand> result = super.getSupportedCommands(commands);
      return result;
   }
}
