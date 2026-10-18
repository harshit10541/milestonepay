package com.milestonepay.common;

/** 401: we don't know who is calling. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
