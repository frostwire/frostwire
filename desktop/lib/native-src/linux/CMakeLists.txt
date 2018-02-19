cmake_minimum_required(VERSION 3.1)
project(SystemUtils)

add_compile_options(
    -fpermissive)

add_library(SystemUtilities SHARED
    SystemUtilities.hpp
    SystemUtilities.cpp)

target_include_directories(SystemUtilities PRIVATE
    /usr/lib/jvm/java-8-openjdk-amd64/include
    /usr/lib/jvm/java-8-openjdk-amd64/include/linux)

set_target_properties(SystemUtilities PROPERTIES LINK_FLAGS
    -static-libstdc++
    -static-libgcc
    "-z noexecstack")
