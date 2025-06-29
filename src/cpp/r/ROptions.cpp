/*
 * ROptions.cpp
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

#define R_INTERNAL_FUNCTIONS
#include <r/ROptions.hpp>

#include <boost/format.hpp>

#include <shared_core/FilePath.hpp>
#include <shared_core/SafeConvert.hpp>

#include <core/Log.hpp>
#include <core/Thread.hpp>
#include <core/system/Environment.hpp>

#include <r/RExec.hpp>
#include <r/RUtil.hpp>

using namespace rstudio::core;

namespace rstudio {
namespace r {
namespace options {

namespace {

// last-known width of the build pane, in characters
int s_buildWidth = -1;

} // anonymous namespace

Error saveOptions(const FilePath& filePath)
{
   return exec::RFunction(".rs.saveOptions", filePath.getAbsolutePath()).call();
}
   
Error restoreOptions(const FilePath& filePath)
{
   return exec::RFunction(".rs.restoreOptions", filePath.getAbsolutePath()).call();
}
   
const int kDefaultWidth = 80;
   
void setOptionWidth(int width)
{
   r::util::setenv(
            "RSTUDIO_CONSOLE_WIDTH",
            core::safe_convert::numberToString(width));

   boost::format fmt("options(width = %1%)");
   Error error = r::exec::executeString(boost::str(fmt % width));
   if (error)
      LOG_ERROR(error);
}
   
int getOptionWidth()
{
   return getOption<int>("width", kDefaultWidth);
}

void setBuildOptionWidth(int width)
{
   s_buildWidth = width;
}

int getBuildOptionWidth()
{
   return s_buildWidth;
}

SEXP getOptionCell(const std::string& name)
{
   if (!ASSERT_MAIN_THREAD("Reading R option: " + name))
   {
      return R_NilValue;
   }

   // keep reference to R options list
   static SEXP optionsSEXP =
         Rf_findVarInFrame(R_BaseNamespace, Rf_install(".Options"));
   
   // we search through the options list directly and return
   // the underlying value to avoid duplicating the underlying
   // R object -- this allows us to detect changes if necessary
   for (SEXP elSEXP = optionsSEXP;
        elSEXP != R_NilValue;
        elSEXP = CDR(elSEXP))
   {
      SEXP tagSEXP = TAG(elSEXP);
      if (CHAR(PRINTNAME(tagSEXP)) == name)
         return elSEXP;
   }
   
   return R_NilValue;
}

SEXP getOption(const std::string& name)
{
   SEXP cellSEXP = getOptionCell(name);
   return CAR(cellSEXP);
}

SEXP setErrorOption(SEXP value)
{
   SEXP errorTag = Rf_install("error");
   SEXP option = SYMVALUE(Rf_install(".Options"));
   while (option != R_NilValue)
   {
      // are we removing the option?
      if (value == R_NilValue)
      {
         // remove the error option from the list
         if (TAG(CDR(option)) == errorTag)
         {
            SEXP previous = CAR(CDR(option));
            SETCDR(option, CDDR(option));
            return previous;
         }
      }

      // is this the error option?
      if (TAG(option) == errorTag)
      {
         // set and return previous value
         SEXP previous = CAR(option);
         SETCAR(option, value);
         return previous;
      }

      if (CDR(option) == R_NilValue && value != R_NilValue)
      {
         // no error option exists at all; add it so we can set the value
         SETCDR(option, Rf_allocList(1));
         SETCAR(CDR(option), value);
         SET_TAG(CDR(option), errorTag);
         break;
      }

      // next option
      option = CDR(option);
   }

   return R_NilValue;
}

} // namespace options   
} // namespace r
} // namespace rstudio



