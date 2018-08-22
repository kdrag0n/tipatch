package com.kdrag0n.tipatch.jni;

public class NativeException extends RuntimeException {
    public NativeException(String text) {
        super(text);
    }
}
