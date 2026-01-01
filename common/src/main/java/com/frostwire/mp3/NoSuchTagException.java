package com.frostwire.mp3;

public class NoSuchTagException extends BaseException {

    public NoSuchTagException() {
        super();
    }

    public NoSuchTagException(String message) {
        super(message);
    }

    public NoSuchTagException(String message, Throwable cause) {
        super(message, cause);
    }
}
