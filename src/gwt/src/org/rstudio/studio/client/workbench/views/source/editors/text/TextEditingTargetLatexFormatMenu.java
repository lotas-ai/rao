/*
 * TextEditingTargetLatexFormatMenu.java
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
package org.rstudio.studio.client.workbench.views.source.editors.text;

import org.rstudio.core.client.widget.ToolbarPopupMenu;
import org.rstudio.studio.client.workbench.prefs.model.UserPrefs;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Position;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.MenuItem;

public class TextEditingTargetLatexFormatMenu extends ToolbarPopupMenu
{
   public TextEditingTargetLatexFormatMenu(DocDisplay editor, UserPrefs prefs)
   {
      editor_ = editor;
      prefs_ = prefs;
      
      addItem(createLatexMenu(constants_.section(), "section*", true));
      addItem(createLatexMenu(constants_.subsection(), "subsection*", true));
      addItem(createLatexMenu(constants_.subSubsection(), "subsubsection*", true));
      addSeparator();
      addItem(createLatexMenu(constants_.bold(), "textbf"));
      addItem(createLatexMenu(constants_.italic(), "emph"));
      addItem(createLatexMenu(constants_.typewriter(), "texttt"));
      addItem(createLatexMenu(constants_.quote(), "``", "''"));
      addSeparator();
      addItem(createLatexListMenu(constants_.bulletList(), "itemize", false));
      addItem(createLatexListMenu(constants_.numberedList(),"enumerate", false));
      addItem(createLatexListMenu(constants_.descriptionList(), "description", true));
      addSeparator();
      addItem(createLatexMenu(constants_.verbatim(),
                            "\\begin{verbatim}\n",
                            "\n\\end{verbatim}"));
      addItem(createLatexMenu(constants_.blockQuote(),
                            "\\begin{quote}\n",
                            "\n\\end{quote}"));
   }

   private MenuItem createLatexMenu(String text, String macro)
   {
      return createLatexMenu(text, macro, false);
   }
   
   private MenuItem createLatexMenu(String text, 
                                    String macro, 
                                    boolean isSectionMenu)
   {
      return createLatexMenu(text, "\\" + macro + "{", "}", isSectionMenu);    
   }
   
   private MenuItem createLatexMenu(String text, String prefix, String suffix)
   {
      return createLatexMenu(text, prefix, suffix, false);
   }
    
   private MenuItem createLatexMenu(String text, 
                                    String prefix, 
                                    String suffix,
                                    boolean isSectionMenu)
   {
      return new MenuItem(text, false, createLatexCommand(prefix, 
                                                          suffix, 
                                                          isSectionMenu)); 
   }
   
  
   
   private Command createLatexCommand(final String prefix, 
                                      final String suffix,
                                      final boolean isSectionCommand)
   {
      return new Command() {

         @Override
         public void execute()
         {
            String selection = editor_.getSelectionValue();
            
            // modify prefix based on prefs
            String insertPrefix = prefix;
            if (isSectionCommand &&  
                prefs_.insertNumberedLatexSections().getValue())
            {
               insertPrefix = insertPrefix.replace("*", "");
            }
            
            editor_.insertCode(insertPrefix + selection + suffix, false);
            editor_.focus();
            
            // if there was no previous selection then put the cursor
            // inside the braces
            if (selection.length() == 0)
            {
               Position pos = editor_.getCursorPosition();
               int row = pos.getRow();
               if (suffix.startsWith("\n"))
                  row = Math.max(0,  row - 1);
               int col = Math.max(0, pos.getColumn() - suffix.length());
           
               editor_.setCursorPosition(Position.create(row, col));
            }
         }};
   }
   
 
   private MenuItem createLatexListMenu(final String text, 
                                        final String type, 
                                        final boolean isDescription)
   {
      return new MenuItem(text, false, new Command(){
         @Override
         public void execute()
         {
            editor_.collapseSelection(true);
            Position pos = editor_.getCursorPosition();
            
            StringBuilder indent = new StringBuilder();
            if (prefs_.useSpacesForTab().getValue())
            {
               int spaces = prefs_.numSpacesForTab().getValue();
               for (int i=0; i<spaces; i++)
                  indent.append(' ');
            }
            else
            {
               indent.append('\t');
            }
           
            String item = indent.toString() + "\\item";
            String itemElement = item + (isDescription ? "[]" : " ");
            
            String code = "\\begin{" + type + "}\n";
            code += itemElement;
            code += "\n\\end{" + type + "}\n";
            
            
            editor_.insertCode(code, false);
            editor_.focus();
            
            editor_.setCursorPosition(Position.create(pos.getRow() + 1, 
                                                      item.length() + 1));
         }
         
      });
   }
  
   
   private final DocDisplay editor_;
   private final UserPrefs prefs_;
   private static final EditorsTextConstants constants_ = GWT.create(EditorsTextConstants.class);
}
