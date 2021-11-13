include(GNUInstallDirs)

set(THREADS_PREFER_PTHREAD_FLAG ON)
find_package(Threads REQUIRED)

set(INCLUDE_DIR /usr/local/include)
include_directories(. ${CMAKE_SOURCE_DIR}/src ${INCLUDE_DIR} ${INCLUDE_DIR}/tpk ${INCLUDE_DIR}/slalib ${INCLUDE_DIR}/tcspk ${INCLUDE_DIR}/csw)
find_package(JNI REQUIRED)
include_directories(${CMAKE_SOURCE_DIR}/src ${JNI_INCLUDE_DIRS} )
link_directories(${CMAKE_BINARY_DIR}/src "/usr/local/lib")

#set_target_properties(${PROJECT_NAME} PROPERTIES CXX_STANDARD 11)

add_executable (BaseCapTests BaseCapTests.cpp)
add_test (NAME BaseCapTests COMMAND BaseCapTests)
target_link_libraries(BaseCapTests
        tpk-jni
        tpk
        tcspk
        slalib
        tinyxml
        csw
        m
        Threads::Threads)

