package com.milestonepay.ai;

/** Contract call 1 request: {@code { jobId, milestoneId, brief }}. */
public record CriteriaRequest(String jobId, String milestoneId, String brief) {
}
