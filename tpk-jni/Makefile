# Simple wrapper for cmake

# Use "make PREFIX=/my/dir" to change installation location
PREFIX = /usr/local

BUILD_DIR = build

# Copy generated Java files here so that sbt can compile them
TARGET_DIR = ../pk-assembly/src/main/java/tcs/pk/wrapper

all:
	(cd $(BUILD_DIR); cmake .. -G "Unix Makefiles" -DCMAKE_INSTALL_PREFIX=$(PREFIX); cmake --build .  --verbose)

# May require sudo
install:
	(cd $(BUILD_DIR); $(MAKE) install)

clean:
	rm -rf $(BUILD_DIR)

