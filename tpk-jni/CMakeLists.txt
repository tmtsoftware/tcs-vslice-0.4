cmake_minimum_required(VERSION 3.19)
project(tpk-jni LANGUAGES CXX VERSION 0.1)
set(CMAKE_C_STANDARD 11)
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
set(INCLUDE_DIR /usr/local/include)

include_directories(. ${INCLUDE_DIR} ${INCLUDE_DIR}/tpk ${INCLUDE_DIR}/slalib ${INCLUDE_DIR}/tcspk ${INCLUDE_DIR}/csw)
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS} .)
link_directories("/usr/local/lib")

add_library(${PROJECT_NAME} SHARED
        TpkC.cpp
        TpkC.h
        ScanTask.cpp
        ScanTask.h)

target_link_libraries(${PROJECT_NAME} tpk tcspk slalib tinyxml csw m pthread)

# set LINK_WHAT_YOU_USE to true so that dependent libs are not stripped away for optimization!
set_target_properties(${PROJECT_NAME} PROPERTIES
        CXX_STANDARD 11
        PUBLIC_HEADER TpkC.h
        SOVERSION 1)

#        LINK_WHAT_YOU_USE "TRUE"

install(TARGETS ${PROJECT_NAME}
        ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
        LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
        RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
        PUBLIC_HEADER DESTINATION include/${PROJECT_NAME})
