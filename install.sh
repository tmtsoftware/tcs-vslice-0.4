#!/bin/sh
#
# Creates an install dir with all dependencies, including native C libs
#

dir=install/tcs-vslice-04
rm -rf $dir

SYS_LIB_DIR=/lib/x86_64-linux-gnu
SYS_LIBS="hiredis cbor"
LOCAL_LIB_DIR=/usr/local/lib
LOCAL_LIBS="tpk-jni tcs tpk slalib tinyxml csw slalib"
TARGET_LIB_DIR=$dir/lib/`uname`-`arch`
LIB_SUFFIX=so

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
  (cd $SYS_LIB_DIR; tar cf - lib$i.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
done
for i in $LOCAL_LIBS; do
  (cd $LOCAL_LIB_DIR; tar cf - lib$i.$LIB_SUFFIX*) | (cd $TARGET_LIB_DIR; tar xf -)
done
