/*
 * CompletionRequester.java
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import org.rstudio.core.client.SafeHtmlUtil;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.js.JsUtil;
import org.rstudio.core.client.regex.Pattern;
import org.rstudio.core.client.resources.ImageResource2x;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.common.codetools.CodeToolsServerOperations;
import org.rstudio.studio.client.common.codetools.Completions;
import org.rstudio.studio.client.common.codetools.RCompletionType;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.studio.client.common.icons.code.CodeIcons;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.codesearch.CodeSearchOracle;
import org.rstudio.studio.client.workbench.prefs.model.UserPrefs;
import org.rstudio.studio.client.workbench.snippets.SnippetHelper;
import org.rstudio.studio.client.workbench.views.console.shell.ConsoleLanguageTracker;
import org.rstudio.studio.client.workbench.views.console.shell.assist.RCompletionManager.AutocompletionContext;
import org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor;
import org.rstudio.studio.client.workbench.views.source.editors.text.CompletionContext;
import org.rstudio.studio.client.workbench.views.source.editors.text.DocDisplay;
import org.rstudio.studio.client.workbench.views.source.editors.text.RFunction;
import org.rstudio.studio.client.workbench.views.source.editors.text.ScopeFunction;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.CodeModel;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Position;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.RInfixData;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.RScopeObject;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.TokenCursor;
import org.rstudio.studio.client.workbench.views.source.model.RnwChunkOptions;
import org.rstudio.studio.client.workbench.views.source.model.RnwChunkOptions.RnwOptionCompletionResult;
import org.rstudio.studio.client.workbench.views.source.model.RnwCompletionContext;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayBoolean;
import com.google.gwt.core.client.JsArrayInteger;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.Command;
import com.google.inject.Inject;

public class CompletionRequester
{
   private final CompletionContext context_;
   private final RnwCompletionContext rnwContext_;
   private final DocDisplay docDisplay_;
   private final SnippetHelper snippets_;
   
   private String cachedLinePrefix_;
   private HashMap<String, CompletionResult> cachedCompletions_ = new HashMap<>();

   // Injected ----
   private CodeToolsServerOperations server_;
   private UserPrefs uiPrefs_;
   
   public CompletionRequester(CompletionContext context,
                              RnwCompletionContext rnwContext,
                              DocDisplay docDisplay,
                              SnippetHelper snippets)
   {
      context_ = context;
      rnwContext_ = rnwContext;
      docDisplay_ = docDisplay;
      snippets_ = snippets;
      RStudioGinjector.INSTANCE.injectMembers(this);
   }

   @Inject
   void initialize(CodeToolsServerOperations server, UserPrefs uiPrefs)
   {
      server_ = server;
      uiPrefs_ = uiPrefs;
   }

   private boolean usingCache(
         String token,
         final ServerRequestCallback<CompletionResult> callback)
   {
      return usingCache(token, false, callback);
   }

   private boolean usingCache(
         String token,
         boolean isHelpCompletion,
         final ServerRequestCallback<CompletionResult> callback)
   {
      if (isHelpCompletion)
         token = StringUtil.substring(token, token.lastIndexOf(':') + 1);

      if (cachedLinePrefix_ == null)
         return false;

      CompletionResult cachedResult = cachedCompletions_.get("");
      if (cachedResult == null)
         return false;

      if (token.toLowerCase().startsWith(cachedLinePrefix_.toLowerCase()))
      {
         String diff = StringUtil.substring(token, cachedLinePrefix_.length());

         // if we already have a cached result for this diff, use it
         CompletionResult cached = cachedCompletions_.get(diff);
         if (cached != null)
         {
            callback.onResponseReceived(cached);
            return true;
         }

         // otherwise, produce a new completion list
         if (diff.length() > 0 && !diff.endsWith("::"))
         {
            callback.onResponseReceived(narrow(cachedResult.token + diff, diff, cachedResult));
            return true;
         }
      }

      return false;
   }

   private String basename(String absolutePath)
   {
      return StringUtil.substring(absolutePath, absolutePath.lastIndexOf('/') + 1);
   }

   private boolean filterStartsWithDot(String item,
                                       String token)
   {
      return !(!token.startsWith(".") && item.startsWith("."));
   }

   private static final native String fuzzy(String string) /*-{
      return string.replace(/(?!^)[._]/g, "");
   }-*/;

   private CompletionResult narrow(final String token,
                                   final String diff,
                                   CompletionResult cachedResult)
   {
      ArrayList<QualifiedName> newCompletions = new ArrayList<>();
      newCompletions.ensureCapacity(cachedResult.completions.size());

      // For completions that are files or directories, we need to post-process
      // the token and the qualified name to strip out just the basename (filename).
      // Note that we normalize the paths such that files will have no trailing slash,
      // while directories will have one trailing slash (but we defend against multiple
      // trailing slashes)

      // Transform the token once beforehand for completions.
      final String tokenSub   = StringUtil.substring(token, token.lastIndexOf('/') + 1);
      final String tokenFuzzy = fuzzy(tokenSub);

      for (QualifiedName qname : cachedResult.completions)
      {
         // File types are narrowed only by the file name
         if (RCompletionType.isFileType(qname.type))
         {
            if (StringUtil.isSubsequence(basename(qname.name), tokenFuzzy, true))
               newCompletions.add(qname);
         }
         else
         {
            String value;
            if (qname.type == RCompletionType.ROXYGEN)
            {
               value = qname.name.replaceAll("\\s.*", "");
            }
            else
            {
               String displayMeta = StringUtil.truncate(qname.meta, META_DISPLAY_LIMIT_CHARACTERS, " <...>");
               value = qname.name + displayMeta;
            }
            
            if (StringUtil.isSubsequence(value, tokenFuzzy, true) &&
                filterStartsWithDot(value, token))
               newCompletions.add(qname);
         }
      }

      newCompletions.sort(new Comparator<QualifiedName>()
      {
         @Override
         public int compare(QualifiedName lhs, QualifiedName rhs)
         {
            // compare completion type first
            int lhsTypeScore = RCompletionType.score(lhs.type, lhs.context);
            int rhsTypeScore = RCompletionType.score(rhs.type, rhs.context);
            if (lhsTypeScore < rhsTypeScore)
               return -1;
            else if (lhsTypeScore > rhsTypeScore)
               return 1;

            // when type score is equal: calculate score with scoreMatch()
            int lhsScore = RCompletionType.isFileType(lhs.type)
                  ? CodeSearchOracle.scoreMatch(basename(lhs.name), tokenSub, true)
                  : CodeSearchOracle.scoreMatch(lhs.name, token, false);
            
            int rhsScore = RCompletionType.isFileType(rhs.type)
               ? CodeSearchOracle.scoreMatch(basename(rhs.name), tokenSub, true)
               : CodeSearchOracle.scoreMatch(rhs.name, token, false);

            if (lhsScore == rhsScore)
               return lhs.compareTo(rhs);

            return lhsScore < rhsScore ? -1 : 1;
         }
      });

      CompletionResult result = new CompletionResult(
            token,
            newCompletions,
            cachedResult.guessedFunctionName,
            cachedResult.dontInsertParens);

      cachedCompletions_.put(diff, result);
      return result;
   }

   private void fillCompletionResult(
         Completions response,
         boolean implicit,
         ServerRequestCallback<CompletionResult> callback)
   {
      JsArrayString comp = response.getCompletions();
      JsArrayString display = response.getCompletionsDisplay();
      JsArrayString pkgs = response.getPackages();
      JsArrayBoolean quote = response.getQuote();
      JsArrayInteger type = response.getType();
      JsArrayBoolean suggestOnAccept = response.getSuggestOnAccept();
      JsArrayBoolean replaceToEnd = response.getReplaceToEnd();
      JsArrayString meta = response.getMeta();
      ArrayList<QualifiedName> newComp = new ArrayList<>();
      for (int i = 0; i < comp.length(); i++)
      {
         newComp.add(new QualifiedName(
            comp.get(i), 
            display.get(i),
            pkgs.get(i), 
            quote.get(i), 
            type.get(i), 
            suggestOnAccept.get(i),  
            replaceToEnd.get(i),
            meta.get(i), 
            response.getHelpHandler(), 
            response.getLanguage(),
            response.getContext().get(i)
            )
         );
      }

      CompletionResult result = new CompletionResult(
            response.getToken(),
            newComp,
            response.getGuessedFunctionName(),
            response.getOverrideInsertParens());

      if (response.isCacheable())
      {
         cachedCompletions_.put("", result);
      }

      if (!implicit || result.completions.size() != 0)
         callback.onResponseReceived(result);

   }

   private static final Pattern RE_EXTRACTION = Pattern.create("[$@:]", "");
   private boolean isTopLevelCompletionRequest()
   {
      String line = docDisplay_.getCurrentLineUpToCursor();
      return !RE_EXTRACTION.test(line);
   }

   public void getCompletions(final AutocompletionContext context,
                              final RInfixData infixData,
                              final String filePath,
                              final String documentId,
                              final String line,
                              final boolean isConsole,
                              final boolean implicit,
                              final ServerRequestCallback<CompletionResult> callback)
   {
      String token = context.getToken();
      boolean isHelp =
            context.getContextData().length() > 0 &&
            context.getContextData().get(0).getType() == AutocompletionContext.TYPE_HELP;

      if (usingCache(token, isHelp, callback))
         return;

      doGetCompletions(
            context,
            infixData,
            filePath,
            documentId,
            line,
            isConsole,
            new ServerRequestCallback<Completions>()
      {
         @Override
         public void onError(ServerError error)
         {
            callback.onError(error);
         }
         
         private boolean isPriorityCompletion(int type)
         {
            return
                  type == RCompletionType.ARGUMENT ||
                  type == RCompletionType.COLUMN;
         }

         @Override
         public void onResponseReceived(Completions response)
         {
            cachedLinePrefix_ = token;
            String token = response.getToken();

            JsArrayString comp = response.getCompletions();
            JsArrayString display = response.getCompletionsDisplay();
            JsArrayString pkgs = response.getPackages();
            JsArrayBoolean quote = response.getQuote();
            JsArrayInteger type = response.getType();
            JsArrayBoolean suggestOnAccept = response.getSuggestOnAccept();
            JsArrayBoolean replaceToEnd = response.getReplaceToEnd();
            JsArrayString meta = response.getMeta();
            ArrayList<QualifiedName> newComp = new ArrayList<>();

            // Add higher-priority completions first
            for (int i = 0; i < comp.length(); i++)
            {
               if (isPriorityCompletion(type.get(i)))
               {
                  newComp.add(new QualifiedName(
                     comp.get(i), 
                     display.get(i),
                     pkgs.get(i), 
                     quote.get(i), 
                     type.get(i), 
                     suggestOnAccept.get(i), 
                     replaceToEnd.get(i),
                     meta.get(i), 
                     response.getHelpHandler(), 
                     response.getLanguage(), 
                     response.getContext().get(i)
                  ));
               }
            }
            
            // Try getting our own function argument completions
            if (!response.getExcludeOtherArgumentCompletions() && !response.getExcludeOtherCompletions())
            {
               addFunctionArgumentCompletions(token, newComp);
            }

            if (!response.getExcludeOtherCompletions())
            {
               addScopedArgumentCompletions(token, newComp);
            }

            // Get variable completions from the current scope
            if (!response.getExcludeOtherCompletions())
            {
               addScopedCompletions(token, newComp, "variable");
               addScopedCompletions(token, newComp, "function");
            }

            // Add lower-priority completions next
            for (int i = 0; i < comp.length(); i++)
            {
               if (!isPriorityCompletion(type.get(i)))
               {
                  newComp.add(new QualifiedName(
                     comp.get(i), 
                     display.get(i),
                     pkgs.get(i), 
                     quote.get(i), 
                     type.get(i), 
                     suggestOnAccept.get(i),
                     replaceToEnd.get(i),
                     meta.get(i), 
                     response.getHelpHandler(), 
                     response.getLanguage(), 
                     response.getContext().get(i)
                  ));
               }
            }
         
            // Get snippet completions. Bail if this isn't a top-level completion
            if (isTopLevelCompletionRequest())
            {
               // disable snippets if Python REPL is active for now
               boolean noSnippets =
                     isConsole &&
                     !StringUtil.equals(response.getLanguage(), ConsoleLanguageTracker.LANGUAGE_R);

               if (!noSnippets)
               {
                  // de-emphasize snippet completions in function calls
                  String line = docDisplay_.getCurrentLineUpToCursor();
                  Pattern pattern = Pattern.create("[\\(\\[]", "");
                  boolean back = pattern.test(line);
                     
                  addSnippetCompletions(token, back, newComp);
               }
            }

            // Remove duplicates
            newComp = resolveDuplicates(newComp);

            CompletionResult result = new CompletionResult(
                  response.getToken(),
                  newComp,
                  response.getGuessedFunctionName(),
                  response.getOverrideInsertParens());

            if (response.isCacheable())
            {
               cachedCompletions_.put("", result);
            }

            callback.onResponseReceived(result);
         }
      });
   }

   private ArrayList<QualifiedName>
   resolveDuplicates(ArrayList<QualifiedName> completions)
   {
      ArrayList<QualifiedName> result = new ArrayList<>(completions);

      // sort the results by name and type for efficient processing
      completions.sort(new Comparator<QualifiedName>()
      {
         @Override
         public int compare(QualifiedName o1, QualifiedName o2)
         {
            int name = o1.name.compareTo(o2.name);
            if (name != 0)
               return name;
            return o1.type - o2.type;
         }
      });

      // walk backwards through the list and remove elements which have the
      // same name and type
      for (int i = completions.size() - 1; i > 0; i--)
      {
         QualifiedName o1 = completions.get(i);
         QualifiedName o2 = completions.get(i - 1);

         // remove qualified names which have the same name and type (allow
         // shadowing of contextual results to reduce confusion)
         if (o1.name == o2.name &&
             (o1.type == o2.type || o1.type == RCompletionType.CONTEXT))
            result.remove(o1);
      }

      return result;
   }

   private void addScopedArgumentCompletions(
         String token,
         ArrayList<QualifiedName> completions)
   {
      AceEditor editor = (AceEditor) docDisplay_;

      // NOTE: this will be null in the console, so protect against that
      if (editor != null)
      {
         Position cursorPosition =
               editor.getSession().getSelection().getCursor();
         CodeModel codeModel = editor.getSession().getMode().getRCodeModel();
         JsArray<RFunction> scopedFunctions =
               codeModel.getFunctionsInScope(cursorPosition);

         if (scopedFunctions.length() == 0)
            return;

         String tokenLower = token.toLowerCase();

         for (int i = 0; i < scopedFunctions.length(); i++)
         {
            RFunction scopedFunction = scopedFunctions.get(i);
            String functionName = scopedFunction.getFunctionName();
            
            JsArrayString argNames = scopedFunction.getFunctionArgs();
            for (int j = 0; j < argNames.length(); j++)
            {
               String argName = argNames.get(j);
               if (argName.toLowerCase().startsWith(tokenLower))
               {
                  if (functionName == null || functionName == "")
                  {
                     completions.add(new QualifiedName(
                           argName,
                           "<anonymous function>",
                           false,
                           RCompletionType.CONTEXT
                     ));
                  }
                  else
                  {
                     completions.add(new QualifiedName(
                           argName,
                           functionName,
                           false,
                           RCompletionType.CONTEXT
                     ));
                  }
               }
            }
         }
      }
   }

   private void addScopedCompletions(
         String token,
         ArrayList<QualifiedName> completions,
         String type)
   {
      AceEditor editor = (AceEditor) docDisplay_;

      // NOTE: this will be null in the console, so protect against that
      if (editor != null)
      {
         Position cursorPosition =
               editor.getSession().getSelection().getCursor();
         CodeModel codeModel = editor.getSession().getMode().getRCodeModel();

         JsArray<RScopeObject> scopeVariables =
               codeModel.getVariablesInScope(cursorPosition);

         String tokenLower = token.toLowerCase();
         for (int i = 0; i < scopeVariables.length(); i++)
         {
            RScopeObject variable = scopeVariables.get(i);
            if (variable.getType() == type &&
                variable.getToken().toLowerCase().startsWith(tokenLower))
               completions.add(new QualifiedName(
                     variable.getToken(),
                     variable.getType(),
                     false,
                     RCompletionType.CONTEXT
               ));
         }
      }
   }

   private void addFunctionArgumentCompletions(
         String token,
         ArrayList<QualifiedName> completions)
   {
      AceEditor editor = (AceEditor) docDisplay_;

      if (editor != null)
      {
         Position cursorPosition =
               editor.getSession().getSelection().getCursor();
         CodeModel codeModel = editor.getSession().getMode().getRCodeModel();

         // Try to see if we can find a function name
         TokenCursor cursor = codeModel.getTokenCursor();

         // NOTE: This can fail if the document is empty
         if (!cursor.moveToPosition(cursorPosition))
            return;

         String tokenLower = token.toLowerCase();
         if (cursor.currentValue() == "(" || cursor.findOpeningBracket("(", false))
         {
            if (cursor.moveToPreviousToken())
            {
               // Check to see if this really is the name of a function
               JsArray<ScopeFunction> functionsInScope =
                     codeModel.getAllFunctionScopes();

               String tokenName = cursor.currentValue();
               for (int i = 0; i < functionsInScope.length(); i++)
               {
                  ScopeFunction rFunction = functionsInScope.get(i);
                  String fnName = rFunction.getFunctionName();
                  if (tokenName == fnName)
                  {
                     JsArrayString args = rFunction.getFunctionArgs();
                     for (int j = 0; j < args.length(); j++)
                     {
                        String arg = args.get(j);
                        if (arg.toLowerCase().startsWith(tokenLower))
                           completions.add(new QualifiedName(
                                 args.get(j) + " = ",
                                 fnName,
                                 false,
                                 RCompletionType.CONTEXT
                           ));
                     }
                  }
               }
            }
         }
      }
   }

   private void addSnippetCompletions(
         String token,
         boolean back,
         ArrayList<QualifiedName> completions)
   {
      if (StringUtil.isNullOrEmpty(token))
         return;

      if (uiPrefs_.enableSnippets().getValue())
      {
         ArrayList<String> snippets = snippets_.getAvailableSnippets();
         String tokenLower = token.toLowerCase();
         for (String snippet : snippets)
         {
            if (snippet.toLowerCase().startsWith(tokenLower))
            {
               if (back)
               {
                  completions.add(QualifiedName.createSnippet(snippet));
               }
               else
               {
                  completions.add(0, QualifiedName.createSnippet(snippet));
               }
            }
         }
      }
   }

   private void doGetCompletions(AutocompletionContext context,
                                 RInfixData infixData,
                                 final String filePath,
                                 final String documentId,
                                 final String line,
                                 final boolean isConsole,
                                 final ServerRequestCallback<Completions> requestCallback)
   {
      if (rnwContext_ != null)
      {
         String token = context.getToken();
         int offset = rnwContext_.getRnwOptionsStart(token, token.length());
         if (offset >= 0)
         {
            doGetSweaveCompletions(token, offset, token.length(), requestCallback);
            return;
         }
      }
      
      Command command = () ->
      {
         server_.getCompletions(
               context.getToken(),
               context.getContextData(),
               context.getFunctionCallString(),
               context.getStatementBounds(),
               infixData.getDataName(),
               infixData.getAdditionalArgs(),
               infixData.getExcludeArgs(),
               infixData.getExcludeArgsFromObject(),
               filePath,
               documentId,
               line,
               isConsole,
               requestCallback);
      };
         
      if (context_ != null && context.getNeedsDocSync())
      {
         context_.withSavedDocument(command);
      }
      else
      {
         command.execute();
      }
   }

   private void doGetSweaveCompletions(
         final String line,
         final int optionsStartOffset,
         final int cursorPos,
         final ServerRequestCallback<Completions> requestCallback)
   {
      rnwContext_.getChunkOptions(new ServerRequestCallback<RnwChunkOptions>()
      {
         @Override
         public void onResponseReceived(RnwChunkOptions options)
         {
            RnwOptionCompletionResult result = options.getCompletions(
                  line,
                  optionsStartOffset,
                  cursorPos,
                  rnwContext_ == null ? null : rnwContext_.getActiveRnwWeave());

            String[] pkgNames = new String[result.completions.length()];
            Arrays.fill(pkgNames, "<chunk-option>");

            Completions response = Completions.createCompletions(
                  result.token,
                  result.completions,
                  result.completions,
                  JsUtil.toJsArrayString(pkgNames),
                  JsUtil.toJsArrayBoolean(new ArrayList<>(result.completions.length())),
                  JsUtil.toJsArrayInteger(new ArrayList<>(result.completions.length())),
                  JsUtil.toJsArrayBoolean(new ArrayList<>(result.completions.length())),
                  JsUtil.toJsArrayBoolean(new ArrayList<>(result.completions.length())),
                  JsUtil.toJsArrayString(new ArrayList<>(result.completions.length())),
                  "",
                  true,
                  true,
                  false,
                  true,
                  null,
                  null, 
                  JsUtil.toJsArrayInteger(new ArrayList<>(result.completions.length())));

            // Unlike other completion types, Sweave completions are not
            // guaranteed to narrow the candidate list (in particular
            // true/false).
            response.setCacheable(false);
            if (result.completions.length() > 0 &&
                result.completions.get(0).endsWith("="))
            {
               ArrayList<Boolean> suggestOnAccept = new ArrayList<Boolean>(
                  Collections.nCopies(result.completions.length(), true)
               );
               response.setSuggestOnAccept(JsUtil.toJsArrayBoolean(suggestOnAccept));
            }

            requestCallback.onResponseReceived(response);
         }

         @Override
         public void onError(ServerError error)
         {
            requestCallback.onError(error);
         }
      });
   }

   public void flushCache()
   {
      cachedLinePrefix_ = null;
      cachedCompletions_.clear();
   }

   public static class CompletionResult
   {
      public CompletionResult(String token,
                              ArrayList<QualifiedName> completions,
                              String guessedFunctionName,
                              boolean dontInsertParens)
      {
         this.token = token;
         this.completions = completions;
         this.guessedFunctionName = guessedFunctionName;
         this.dontInsertParens = dontInsertParens;
      }

      public final String token;
      public final ArrayList<QualifiedName> completions;
      public final String guessedFunctionName;
      public final boolean dontInsertParens;

      // this should probably be set in the R side as a generic
      // canAutoAccept (default TRUE)
      public boolean canAutoAccept() {
         return !StringUtil.equals(guessedFunctionName, "[.data.table");
      }
   }

   public static class QualifiedName implements Comparable<QualifiedName>
   {
      public QualifiedName(String name,
                           String source,
                           boolean shouldQuote,
                           int type)
      {
         this(name, source, shouldQuote, type, false);
      }
      
      public QualifiedName(String name,
                           String source,
                           boolean shouldQuote,
                           int type,
                           boolean suggestOnAccept)
      {
         this(name, name, source, shouldQuote, type, suggestOnAccept, false, "", null, "R", RCompletionManager.AutocompletionContext.TYPE_UNKNOWN);
      }

      public QualifiedName(String name,
                           String source)
      {
         this(name, name, source, false, RCompletionType.UNKNOWN, false, false, "", null, "R", RCompletionManager.AutocompletionContext.TYPE_UNKNOWN);
      }
      
      public QualifiedName(String name,
                           String display,
                           String source,
                           boolean shouldQuote,
                           int type,
                           boolean suggestOnAccept,
                           boolean replaceToEnd,
                           String meta,
                           String helpHandler,
                           String language, 
                           int context)
      {
         this.name = name;
         this.display = display;
         this.source = source;
         this.shouldQuote = shouldQuote;
         this.type = type;
         this.suggestOnAccept = suggestOnAccept;
         this.replaceToEnd = replaceToEnd;
         this.meta = meta;
         this.helpHandler = helpHandler;
         this.language = language;
         this.context = context;
      }

      public static QualifiedName createSnippet(String name)
      {
         return new QualifiedName(
               name,
               name,
               "snippet",
               false,
               RCompletionType.SNIPPET,
               false,
               false,
               "",
               null,
               "R", 
               RCompletionManager.AutocompletionContext.TYPE_UNKNOWN);
      }
      
      public QualifiedName withSuggestOnAccept()
      {
         return new QualifiedName(
            this.name,
            this.display,
            this.source,
            this.shouldQuote,
            this.type,
            true,
            this.replaceToEnd,
            this.meta,
            this.helpHandler,
            this.language, 
            this.context
         );
      }
      
      @Override
      public String toString()
      {
         SafeHtmlBuilder sb = new SafeHtmlBuilder();

         // Get an icon for the completion
         // We use separate styles for file icons, so we can nudge them
         // a bit differently
         ImageResource icon = getIcon();
         if (icon != null)
         {
            String style = RES.styles().completionIcon();
            if (RCompletionType.isFileType(type))
               style = RES.styles().fileIcon();

            SafeHtmlUtil.appendImage(
                  sb,
                  style,
                  getIcon());
         }
        
         // Get the display name. Note that for file completions this requires
         // some munging of the 'name' and 'package' fields.
         addDisplayName(sb);

         return sb.toSafeHtml().asString();
      }

      private void addDisplayName(SafeHtmlBuilder sb)
      {
         // Handle files specially
         if (RCompletionType.isFileType(type))
            doAddDisplayNameFile(sb);
         else
            doAddDisplayNameGeneric(sb);
      }

      private void doAddDisplayNameFile(SafeHtmlBuilder sb)
      {
         ArrayList<Integer> slashIndices =
               StringUtil.indicesOf(name, '/');

         if (slashIndices.size() < 1)
         {
            SafeHtmlUtil.appendSpan(
                  sb,
                  RES.styles().completion(),
                  display);
         }
         else
         {
            int lastSlashIndex = slashIndices.get(
                  slashIndices.size() - 1);

            int firstSlashIndex = 0;
            if (slashIndices.size() > 2)
               firstSlashIndex = slashIndices.get(
                     slashIndices.size() - 3);

            String endName = StringUtil.substring(display, lastSlashIndex + 1);
            String startName = "";
            if (slashIndices.size() > 2)
               startName += "...";
            startName += StringUtil.substring(display, firstSlashIndex, lastSlashIndex);

            SafeHtmlUtil.appendSpan(
                  sb,
                  RES.styles().completion(),
                  endName);

            SafeHtmlUtil.appendSpan(
                  sb,
                  RES.styles().packageName(),
                  startName);
         }

      }

      private void doAddDisplayNameGeneric(SafeHtmlBuilder sb)
      {
         String style = RES.styles().completion();
         if (type == RCompletionType.COLUMN) 
            style = style + " " + RES.styles().column();

         // Get the name for the completion
         String displayName = (type == RCompletionType.ROXYGEN)
               ? display.split("\n")[0].replaceFirst(" .*", "")
               : display;
         
         SafeHtmlUtil.appendSpan(sb, style, displayName);
         
         // Display the source for functions and snippets (unless there
         // is a custom helpHandler provided, indicating that the "source"
         // isn't a package but rather some custom DollarNames scope)
         if ((RCompletionType.isFunctionType(type) ||
             type == RCompletionType.SNIPPET ||
             type == RCompletionType.DATASET ||
             type == RCompletionType.DATAFRAME
             ) &&
             helpHandler == null)
         {
            SafeHtmlUtil.appendSpan(
                  sb,
                  RES.styles().packageName(),
                  "{" + source.replaceAll("package:", "") + "}");
         }

         if (type == RCompletionType.COLUMN) 
         {
            SafeHtmlUtil.appendSpan(
                  sb,
                  RES.styles().dataframe(),
                  "[" + source + "]");
         }

         if (type == RCompletionType.ARGUMENT || type == RCompletionType.SECUNDARY_ARGUMENT) 
         {
            SafeHtmlUtil.appendSpan(
                  sb,
                  RES.styles().argument(),
                  source + "()");
         }
         
         // Append metadata for display if available
         boolean useMeta =
               type != RCompletionType.ROXYGEN &&
               type != RCompletionType.YAML_KEY &&
               type != RCompletionType.YAML_VALUE &&
               !StringUtil.isNullOrEmpty(meta);
         
         if (useMeta)
         {
            String displayMeta = StringUtil.truncate(meta, META_DISPLAY_LIMIT_CHARACTERS, " <...>");
            SafeHtml openTag = SafeHtmlUtil.createOpenTag("span",
                  "class", RES.styles().meta(),
                  "title", StringUtil.truncate(meta, 256, " <...>"));
            sb.append(openTag);
            sb.appendEscaped(displayMeta);
            sb.appendHtmlConstant("</span>");
         }
         
      }

      private ImageResource getIcon()
      {
         if (RCompletionType.isFunctionType(type))
            return new ImageResource2x(ICONS.function2x());
         
         switch(type)
         {
         case RCompletionType.UNKNOWN:
         case RCompletionType.YAML_VALUE:
            return new ImageResource2x(ICONS.variable2x());
         case RCompletionType.VECTOR:
            return new ImageResource2x(ICONS.variable2x());
         case RCompletionType.ARGUMENT:
         case RCompletionType.SECUNDARY_ARGUMENT:
            return new ImageResource2x(ICONS.variable2x());
         case RCompletionType.ARRAY:
         case RCompletionType.DATAFRAME:
            return new ImageResource2x(ICONS.dataFrame2x());
         case RCompletionType.LIST:
            return new ImageResource2x(ICONS.clazz2x());
         case RCompletionType.ENVIRONMENT:
            return new ImageResource2x(ICONS.environment2x());
         case RCompletionType.S4_CLASS:
         case RCompletionType.S4_OBJECT:
         case RCompletionType.R5_CLASS:
         case RCompletionType.R5_OBJECT:
         case RCompletionType.R6_OBJECT:
            return new ImageResource2x(ICONS.clazz2x());
         case RCompletionType.FILE:
            return getIconForFilename(name);
         case RCompletionType.DIRECTORY:
            return new ImageResource2x(ICONS.folder2x());
         case RCompletionType.CHUNK:
         case RCompletionType.ROXYGEN:
            return new ImageResource2x(ICONS.roxygen2x());
         case RCompletionType.HELP:
            return new ImageResource2x(ICONS.help2x());
         case RCompletionType.STRING:
            return new ImageResource2x(ICONS.variable2x());
         case RCompletionType.PACKAGE:
            return new ImageResource2x(ICONS.rPackage2x());
         case RCompletionType.KEYWORD:
            return new ImageResource2x(ICONS.keyword2x());
         case RCompletionType.CONTEXT:
         case RCompletionType.YAML_KEY:
            return new ImageResource2x(ICONS.context2x());
         case RCompletionType.SNIPPET:
            return new ImageResource2x(ICONS.snippet2x());
         case RCompletionType.COLUMN:
            return new ImageResource2x(ICONS.column2x());
         case RCompletionType.DATATABLE_SPECIAL_SYMBOL:
            return new ImageResource2x(ICONS.datatableSpecialSymbol2x());
         default:
            return new ImageResource2x(ICONS.variable2x());
         }
      }

      private ImageResource getIconForFilename(String name)
      {
         return FILE_TYPE_REGISTRY.getIconForFilename(name).getImageResource();
      }

      public static QualifiedName parseFromText(String val)
      {
         String name, pkgName = "";
         int idx = val.indexOf('{');
         if (idx < 0)
         {
            name = val;
         }
         else
         {
            name = StringUtil.substring(val, 0, idx).trim();
            pkgName = StringUtil.substring(val, idx + 1, val.length() - 1);
         }

         return new QualifiedName(name, pkgName);
      }

      public int compareTo(QualifiedName o)
      {
         if (name.endsWith("=") ^ o.name.endsWith("="))
            return name.endsWith("=") ? -1 : 1;

         int result = String.CASE_INSENSITIVE_ORDER.compare(name, o.name);
         if (result != 0)
            return result;

         String pkg = source == null ? "" : source;
         String opkg = o.source == null ? "" : o.source;
         return pkg.compareTo(opkg);
      }

      @Override
      public boolean equals(Object object)
      {
         if (!(object instanceof QualifiedName))
            return false;

         QualifiedName other = (QualifiedName) object;
         return name.equals(other.name) &&
                type == other.type;
      }

      @Override
      public int hashCode()
      {
         int hash = 17;
         hash = 31 * hash + name.hashCode();
         hash = 31 * hash + type;
         return hash;
      }

      public final String name;
      public final String display;
      public final String source;
      public final boolean shouldQuote;
      public final int type;
      public final int context;
      public final boolean suggestOnAccept;
      public final boolean replaceToEnd;
      public final String meta;
      public final String helpHandler;
      public final String language;

      private static final FileTypeRegistry FILE_TYPE_REGISTRY =
            RStudioGinjector.INSTANCE.getFileTypeRegistry();
   }

   private static final int META_DISPLAY_LIMIT_CHARACTERS = 32;
   
   private static final CompletionRequesterResources RES =
         CompletionRequesterResources.INSTANCE;

   private static final CodeIcons ICONS = CodeIcons.INSTANCE;

   static {
      RES.styles().ensureInjected();
   }

}
