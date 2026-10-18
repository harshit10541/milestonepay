package com.milestonepay.ai;

/** Calls to the AI service. One implementation is active, picked by {@code milestonepay.ai.mode}. */
public interface AiClient {

    /** Contract call 1: turn a milestone brief into a draft checklist. */
    CriteriaResponse generateCriteria(CriteriaRequest request);
}
