package com.milestonepay.ai;

import java.util.Set;

/**
 * The {@code checkMethod} values of the AI contract, mirrored from {@code contracts/README.md}. A test keeps
 * this list and the contract schema in step.
 * <p>
 * Proposal awaiting the AI side's confirmation. Kept as strings, like {@code type}, so the criteria module
 * can report a bad value instead of failing to parse it.
 */
public final class CheckMethod {

    /** Page loads: HTTP status and load time. */
    public static final String BROWSER_TIMING = "browser_timing";
    /** An element or section exists on the page. */
    public static final String BROWSER_ELEMENT = "browser_element";
    /** A form fills, submits and shows success. */
    public static final String BROWSER_FORM = "browser_form";
    /** No horizontal scroll at a 375px-wide viewport. */
    public static final String BROWSER_MOBILE = "browser_mobile";
    /** Subjective criteria: no automatic check, the client judges. */
    public static final String CLIENT_REVIEW = "client_review";

    public static final Set<String> ALL =
            Set.of(BROWSER_TIMING, BROWSER_ELEMENT, BROWSER_FORM, BROWSER_MOBILE, CLIENT_REVIEW);

    private CheckMethod() {
    }
}
