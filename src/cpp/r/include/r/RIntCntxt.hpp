/*
 * RIntCntxt.cpp
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

#ifndef R_INTERNAL_CONTEXT_HPP
#define R_INTERNAL_CONTEXT_HPP

#include "RInterface.hpp"
#include "RCntxt.hpp"

namespace rstudio {
namespace r {
namespace context {

// header-only implementation of the RCntxtInterface; can serve as an 
// implementation for any memory layout (depending on the template parameter)
template <typename T>
class RIntCntxt: public RCntxtInterface
{
public:
   explicit RIntCntxt(void* pCntxt)
      : pCntxt_(static_cast<T*>(pCntxt))
   {
   }
   
   RCntxt nextcontext() const
   {
      if (pCntxt_->nextcontext == nullptr)
         return RCntxt();
      else
         return RCntxt(pCntxt_->nextcontext);
   }
   
   int callflag() const
   {
      return pCntxt_->callflag;
   }
   
   int evaldepth() const
   {
      return pCntxt_->evaldepth;
   }

   SEXP promargs() const
   {
      return pCntxt_->promargs;
   }
   
   SEXP callfun() const
   {
      return pCntxt_->callfun;
   }
   
   SEXP sysparent() const
   {
      return pCntxt_->sysparent;
   }
   
   SEXP call() const
   {
      return pCntxt_->call;
   }

   SEXP cloenv() const
   {
      return pCntxt_->cloenv;
   }

   SEXP srcref() const
   {
      return pCntxt_->srcref;
   }

   bool isNull() const
   {
      return false;
   }

   void* rcntxt() const
   {
      return static_cast<void *>(pCntxt_);
   }

private:
   const T* pCntxt_;
};

} // namespace context
} // namespace r
} // namespace rstudio

#endif

