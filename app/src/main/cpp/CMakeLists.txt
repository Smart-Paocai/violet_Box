cmake_minimum_required(VERSION 3.22.1)

project("violet_native")

add_library(
        violet_native
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        violet_native
        ${log-lib})
