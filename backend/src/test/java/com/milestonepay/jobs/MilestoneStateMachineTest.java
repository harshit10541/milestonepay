package com.milestonepay.jobs;

import com.milestonepay.common.Actor;
import com.milestonepay.events.EventLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.milestonepay.jobs.MilestoneState.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MilestoneStateMachineTest {

    /** Written out separately from the production map on purpose, so a typo in either one fails here. */
    private static final Set<String> EXPECTED = Set.of(
            "draft>criteria_approved", "draft>cancelled",
            "criteria_approved>funded", "criteria_approved>cancelled",
            "funded>submitted", "funded>cancelled",
            "submitted>in_review",
            "in_review>awaiting_client",
            "awaiting_client>released", "awaiting_client>changes_requested", "awaiting_client>disputed",
            "changes_requested>submitted");

    private static final UUID MILESTONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Actor ACTOR = Actor.system("test");

    private EventLog eventLog;
    private MilestoneStateMachine machine;

    @BeforeEach
    void setUp() {
        eventLog = mock(EventLog.class);
        machine = new MilestoneStateMachine(eventLog);
    }

    /** All 100 (from, to) pairs, so every allowed and every refused change is covered. */
    static Stream<Arguments> allPairs() {
        return Arrays.stream(MilestoneState.values())
                .flatMap(from -> Arrays.stream(MilestoneState.values()).map(to -> Arguments.of(from, to)));
    }

    private static boolean expected(MilestoneState from, MilestoneState to) {
        return EXPECTED.contains(from.dbValue() + ">" + to.dbValue());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void allowsExactlyTheAgreedTransitions(MilestoneState from, MilestoneState to) {
        assertThat(machine.canTransition(from, to)).isEqualTo(expected(from, to));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void allowedTransitionRecordsOneEventAndRefusedOneRecordsNothing(MilestoneState from, MilestoneState to) {
        if (expected(from, to)) {
            assertThat(machine.transition(MILESTONE, from, to, ACTOR, "test")).isEqualTo(to);
            verify(eventLog).record(MILESTONE, "STATE_CHANGED", ACTOR,
                    Map.of("from", from.dbValue(), "to", to.dbValue(), "reason", "test"));
        } else {
            assertThatThrownBy(() -> machine.transition(MILESTONE, from, to, ACTOR, "test"))
                    .isInstanceOfSatisfying(IllegalTransitionException.class, e -> {
                        assertThat(e.from()).isEqualTo(from);
                        assertThat(e.to()).isEqualTo(to);
                    });
            verifyNoInteractions(eventLog);
        }
    }

    @Test
    void finalStatesHaveNoWayOut() {
        assertThat(machine.nextStates(RELEASED)).isEmpty();
        assertThat(machine.nextStates(CANCELLED)).isEmpty();
        assertThat(machine.nextStates(DISPUTED)).isEmpty();
    }

    @Test
    void cancelIsOnlyPossibleBeforeAnySubmission() {
        Set<MilestoneState> canCancel = EnumSet.noneOf(MilestoneState.class);
        for (MilestoneState s : MilestoneState.values()) {
            if (machine.canTransition(s, CANCELLED)) {
                canCancel.add(s);
            }
        }
        assertThat(canCancel).containsExactlyInAnyOrder(DRAFT, CRITERIA_APPROVED, FUNDED);
    }

    @Test
    void releasedIsOnlyReachableFromAwaitingClient() {
        for (MilestoneState s : MilestoneState.values()) {
            assertThat(machine.canTransition(s, RELEASED)).as(s.dbValue()).isEqualTo(s == AWAITING_CLIENT);
        }
    }

    @Test
    void everyStateIsReachableFromDraft() {
        Set<MilestoneState> seen = EnumSet.of(DRAFT);
        Deque<MilestoneState> todo = new ArrayDeque<>(Set.of(DRAFT));
        while (!todo.isEmpty()) {
            for (MilestoneState next : machine.nextStates(todo.pop())) {
                if (seen.add(next)) {
                    todo.push(next);
                }
            }
        }
        assertThat(seen).containsExactlyInAnyOrder(MilestoneState.values());
    }

    @Test
    void mapCannotBeChangedAtRuntime() {
        assertThatThrownBy(() -> machine.nextStates(DRAFT).add(RELEASED))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingInputs() {
        assertThatThrownBy(() -> machine.transition(MILESTONE, DRAFT, CANCELLED, ACTOR, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> machine.transition(null, DRAFT, CANCELLED, ACTOR, "test"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> machine.transition(MILESTONE, DRAFT, CANCELLED, null, "test"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> machine.transition(MILESTONE, null, CANCELLED, ACTOR, "test"))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(eventLog);
    }
}
