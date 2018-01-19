LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, ../FileCoreLibrary/src src)

include $(LOCAL_PATH)/../Environment/Environment.mk

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, ../FileCoreLibrary/res res)
LOCAL_PACKAGE_NAME := FileManager2
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
