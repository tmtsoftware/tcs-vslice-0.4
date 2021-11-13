#!/bin/sh
#
# Creates an install dir with all dependencies, including native C libs
#
# shellcheck disable=SC2164

dir=install/tcs-vslice-04
rm -rf $dir

os="$(uname -s)"
case "${os}" in
    Linux*)
      LIB_SUFFIX=so
      SYS_LIB_DIR=/lib/x86_64-linux-gnu;;
    Darwin*)
      LIB_SUFFIX=dylib
      SYS_LIB_DIR=/usr/local/lib;;
    *)
      echo "Unsupported os: $os"
esac
LOCAL_LIB_DIR=/usr/local/lib
SYS_LIBS="hiredis cbor"
LOCAL_LIBS="tcs tcspk tpk slalib tinyxml csw slalib"
TARGET_LIB_DIR=$dir/lib/$os

# Make sure we can find sbt for the build
hash sbt 2>/dev/null || { echo >&2 "Please install sbt first. Aborting."; exit 1; }
sbt stage
set -x
for i in install $dir $dir/bin $dir/lib $TARGET_LIB_DIR $dir/conf; do test -d $i || mkdir $i; done
for i in bin lib; do
    for j in target/universal/stage/$i/* ; do
        cp -rf $j $dir/$i
    done
done
cp tcs-deploy/src/main/resources/*.conf $dir/conf/
rm $dir/conf/application.conf
rm -f $dir/bin/*.log.* $dir/bin/*.bat
cp tcs-deploy/README.md $dir/

# Native C dependencies (copy with links)
for i in $SYS_LIBS; do
  cp $SYS_LIB_DIR/lib$i.$LIB_SUFFIX* $TARGET_LIB_DIR
done
for i in $LOCAL_LIBS; do
  (cd $LOCAL_LIB_DIR; tar cf - lib$i.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
  if test "$os" = "Darwin" ; then
    (cd $LOCAL_LIB_DIR; tar cf - lib$i.*.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
  fi
done

d=tpk-jni
(cd $d; make)
(cd $d/build/src; tar cf - lib$d.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
if test "$os" = "Darwin" ; then
   (cd $d/build/src; tar cf - lib$d.*.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
fi
