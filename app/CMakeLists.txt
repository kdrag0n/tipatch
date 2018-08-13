# Sets the minimum version of CMake required to build your native library.
cmake_minimum_required(VERSION 3.4.1)

# Sets some warning/error conditions and which version of C/C++ to use
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Werror -std=c++17")

# Include a location to the header files
include_directories(src/main/cpp)

find_library( # Defines the name of the path variable that stores the
              # location of the NDK library.
              log-lib

              # Specifies the name of the NDK library that
              # CMake needs to locate.
              log )

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
             src/main/cpp/tipatch.cpp
             src/main/cpp/jni.cpp
             src/main/cpp/util.cpp
             src/main/cpp/gzip.cpp )

# Links your native library against one or more other native libraries.
target_link_libraries(tipatch ${log-lib} ${zlib})