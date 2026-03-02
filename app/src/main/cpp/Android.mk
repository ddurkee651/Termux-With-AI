LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libtermux-bootstrap
LOCAL_SRC_FILES := termux-bootstrap-zip.S termux-bootstrap.c
include $(BUILD_SHARED_LIBRARY)

LLAMA_PREBUILT_DIR := $(LOCAL_PATH)/../jniLibs/$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)
LOCAL_MODULE := llama
LOCAL_SRC_FILES := $(LLAMA_PREBUILT_DIR)/libllama.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := ggml
LOCAL_SRC_FILES := $(LLAMA_PREBUILT_DIR)/libggml.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := ggml-base
LOCAL_SRC_FILES := $(LLAMA_PREBUILT_DIR)/libggml-base.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := ggml-cpu
LOCAL_SRC_FILES := $(LLAMA_PREBUILT_DIR)/libggml-cpu.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := ai-llama-jni
LOCAL_SRC_FILES := ai_llama_jni.cpp
LOCAL_CPPFLAGS += -std=c++17
LOCAL_C_INCLUDES += $(LOCAL_PATH)/llama/include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/llama/ggml/include
LOCAL_LDLIBS += -llog -landroid
LOCAL_SHARED_LIBRARIES := llama ggml ggml-base ggml-cpu
include $(BUILD_SHARED_LIBRARY)
