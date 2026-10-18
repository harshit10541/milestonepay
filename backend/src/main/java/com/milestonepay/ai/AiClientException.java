package com.milestonepay.ai;

/** The AI service failed or answered something that doesn't match the contract. Endpoints answer 502. */
public class AiClientException extends RuntimeException {

    public AiClientException(String message) {
        super(message);
    }

    public AiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
