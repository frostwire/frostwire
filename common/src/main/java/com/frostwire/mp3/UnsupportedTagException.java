package com.frostwire.mp3;

public class UnsupportedTagException extends BaseException {

    public UnsupportedTagException() {
        super();
    }

    public UnsupportedTagException(String message) {
        super(message);
    }

    public UnsupportedTagException(String message, Throwable cause) {
        super(message, cause);
    }
}
