/*
 * CompletionCache.java
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.rstudio.core.client.JsVectorBoolean;
import org.rstudio.core.client.JsVectorInteger;
import org.rstudio.core.client.JsVectorString;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.container.SafeMap;
import org.rstudio.studio.client.common.codetools.Completions;
import org.rstudio.studio.client.common.codetools.RCompletionType;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.codesearch.CodeSearchOracle;

import com.google.gwt.core.client.JsArrayBoolean;
import com.google.gwt.core.client.JsArrayInteger;
import com.google.gwt.core.client.JsArrayString;

// This class should be used to cache completions during an active completion
// session. For example, if one retrieves completions for the token 'rn',
// and the user types 'rnorm', the completion cache should be able to satisfy
// the intermediate completion requests for 'rno', 'rnor', and 'rnorm'.
public class CompletionCache
{
   public CompletionCache()
   {
      cache_ = new SafeMap<>();
   }
   
   public boolean satisfyRequest(String line,
                                 ServerRequestCallback<Completions> requestCallback)
   {
      if (StringUtil.isNullOrEmpty(line))
         return false;
      
      for (int i = 0, n = line.length(); i < n; i++)
      {
         String substring = StringUtil.substring(line, 0, n - i);
         if (cache_.containsKey(substring))
         {
            Completions completions = narrow(line, substring, cache_.get(substring));
            requestCallback.onResponseReceived(completions);
            return true;
         }
      }
      
      return false;
   }
   
   public void store(String token, Completions completions)
   {
      cache_.put(token, completions);
   }
   
   public void flush()
   {
      cache_.clear();
   }
   
   private Completions narrow(String line,
                              String substring,
                              Completions original)
   {
      // no need to narrow when line + substring are equivalent
      if (line.equals(substring))
         return original;
      
      // Construct the new completion token by taking the original
      // completion token, and adding the delta between the new line and
      // the original completion line used.
      String token = original.getToken() + StringUtil.substring(line, substring.length());
      
      // Extract the vector elements of the completion string
      JsArrayString completions      = original.getCompletions();
      JsArrayString display          = original.getCompletionsDisplay();
      JsArrayString packages         = original.getPackages();
      JsArrayBoolean quote           = original.getQuote();
      JsArrayInteger type            = original.getType();
      JsArrayInteger context         = original.getContext();
      JsArrayBoolean suggestOnAccept = original.getSuggestOnAccept();
      JsArrayBoolean replaceToEnd    = original.getReplaceToEnd();
      JsArrayString meta             = original.getMeta();
      
      // Now, generate narrowed versions of the above
      final JsVectorString completionsNarrow     = JsVectorString.createVector().cast();
      final JsVectorString displayNarrow         = JsVectorString.createVector().cast();
      final JsVectorString packagesNarrow        = JsVectorString.createVector().cast();
      final JsVectorBoolean quoteNarrow          = JsVectorBoolean.createVector().cast();
      final JsVectorInteger typeNarrow           = JsVectorInteger.createVector().cast();
      final JsArrayBoolean suggestOnAcceptNarrow = JsVectorBoolean.createVector().cast();
      final JsArrayBoolean replaceToEndNarrow    = JsVectorBoolean.createVector().cast();
      final JsVectorString metaNarrow            = JsVectorString.createVector().cast();
      final JsVectorInteger contextNarrow        = JsVectorInteger.createVector().cast();
      
      for (int i = 0, n = completions.length(); i < n; i++)
      {
         boolean isSubsequence = StringUtil.isSubsequence(completions.get(i), token, true);
         if (isSubsequence)
         {
            completionsNarrow.push(completions.get(i));
            displayNarrow.push(display.get(i));
            packagesNarrow.push(packages.get(i));
            quoteNarrow.push(quote.get(i));
            typeNarrow.push(type.get(i));
            suggestOnAcceptNarrow.push(suggestOnAccept.get(i));
            replaceToEndNarrow.push(replaceToEnd.get(i));
            metaNarrow.push(meta.get(i));
            contextNarrow.push(context.get(i));
         }
      }
      
      // Finally, sort these based on score
      List<Integer> indices = new ArrayList<>();
      for (int i = 0, n = completionsNarrow.size(); i < n; i++)
         indices.add(i);
      
      // Sort our indices vector
      Collections.sort(indices, new Comparator<Integer>()
      {
         @Override
         public int compare(Integer lhs, Integer rhs)
         {
            int lhsContext = contextNarrow.get(lhs);
            int rhsContext = contextNarrow.get(rhs);

            int lhsType = typeNarrow.get(lhs);
            int rhsType = typeNarrow.get(rhs);

            int lhsTypeScore = RCompletionType.score(lhsType, lhsContext);
            int rhsTypeScore = RCompletionType.score(rhsType, rhsContext);
            if (lhsTypeScore < rhsTypeScore)
               return -1;
            else if (lhsTypeScore > rhsTypeScore)
               return 1;

            String lhsName = completionsNarrow.get(lhs);
            String rhsName = completionsNarrow.get(rhs);
            
            int lhsScore = CodeSearchOracle.scoreMatch(lhsName, token, false);
            int rhsScore = CodeSearchOracle.scoreMatch(rhsName, token, false);
            
            if (lhsScore == rhsScore)
               return lhsName.compareTo(rhsName);
            
            return lhsScore < rhsScore ? -1 : 1;
         }
      });
      
      // Finally, re-arrange our vectors.
      final JsVectorString completionsSorted = JsVectorString.createVector().cast();
      final JsVectorString displaySorted     = JsVectorString.createVector().cast();
      final JsVectorString packagesSorted    = JsVectorString.createVector().cast();
      final JsVectorBoolean quoteSorted      = JsVectorBoolean.createVector().cast();
      final JsVectorInteger typeSorted       = JsVectorInteger.createVector().cast();
      final JsVectorInteger contextSorted    = JsVectorInteger.createVector().cast();
      final JsVectorBoolean suggestOnAcceptSorted = JsVectorBoolean.createVector().cast();
      final JsVectorBoolean replaceToEndSorted = JsVectorBoolean.createVector().cast();
      final JsVectorString metaSorted        = JsVectorString.createVector().cast();
      
      for (int i = 0, n = indices.size(); i < n; i++)
      {
         int index = indices.get(i);
         completionsSorted.push(completionsNarrow.get(index));
         displaySorted.push(displayNarrow.get(index));
         packagesSorted.push(packagesNarrow.get(index));
         quoteSorted.push(quoteNarrow.get(index));
         typeSorted.push(typeNarrow.get(index));
         contextSorted.push(context.get(index));
         suggestOnAcceptSorted.push(suggestOnAcceptNarrow.get(index));
         replaceToEndSorted.push(replaceToEndNarrow.get(index));
         metaSorted.push(metaNarrow.get(index));
      }
      
      // And return the completion result
      return Completions.createCompletions(
            token,
            completionsSorted.cast(),
            displaySorted.cast(),
            packagesSorted.cast(),
            quoteSorted.cast(),
            typeSorted.cast(),
            suggestOnAcceptSorted.cast(),
            replaceToEndSorted.cast(),
            metaSorted.cast(),
            original.getGuessedFunctionName(),
            original.getExcludeOtherCompletions(),
            original.getExcludeOtherArgumentCompletions(),
            original.getOverrideInsertParens(),
            original.isCacheable(),
            original.getHelpHandler(),
            original.getLanguage(), 
            contextSorted.cast()
            );
   }
   
   private final SafeMap<String, Completions> cache_;
}
