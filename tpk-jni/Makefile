# Simple wrapper for cmake

BUILD_DIR = build

# Copy generated Java files here so that sbt can compile them
TARGET_DIR = ../pk-assembly/src/main/java/tcs/pk/wrapper

all:
	test -d $(BUILD_DIR) || mkdir $(BUILD_DIR)
	(cd $(BUILD_DIR); cmake .. -G "Unix Makefiles"; cmake --build .  --verbose)
	test -d $(TARGET_DIR) || mkdir -p $(TARGET_DIR)
	rm -f $(TARGET_DIR)/*.java
	cp $(BUILD_DIR)/*.java $(TARGET_DIR)

# May require sudo
install:
	(cd $(BUILD_DIR); $(MAKE) install)

clean:
	rm -rf $(BUILD_DIR)

