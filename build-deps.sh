#!/bin/sh
# Build C/C++ dependencies
# Note: This script does a "make; sudo make install" for each of: tpk (private!), csw-c, ./tpk-jni,
# so that the shared libs are all installed in /usr/local/lib

srcdirs="../csw-c ../../TPK ./tpk-jni"
LOCAL_LIBS="tcspk tpk slalib tinyxml csw tpk-jni"
os="$(uname -s)"
case "${os}" in
    Linux*)
      LIB_SUFFIX=so
      LOCAL_LIBS="$LOCAL_LIBS zlog";;
    Darwin*)
      LIB_SUFFIX=dylib;;
    *)
      echo "Unsupported os: $os"
esac
LOCAL_LIB_DIR=/usr/local/lib

# Remove install libs before reinstalling
for i in $LOCAL_LIBS; do
  (cd $LOCAL_LIB_DIR; sudo rm lib$i.$LIB_SUFFIX*)
  if test "$os" = "Darwin" ; then
    (cd $LOCAL_LIB_DIR; sudo rm lib$i.*.$LIB_SUFFIX*)
  fi
done

for i in $srcdirs; do
  (cd $i; make clean all; sudo make install)
done
