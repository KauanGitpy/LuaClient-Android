cmake_minimum_required(VERSION 3.22)
project(lua_client_mobile LANGUAGES CXX)

set(CMAKE_BUILD_TYPE RelWithDebInfo CACHE STRING "Build type" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared libraries" FORCE)

if(ANDROID)
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
    add_link_options("-Wl,-z,max-page-size=16384")
endif()

add_subdirectory(preloader)
add_subdirectory(libHttpClient/Build/libHttpClient.Android)

add_library(LuaClient SHARED
    lua_client/LuaClientCore.cpp)

target_compile_features(LuaClient PRIVATE cxx_std_20)
target_compile_options(LuaClient PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -fvisibility=hidden
    -fvisibility-inlines-hidden)

find_library(LUA_ANDROID_LOG_LIB log)
if(LUA_ANDROID_LOG_LIB)
    target_link_libraries(LuaClient PRIVATE "${LUA_ANDROID_LOG_LIB}")
endif()

set_target_properties(LuaClient PROPERTIES
    OUTPUT_NAME "LuaClient")
