package com.milestonepay.common;

/**
 * 404. Also used when the caller is not a party to the job, so outsiders can't tell
 * which job or milestone ids exist.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
