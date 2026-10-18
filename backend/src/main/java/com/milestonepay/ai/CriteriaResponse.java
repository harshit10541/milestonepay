package com.milestonepay.ai;

import java.util.List;

/**
 * Contract call 1 response: {@code { criteria: [{ id, text, type, checkMethod }] }}.
 * Treat it as untrusted input: the criteria module validates it before storing anything.
 */
public record CriteriaResponse(List<Criterion> criteria) {

    /** {@code type} is {@code "objective"} or {@code "subjective"}; kept as a string so bad values can be reported. */
    public record Criterion(String id, String text, String type, String checkMethod) {
    }
}
