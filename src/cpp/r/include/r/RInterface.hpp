/*
 * RInterface.hpp
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

#ifndef R_INTERFACE_HPP
#define R_INTERFACE_HPP

#include <string>
#include <setjmp.h>

#ifdef _WIN32

#include <R_ext/Boolean.h>
#include <R_ext/RStartup.h>

extern "C" {

void R_RestoreGlobalEnvFromFile(const char *, Rboolean);
void R_SaveGlobalEnvToFile(const char *);
void R_Suicide(const char *);
char *R_HomeDir(void);
void Rf_jump_to_toplevel(void);
void Rf_onintr(void);
#define R_ClearerrConsole void
void R_FlushConsole();
void run_Rmainloop();
void Rf_mainloop(void);

extern __declspec(dllimport) int R_SignalHandlers;
extern __declspec(dllimport) void* R_GlobalContext;

}

typedef struct SEXPREC *SEXP;

#else

#define R_INTERFACE_PTRS 1
#include <Rinterface.h>

#endif

#ifdef _WIN32
# define RS_JMP_BUF struct { jmp_buf buf; int sigmask; int savedmask; }
#else
# define RS_JMP_BUF sigjmp_buf
#endif

typedef struct R_BCSTACK_T {
   int tag;
   int flags;
   union {
      int ival;
      double dval;
      SEXP sxpval;
   } u;
} R_BCSTACK_T;

typedef struct RCNTXT_44 {
    struct RCNTXT_44 *nextcontext;
    int callflag;
    RS_JMP_BUF cjmpbuf;
    int cstacktop;
    int evaldepth;
    SEXP promargs;
    SEXP callfun;
    SEXP sysparent;
    SEXP call;
    SEXP cloenv;
    SEXP conexit;
    void (*cend)(void *);
    void *cenddata;
    void *vmax;
    int intsusp;
    int gcenabled;
    int bcintactive;
    SEXP bcbody;
    void *bcpc;
    ptrdiff_t relpc;  // added in R 4.4
    SEXP handlerstack;
    SEXP restartstack;
    struct RPRSTACK* prstack;
    R_BCSTACK_T* nodestack;
    R_BCSTACK_T* bcprottop;
    void* bcframe;  // added in R 4.4
    SEXP srcref;
    int browserfinish;
    R_BCSTACK_T returnValue;  // changed in R 4.4
    struct RCNTXT_44 *jumptarget;
    int jumpmask;
} RCNTXT_44;

typedef struct RCNTXT_40 {
    struct RCNTXT_40 *nextcontext;
    int callflag;
    RS_JMP_BUF cjmpbuf;
    int cstacktop;
    int evaldepth;
    SEXP promargs;
    SEXP callfun;
    SEXP sysparent;
    SEXP call;
    SEXP cloenv;
    SEXP conexit;
    void (*cend)(void *);
    void *cenddata;
    void *vmax;
    int intsusp;
    int gcenabled;
    int bcintactive;
    SEXP bcbody;
    void *bcpc;
    SEXP handlerstack;
    SEXP restartstack;
    struct RPRSTACK *prstack;
    R_BCSTACK_T *nodestack;
    R_BCSTACK_T *bcprottop; // new in R 4.0
    SEXP srcref;
    int browserfinish;
    SEXP returnValue;
    struct RCNTXT_40 *jumptarget;
    int jumpmask;
} RCNTXT_40;

typedef struct RCNTXT_34 {
    struct RCNTXT_34 *nextcontext;
    int callflag;
    RS_JMP_BUF cjmpbuf;
    int cstacktop;
    int evaldepth;
    SEXP promargs;
    SEXP callfun;
    SEXP sysparent;
    SEXP call;
    SEXP cloenv;
    SEXP conexit;
    void (*cend)(void *);
    void *cenddata;
    void *vmax;
    int intsusp;
    int gcenabled;
    int bcintactive;
    SEXP bcbody;
    void *bcpc;
    SEXP handlerstack;
    SEXP restartstack;
    struct RPRSTACK *prstack;
    struct {
       int tag;
       union {
          int ival;
          double dval;
          SEXP sxpval;
       } u;
    } *nodestack;
#ifdef BC_INT_STACK
    IStackval *intstack;
#endif
    SEXP srcref;
    int browserfinish;
    SEXP returnValue;
    struct RCNTXT_34 *jumptarget;
    int jumpmask;
} RCNTXT_34;

typedef struct RCNTXT_33 {
    struct RCNTXT_33 *nextcontext;
    int callflag;
    RS_JMP_BUF cjmpbuf;
    int cstacktop;
    int evaldepth;
    SEXP promargs;
    SEXP callfun;
    SEXP sysparent;
    SEXP call;
    SEXP cloenv;
    SEXP conexit;
    void (*cend)(void *);
    void *cenddata;
    void *vmax;
    int intsusp;
    int gcenabled;
    SEXP handlerstack;
    SEXP restartstack;
    struct RPRSTACK *prstack;
    SEXP *nodestack;
#ifdef BC_INT_STACK
    IStackval *intstack;
#endif
    SEXP srcref;
    int browserfinish;
    SEXP returnValue;
    struct RCNTXT_33 *jumptarget;
    int jumpmask;
} RCNTXT_33;

typedef struct RCNTXT_32 {
    struct RCNTXT_32 *nextcontext;
    int callflag;
    RS_JMP_BUF cjmpbuf;
    int cstacktop;
    int evaldepth;
    SEXP promargs;
    SEXP callfun;
    SEXP sysparent;
    SEXP call;
    SEXP cloenv;
    SEXP conexit;
    void (*cend)(void *);
    void *cenddata;
    void *vmax;
    int intsusp;
    SEXP handlerstack;
    SEXP restartstack;
    struct RPRSTACK *prstack;
    SEXP *nodestack;
#ifdef BC_INT_STACK
    IStackval *intstack;
#endif
    SEXP srcref;
} RCNTXT_32;

enum {
    CTXT_TOPLEVEL = 0,
    CTXT_NEXT	   = 1,
    CTXT_BREAK	   = 2,
    CTXT_LOOP	   = 3,
    CTXT_FUNCTION = 4,
    CTXT_CCODE	   = 8,
    CTXT_RETURN	= 12,
    CTXT_BROWSER  = 16,
    CTXT_GENERIC  = 20,
    CTXT_RESTART  = 32,
    CTXT_BUILTIN  = 64
};

#undef RS_JMP_BUF

#endif // R_INTERFACE_HPP

