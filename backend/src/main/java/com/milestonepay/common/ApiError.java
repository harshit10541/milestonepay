package com.milestonepay.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * The one error body every endpoint returns, e.g. {@code {"error":"not_found","message":"Job not found"}}.
 * {@code fields} is only set for validation errors: field name to what's wrong with it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String message, Map<String, String> fields) {

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, null);
    }
}
