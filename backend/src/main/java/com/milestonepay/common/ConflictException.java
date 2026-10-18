package com.milestonepay.common;

/** 409: the request is valid but the milestone isn't in a state that allows it right now. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
