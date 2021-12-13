#!/bin/sh
# Creates a zip file containing the native C shared libraries that can be used in a Homebrew formula
# Note: This script does a "make; sudo make install" for each of: tpk (private!), csw-c, ./tpk-jni,
# so that the shared libs are all installed in /usr/local/lib

# shellcheck disable=SC2164

dir=install/tcs-vslice
rm -rf $dir
srcdirs="../csw-c ../../TPK ./tpk-jni"
LOCAL_LIBS="tcspk tpk slalib tinyxml csw tpk-jni"

for i in $srcdirs; do
  (cd $i; make clean all; sudo make install)
done

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
TARGET_LIB_DIR=$dir

# Make sure we can find sbt for the build
set -x
for i in install $dir; do test -d $i || mkdir $i; done

# Native C dependencies (copy with links)
for i in $LOCAL_LIBS; do
  (cd $LOCAL_LIB_DIR; tar cf - lib$i.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
  if test "$os" = "Darwin" ; then
    (cd $LOCAL_LIB_DIR; tar cf - lib$i.*.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
  fi
done

cp tpk-jni/brew/Makefile $dir

zipfile=tcs-vslice-dylibs.zip
(cd install; rm -f $zipfile; zip -r $zipfile tcs-vslice; echo "NOTE: Update SHA in Homebrew formula"; shasum -a 256 $zipfile)

# Remove install libs before testing brew install
for i in $LOCAL_LIBS; do
  (cd $LOCAL_LIB_DIR; sudo rm lib$i.$LIB_SUFFIX*)
  if test "$os" = "Darwin" ; then
    (cd $LOCAL_LIB_DIR; sudo rm lib$i.*.$LIB_SUFFIX*)
  fi
done
