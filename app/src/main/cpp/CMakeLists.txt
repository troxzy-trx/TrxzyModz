cmake_minimum_required(VERSION 3.22.1)
project(troxzy_native LANGUAGES CXX)

add_library(troxzy SHARED native-lib.cpp)

target_compile_features(troxzy PRIVATE cxx_std_17)
target_compile_options(troxzy PRIVATE -Wall -Wextra -Werror=return-type -fvisibility=hidden)

find_library(android-log-lib log)
target_link_libraries(troxzy PRIVATE ${android-log-lib})
