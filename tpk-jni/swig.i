/* File : Swig.i */
%module(directors="1") tpkJni

%{
  #include "interface.h"
%}

/* turn on director wrapping Callback */
%feature("director") IDemandsCB;

/* Let's just grab the original header file here */
%include "interface.h"
