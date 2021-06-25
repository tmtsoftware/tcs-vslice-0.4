cmake_minimum_required(VERSION 3.16)
set(CMAKE_C_STANDARD 99)
enable_language(CXX)

set(DEFAULT_BUILD_TYPE "Release")

if (NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    message(STATUS "Setting build type to '${DEFAULT_BUILD_TYPE}' as none was specified.")
    set(CMAKE_BUILD_TYPE "${DEFAULT_BUILD_TYPE}" CACHE STRING "Choose the type of build." FORCE)
    # Set the possible values of build type for cmake-gui
    set_property(CACHE CMAKE_BUILD_TYPE PROPERTY STRINGS "Debug" "Release" "MinSizeRel" "RelWithDebInfo")
endif ()

include(GNUInstallDirs)

# Edit this to point to the top level TPK project source
set(TPK_DIR /usr/local/include)

project(tpk-jni LANGUAGES CXX VERSION 0.1)
include_directories(. ${TPK_DIR} ${TPK_DIR}/tpk ${TPK_DIR}/slalib ${TPK_DIR}/tcspk)
SET(CMAKE_SWIG_FLAGS -c++ -java -package tcs.pk.wrapper)
find_package(SWIG REQUIRED COMPONENTS java)
include(${SWIG_USE_FILE})
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS} .)

#link_directories(/usr/local/lib)
link_libraries(tinyxml tpk tcs slalib m pthread)

swig_add_library(${PROJECT_NAME}  LANGUAGE java  SOURCES
                impl.cpp
                interface.h
                ScanTask.cpp
                ScanTask.h
                swig.i)

install(TARGETS ${PROJECT_NAME} EXPORT MyLibConfig
        ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
        LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
        RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
