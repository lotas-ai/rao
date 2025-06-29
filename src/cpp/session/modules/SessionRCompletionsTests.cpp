/*
 * SessionRCompletionsTests.cpp
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

#include <tests/TestThat.hpp>

#include "SessionRCompletions.hpp"

namespace rstudio {
namespace session {
namespace modules {
namespace r_packages {

test_context("r_completions")
{
   test_that("finishExpression works")
   {
      expect_true(finishExpression("(abc") == "(abc)");
      expect_true(finishExpression(L"(abc") == L"(abc)");
   }

   // https://github.com/rstudio/rstudio/issues/14625
   test_that("finishExpression accepts non-ASCII inputs")
   {
      expect_true(finishExpression(L"(你好") == L"(你好)");
      expect_true(finishExpression(L"(こんにちは") == L"(こんにちは)");
   }
}

} // namespace r_completions
} // namespace modules
} // namespace rsession
} // namespace rstudio
