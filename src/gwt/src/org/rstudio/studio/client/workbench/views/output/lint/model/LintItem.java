/*
 * LintItem.java
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
package org.rstudio.studio.client.workbench.views.output.lint.model;

import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Position;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Range;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

public class LintItem extends JavaScriptObject
{
   protected LintItem() {}
   
   public static final native LintItem create(int startRow,
                                              int startColumn,
                                              int endRow,
                                              int endColumn,
                                              String text,
                                              String type) /*-{
      
      return {
         "start.row": startRow,
         "start.column": startColumn,
         "end.row": endRow,
         "end.column": endColumn,
         "text": text,
         "type": type
      };
                                
   }-*/;
   
   public final native int getStartRow() /*-{
      return this["start.row"];
   }-*/;

   public final native void setStartRow(int row) /*-{
      this["start.row"] = row;
   }-*/;

   public final native int getStartColumn() /*-{
      return this["start.column"];
   }-*/;
   
   public final native int getEndRow() /*-{
      return this["end.row"];
   }-*/;

   public final native void setEndRow(int row) /*-{
      this["end.row"] = row;
   }-*/;

   public final native int getEndColumn() /*-{
      return this["end.column"];
   }-*/;
   
   public final native String getText() /*-{
      return this["text"];
   }-*/;

   public final native void setText(String text) /*-{
      this["text"] = text;
   }-*/;
   
   public final native String getHtml() /*-{
      return this["html"];
   }-*/;

   public final native void setHtml(String html) /*-{
      this["html"] = html;
   }-*/;
   
   public final native String getType() /*-{
      return this["type"];
   }-*/;
   
   public final Range asRange()
   {
      return Range.fromPoints(
            Position.create(getStartRow(), getStartColumn()),
            Position.create(getEndRow(), getEndColumn()));
   }
   
   public final AceAnnotation asAceAnnotation()
   {
      return AceAnnotation.create(
            getStartRow(),
            getStartColumn(),
            getHtml(),
            getText(),
            getType());
   }
   
   public static final native JsArray<AceAnnotation> asAceAnnotations(
         JsArray<LintItem> items)
   /*-{
      
      var aceAnnotations = [];
      
      for (var key in items)
      {
         var item = items[key];
         var type = item["type"];
         if (type !== "error" && type !== "warning") {
            type = "info";
         }

         var annotation = {
            row: item["start.row"],
            column: item["start.column"],
            type: type
         };
         
         var html = item["html"]
         if (html)
            annotation.html = html;
         else 
            annotation.text = item["text"];
         
         aceAnnotations.push(annotation);
      }
      
      return aceAnnotations;
         
   }-*/;
   
}
