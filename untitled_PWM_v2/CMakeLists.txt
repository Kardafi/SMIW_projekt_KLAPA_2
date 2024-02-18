cmake_minimum_required(VERSION 3.20.0)
find_package(Zephyr REQUIRED HINTS $ENV{ZEPHYR_BASE})

project(untitled_PWM_v2)

target_sources(app PRIVATE src/main.c)

# Custom files and folders
target_sources(app PRIVATE
    src/custom_files/serwo_control.c;
    src/custom_files/remote.c
    src/custom_files/battery.c
)
zephyr_library_include_directories(src/custom_files)
