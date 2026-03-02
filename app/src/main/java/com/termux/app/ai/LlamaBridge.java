package com.termux.app.ai;

public final class LlamaBridge {
    static {
        System.loadLibrary("ai-llama-jni");
    }

    private LlamaBridge() {}

    public static native long nativeInit(String modelPath, int nCtx);
    public static native void nativeFree(long handle);
    public static native String nativeComplete(long handle, String prompt, int nPredict);
}
