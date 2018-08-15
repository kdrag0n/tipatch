# Sets the minimum version of CMake required to build your native library.
cmake_minimum_required(VERSION 3.4.1)

# Sets some warning/error conditions and which version of C/C++ to use
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -flto -std=c11")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -flto -Wall -Werror -Wno-error=unused-command-line-argument -std=c++17")

# Fix LTO on release
string(REPLACE "-Os" "-O3" CMAKE_C_FLAGS_RELEASE ${CMAKE_C_FLAGS_RELEASE})
string(REPLACE "-Os" "-O3" CMAKE_C_FLAGS ${CMAKE_C_FLAGS})
string(REPLACE "-Os" "-O3" CMAKE_CXX_FLAGS_RELEASE ${CMAKE_CXX_FLAGS_RELEASE})
string(REPLACE "-Os" "-O3" CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})

# Include a location to the header files
include_directories(src/main/cpp)

if (NOT (${CMAKE_BUILD_TYPE} STREQUAL "Release"))
    find_library( # Defines the name of the path variable that stores the
                  # location of the NDK library.
                  log-lib

                  # Specifies the name of the NDK library that
                  # CMake needs to locate.
                  log )
else()
    set(log-lib "")
endif()

find_library(zlib z)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.
add_library( # Specifies the name of the library.
             tipatch

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/image.cpp
             src/main/cpp/java.cpp
             src/main/cpp/io.cpp
             src/main/cpp/gzip.cpp
             src/main/cpp/decompress.cpp
             src/main/cpp/compress.cpp
             src/main/cpp/const.cpp
             src/main/cpp/patcher.cpp
             src/main/cpp/output.cpp
             src/main/cpp/xxhash.c
             src/main/cpp/lzo/minilzo.c
             src/main/cpp/util.cpp )

# Links your native library against one or more other native libraries.
target_link_libraries(tipatch ${log-lib} ${zlib})
