package com.milestonepay.jobs;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A state change the allowed-transitions map does not permit. Endpoints answer 409 Conflict. */
@ResponseStatus(HttpStatus.CONFLICT)
public class IllegalTransitionException extends RuntimeException {

    private final MilestoneState from;
    private final MilestoneState to;

    public IllegalTransitionException(MilestoneState from, MilestoneState to) {
        super("Milestone cannot move from " + from.dbValue() + " to " + to.dbValue());
        this.from = from;
        this.to = to;
    }

    public MilestoneState from() {
        return from;
    }

    public MilestoneState to() {
        return to;
    }
}
