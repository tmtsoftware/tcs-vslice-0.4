include(GNUInstallDirs)
set(CMAKE_CXX_STANDARD 11)
enable_language(CXX)

set(THREADS_PREFER_PTHREAD_FLAG ON)
find_package(Threads REQUIRED)

set(INCLUDE_DIR /usr/local/include)

include_directories(. ${INCLUDE_DIR} ${INCLUDE_DIR}/tpk ${INCLUDE_DIR}/slalib ${INCLUDE_DIR}/tcspk ${INCLUDE_DIR}/csw)
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS} .)
link_directories("/usr/local/lib")

add_library(${PROJECT_NAME} SHARED
        FakeSystemClock.cpp
        FakeSystemClock.h
        TpkC.cpp
        TpkC.h
        ScanTask.cpp
        ScanTask.h)

target_link_libraries(${PROJECT_NAME}
        tpk
        tcspk
        slalib
        tinyxml
        csw
        m
        Threads::Threads)

set_target_properties(${PROJECT_NAME} PROPERTIES
        CXX_STANDARD 11
        PUBLIC_HEADER TpkC.h
        SOVERSION 1)

install(TARGETS ${PROJECT_NAME}
        ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
        LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
        RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
        PUBLIC_HEADER DESTINATION include/${PROJECT_NAME})
