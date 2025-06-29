/*
 * markdown_highlight_rules.js
 *
 * Copyright (C) 2022 by Posit Software, PBC
 *
 * The Initial Developer of the Original Code is
 * Ajax.org B.V.
 * Portions created by the Initial Developer are Copyright (C) 2010
 * the Initial Developer. All Rights Reserved.
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

/* ***** BEGIN LICENSE BLOCK *****
 * Distributed under the BSD license:
 *
 * Copyright (c) 2010, Ajax.org B.V.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Ajax.org B.V. nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL AJAX.ORG B.V. BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ***** END LICENSE BLOCK ***** */

define("mode/markdown_highlight_rules", ["require", "exports", "module"], function (require, exports, module) {

var oop = require("ace/lib/oop");
var lang = require("ace/lib/lang");
var TextHighlightRules = require("ace/mode/text_highlight_rules").TextHighlightRules;
var JavaScriptHighlightRules = require("ace/mode/javascript_highlight_rules").JavaScriptHighlightRules;
var XmlHighlightRules = require("ace/mode/xml_highlight_rules").XmlHighlightRules;
var HtmlHighlightRules = require("ace/mode/html_highlight_rules").HtmlHighlightRules;
var CssHighlightRules = require("ace/mode/css_highlight_rules").CssHighlightRules;
var ScssHighlightRules = require("ace/mode/scss_highlight_rules").ScssHighlightRules;
var SassHighlightRules = require("ace/mode/sass_highlight_rules").SassHighlightRules;
var LessHighlightRules = require("ace/mode/less_highlight_rules").LessHighlightRules;
var PerlHighlightRules = require("ace/mode/perl_highlight_rules").PerlHighlightRules;
var PythonHighlightRules = require("mode/python_highlight_rules").PythonHighlightRules;
var RubyHighlightRules = require("ace/mode/ruby_highlight_rules").RubyHighlightRules;
var ScalaHighlightRules = require("ace/mode/scala_highlight_rules").ScalaHighlightRules;
var ShHighlightRules = require("mode/sh_highlight_rules").ShHighlightRules;
var StanHighlightRules = require("mode/stan_highlight_rules").StanHighlightRules;
var SqlHighlightRules = require("mode/sql_highlight_rules").SqlHighlightRules;
var MermaidHighlightRules = require("mode/mermaid_highlight_rules").MermaidHighlightRules;
var DotHighlightRules = require("ace/mode/dot_highlight_rules").DotHighlightRules;


var escaped = function (ch) {
   return "(?:[^" + lang.escapeRegExp(ch) + "\\\\]|\\\\.)*";
};

var $rainbowFencedDivs = true;
var $numFencedDivsColors = 7;

exports.setRainbowFencedDivs = function (value) {
   $rainbowFencedDivs = value;
};
exports.getRainbowFencedDivs = function () {
   return $rainbowFencedDivs;
};
exports.setNumFencedDivsColors = function (value) {
   $numFencedDivsColors = value;
};

var MarkdownHighlightRules = function () {

   var slideFields = lang.arrayToMap(
      ("title|author|date|rtl|depends|autosize|width|height|transition|transition-speed|font-family|css|class|navigation|incremental|left|right|id|audio|video|type|at|help-doc|help-topic|source|console|console-input|execute|pause")
         .split("|")
   );

   // regexp must not have capturing parentheses
   // regexps are ordered -> the first match is used

   // handle highlighting for *abc*, _abc_ separately, as pandoc's
   // parser is a bit more strict about where '_' can appear
   var strongUnderscore = {
      token: ["text", "constant.numeric.text", "constant.numeric.text", "constant.numeric.text"],
      regex: "(^|\\s+)(_{2,3})(?![\\s_])(.*?)(?=_)(\\2)\\b"
   };

   var emphasisUnderscore = {
      token: ["text", "constant.language.boolean.text"],
      regex: "(^|\\s+)(_(?=[^\\s_]).*?_)\\b"
   };

   var strongStars = {
      token: ["constant.numeric.text", "constant.numeric.text", "constant.numeric.text"],
      regex: "([*]{2,3})(?![\\s*])(.*?)(?=[*])(\\1)"
   };

   var emphasisStars = {
      token: ["constant.language.boolean.text"],
      regex: "([*](?=[^\\s*]).*?[*])"
   };

   var inlineNote = {
      token: "text",
      regex: "\\^\\[" + escaped("]") + "\\]"
   };

   var reference = {
      token: ["text", "constant", "text", "url", "string", "text"],
      regex: "^([ ]{0,3}\\[)([^\\]]+)(\\]:\\s*)([^ ]+)(\\s*(?:[\"][^\"]+[\"])?(\\s*))$"
   };

   var linkByReference = {
      token: ["text", "keyword", "text", "constant", "text"],
      regex: "(\\s*\\[)(" + escaped("]") + ")(\\]\\[)(" + escaped("]") + ")(\\])"
   };

   var linkByUrl = {
      token: ["text", "keyword", "text", "markup.href", "string", "text", "paren.keyword.operator", "nospell", "paren.keyword.operator"],
      regex: "(\\s*\\[)(" +                            // [
         escaped("]") +                                // link text
         ")(\\]\\()" +                                 // ](
         '((?:[^\\)\\s\\\\]|\\\\.|\\s(?=[^"]))*)' +    // href
         '(\\s*"' + escaped('"') + '"\\s*)?' +        // "title"
         "(\\))" +                                     // )
         "(?:(\\s*{)((?:[^\\}]+))(\\s*}))?"            // { block text }
   };

   var urlLink = {
      token: ["text", "keyword", "text"],
      regex: "(<)((?:https?|ftp|dict):[^'\">\\s]+|(?:mailto:)?[-.\\w]+\\@[-a-z0-9]+(?:\\.[-a-z0-9]+)*\\.[a-z]+)(>)"
   };

   this.$rules = {

      "basic": [{
         token: "constant.language.escape",
         regex: /\\[\\`*_{}[\]()#+\-.!]/
      }, { // latex-style inverted question mark
         token: "text",
         regex: /[?]`/
      }, { // inline r code
         token: "support.function.inline_r_chunk",
         regex: "`r (?:.*?[^`])`"
      }, { // code span `
         token: ["support.function", "support.function", "support.function"],
         regex: "(`+)(.*?[^`])(\\1)"
      },
         inlineNote,
         reference,
         linkByReference,
         linkByUrl,
         urlLink,
         strongStars,
         strongUnderscore,
         emphasisStars,
         emphasisUnderscore
      ],

      "start": [{
         token: "empty_line",
         regex: '^\\s*$',
         next: "allowBlock"
      }, { // latex-style inverted question mark
         token: "text",
         regex: /[?]`/
      }, { // inline r code
         token: "support.function.inline_r_chunk",
         regex: "`r (?:.*?[^`])`"
      }, { // code span `
         token: ["support.function", "support.function", "support.function"],
         regex: "(`+)([^\\r]*?[^`])(\\1)"
      }, { // h1 with equals
         token: "markup.heading.1",
         regex: "^\\={3,}\\s*$",
         next: "fieldblock"
      }, { // h1
         token: "markup.heading.1",
         regex: "^={3,}(?=\\s*$)"
      }, { // h2
         token: "markup.heading.2",
         regex: "^\\-{3,}(?=\\s*$)"
      }, {
         // opening fenced div
         token: "fenced_open",
         regex: "^[:]{3,}\\s*.*$",
         onMatch: function (val, state, stack, line, context) {

            if (!$rainbowFencedDivs) {
               return "keyword.operator";
            }

            var color = (context.fences || 0) % $numFencedDivsColors;
            var close = /^[:]{3,}\s*$/.test(val);

            if (close) {
               context.fences = color + 1;
               return "fenced_div_" + color;
            } else {
               // separating the fence (:::) from the follow up text
               // in case we want to style them differently
               var rx = /^([:]{3,})(.*)$/;
               return [
                  { type: "fenced_div_" + color, value: val.replace(rx, '$1') },
                  { type: "fenced_div_text_" + color, value: val.replace(rx, '$2') },
               ];
            }
         },
         next: "start"
      }, {
         token: function (value) {
            return "markup.heading." + value.length;
         },
         regex: /^#{1,6}/,
         next: "header"
      }, { // ioslides-style bullet
         token: "string.blockquote",
         regex: "^\\s*>\\s*(?=[-])"
      }, { // block quote
         token: "string.blockquote",
         regex: "^\\s*>\\s*",
         next: "blockquote"
      },
         inlineNote,
         reference,
         linkByReference,
      { // HR *
         token: "constant.hr",
         regex: "^\\s*[*](?:\\s*[*]){2,}\\s*$",
         next: "allowBlock",
      }, { // HR -
         token: "constant.hr",
         regex: "^\\s*[-](?:\\s*[-]){2,}\\s*$",
         next: "allowBlock",
      }, { // HR _
         token: "constant.hr",
         regex: "^\\s*[_](?:\\s*[_]){2,}\\s*$",
         next: "allowBlock"
      }, { // $ escape
         token: "text",
         regex: "\\\\\\$"
      }, { // MathJax $$
         token: "latex.markup.list.string.begin",
         regex: "\\${2}",
         next: "mathjaxdisplay"
      }, { // MathJax $...$ (org-mode style)
         token: ["latex.markup.list.string.begin", "latex.support.function", "latex.markup.list.string.end"],
         regex: "(\\$)((?:(?:\\\\.)|(?:[^\\$\\\\]))*?)(\\$)"
      }, { // simple links <url>
         token: ["text", "keyword", "text"],
         regex: "(<)(" +
            "(?:https?|ftp|dict):[^'\">\\s]+" +
            "|" +
            "(?:mailto:)?[-.\\w]+\\@[-a-z0-9]+(?:\\.[-a-z0-9]+)*\\.[a-z]+" +
            ")(>)"
      }, {
         // embedded latex command
         token: "keyword",
         regex: "\\\\(?:[a-zA-Z0-9]+|[^a-zA-Z0-9])"
      }, {
         // brackets
         token: "paren.keyword.operator",
         regex: "[{}]"
      }, {
         // pandoc citation
         token: "markup.list",
         regex: "-?\\@[\\w\\d-]+"
      }, {
         token: "text",
         regex: "[^\\*_%$`\\[#<>{}\\\\@\\s!]+"
      }, {
         token: "text",
         regex: "\\\\"
      }, { // list
         token: "text",
         regex: "^\\s*(?:[*+-]|\\d+\\.)\\s+",
         next: "listblock"
      },
         strongStars,
         strongUnderscore,
         emphasisStars,
         emphasisUnderscore,
      { // html comment
         token: "comment",
         regex: "<\\!--",
         next: "html-comment"
      }, {
         include: "basic"
      }],

      "html-comment": [{
         token: "comment",
         regex: "-->",
         next: "start"
      }, {
         defaultToken: "comment.text"
      }],

      // code block
      "allowBlock": [{
         token: "support.function",
         regex: "^ {4}.+",
         next: "allowBlock"
      }, {
         token: "empty_line",
         regex: "^\\s*$",
         next: "allowBlock"
      }, {
         token: "empty",
         regex: "",
         next: "start"
      }],

      "header": [{
         regex: "$",
         next: "start"
      }, {
         include: "basic"
      }, {
         defaultToken: "heading"
      }],

      "listblock": [{ // Lists only escape on completely blank lines.
         token: "empty_line",
         regex: "^\\s*$",
         next: "start"
      }, { // list
         token: "text",
         regex: "^\\s{0,3}(?:[*+-]|\\d+\\.)\\s+",
         next: "listblock"
      }, { // MathJax $...$ (org-mode style)
         token: ["latex.markup.list.string.begin", "latex.support.function", "latex.markup.list.string.end"],
         regex: "(\\$)((?:(?:\\\\.)|(?:[^\\$\\\\]))*?)(\\$)"
      }, {
         include: "basic", noEscape: true
      }, {
         defaultToken: "text" //do not use markup.list to allow stling leading `*` differently
      }],

      "blockquote": [{ // Blockquotes only escape on blank lines.
         token: "empty_line",
         regex: "^\\s*$",
         next: "start"
      }, {
         token: "constant.language.escape",
         regex: /\\[\\`*_{}[\]()#+\-.!]/
      }, { // latex-style inverted question mark
         token: "text",
         regex: /[?]`/
      }, { // inline r code
         token: "support.function.inline_r_chunk",
         regex: "`r (?:.*?[^`])`"
      }, { // code span `
         token: ["support.function", "support.function", "support.function"],
         regex: "(`+)(.*?[^`])(\\1)"
      },
         inlineNote,
         reference,
         linkByReference,
         linkByUrl,
         urlLink,
         strongStars,
         strongUnderscore,
         emphasisStars,
         emphasisUnderscore,
      {
         defaultToken: "string.blockquote"
      }],

      "fieldblock": [{
         token: function (value) {
            var field = value.slice(0, -1);
            if (slideFields[field])
               return "comment.doc.tag";
            else
               return "text";
         },
         regex: "^" + "[\\w-]+\\:",
         next: "fieldblockvalue"
      }, {
         token: "text",
         regex: "(?=.+)",
         next: "start"
      }],

      "fieldblockvalue": [{
         token: "text",
         regex: "$",
         next: "fieldblock"
      }, {
         token: "text",
         regex: "[^{}]+"
      }],

      "mathjaxdisplay": [{
         token: "latex.markup.list.string.end",
         regex: "\\${2}",
         next: "start"
      }, {
         token: "latex.support.function",
         regex: "[^\\$]+"
      }],

      "mathjaxnativedisplay": [{
         token: "latex.markup.list.string.end",
         regex: "\\\\\\]",
         next: "start"
      }, {
         token: "latex.support.function",
         regex: "[\\s\\S]+?"
      }],

      "mathjaxnativeinline": [{
         token: "latex.markup.list.string.end",
         regex: "\\\\\\)",
         next: "start"
      }, {
         token: "latex.support.function",
         regex: "[\\s\\S]+?"
      }]

   };

   // Support for GitHub blocks
   this.$rules["start"].unshift(
      {
         token: "support.function",
         regex: "^\\s*`{3,16}(?!`)",
         onMatch: function (value, state, stack, line, context) {
            // Check whether we're already within a chunk. If so,
            // skip this chunk header -- assume that it's embedded
            // within another active chunk.
            context.chunk = context.chunk || {};
            if (context.chunk.state != null) {
               this.next = state;
               return this.token;
            }

            // A chunk header was found; record the state we entered
            // from, and also the width of the chunk header.
            var match = /^\s*((?:`|-)+)/.exec(value);
            context.chunk.width = match[1].length;
            context.chunk.state = state;

            // Update the next state and return the matched token.
            this.next = `github-block-${context.chunk.width}`;
            return this.token;
         }
      }
   );

   var githubBlockExitRules = [
      {
         token: "support.function",
         regex: "^\\s*`{3,16}(?!`)",
         onMatch: function (value, state, stack, line, context) {
            // Check whether the width of this chunk tail matches
            // the width of the chunk header that started this chunk.
            var match = /^\s*((?:`|-)+)/.exec(value);
            var width = match[1].length;
            if (context.chunk.width !== width) {
               this.next = state;
               return this.token;
            }

            // Update the next state and return the matched token.
            this.next = context.chunk.state || "start";
            delete context.chunk;
            return this.token;
         }
      },
      {
         token: "support.function",
         regex: ".+"
      }
   ];

   for (var i = 3; i <= 16; i++) {
      this.$rules[`github-block-${i}`] = githubBlockExitRules;
   }

   this.normalizeRules();
   };
   
oop.inherits(MarkdownHighlightRules, TextHighlightRules);
exports.MarkdownHighlightRules = MarkdownHighlightRules;

});
