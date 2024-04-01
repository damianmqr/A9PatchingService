LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := a9_eink_server
LOCAL_SRC_FILES := eink_daemon.c
LOCAL_CPPFLAGS := -std=gnu++0x -Wall -Wextra -Werror -fPIE -D_FORTIFY_SOURCE=2
LOCAL_LDLIBS := -llog

include $(BUILD_EXECUTABLE)