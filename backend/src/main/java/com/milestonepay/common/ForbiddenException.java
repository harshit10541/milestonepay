package com.milestonepay.common;

/** 403: the caller is a party to the job but not the one allowed to do this (e.g. the freelancer funding). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
