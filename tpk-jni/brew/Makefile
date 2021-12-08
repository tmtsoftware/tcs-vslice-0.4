# Special makefile for copying libs in homebrew install

all:
	echo "Installing tcs-vslice libs in $(PREFIX)/lib"
	ls -l

install: all
	test -d $(PREFIX)/lib || mkdir $(PREFIX)/lib
	tar cf - *.dylib | (cd $(PREFIX)/lib; tar xf -)
