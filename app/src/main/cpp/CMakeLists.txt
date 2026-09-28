cmake_minimum_required(VERSION 3.22.1)
project(spd_host_exec)

add_library(spd_host_exec SHARED exec_helper.c)

find_library(log-lib log)
target_link_libraries(spd_host_exec ${log-lib})
