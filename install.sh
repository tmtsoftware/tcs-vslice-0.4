#!/bin/sh
#
# Creates an install dir with all dependencies, including native C libs
#
# shellcheck disable=SC2164

dir=install/tcs-vslice-04
rm -rf $dir

# Note that zlog is from source on Linux, brew on Mac
SYS_LIBS="hiredis cbor uuid"
LOCAL_LIBS="tcspk tpk slalib tinyxml csw"

os="$(uname -s)"
case "${os}" in
    Linux*)
      LIB_SUFFIX=so
      LOCAL_LIBS="$LOCAL_LIBS zlog"
      #SYS_LIB_DIR=/lib/x86_64-linux-gnu;;
      SYS_LIB_DIR=/usr/lib64;;
    Darwin*)
      LIB_SUFFIX=dylib
      SYS_LIBS="$SYS_LIBS zlog"
      SYS_LIB_DIR=/usr/local/lib;;
    *)
      echo "Unsupported os: $os"
esac
LOCAL_LIB_DIR=/usr/local/lib64
LOCAL_LIB_DIR2=/usr/local/lib
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
  (cd $LOCAL_LIB_DIR2; tar cf - lib$i.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
  if test "$os" = "Darwin" ; then
    (cd $LOCAL_LIB_DIR; tar cf - lib$i.*.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
  fi
done

d=tpk-jni
(cd $d; make)
(cd $d/build/src; tar cf - lib$d.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
if test "$os" = "Darwin" ; then
   (cd $d/build/src; tar cf - lib$d.*.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
   # Fix rpath on MacOS
   for i in $SYS_LIBS ; do
     libpath=`otool -L $SYS_LIB_DIR/libcsw.dylib | grep $i | awk '{print $1;}'`
     install_name_tool -change $libpath '@rpath'/lib$i.dylib $TARGET_LIB_DIR/libcsw.dylib
   done
fi
