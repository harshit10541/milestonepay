package com.milestonepay.jobs;

import com.milestonepay.common.Actor;
import com.milestonepay.common.ConflictException;
import com.milestonepay.common.NotFoundException;
import com.milestonepay.events.EventLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Criteria approval rules, with the real state machine and mocked storage. */
class MilestoneServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID FREELANCER = UUID.fromString("00000000-0000-0000-0000-00000000000f");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-00000000000e");

    private EventLog eventLog;
    private MilestoneService service;
    private Milestone milestone;
    private UUID milestoneId;

    @BeforeEach
    void setUp() {
        UUID jobId = UUID.randomUUID();
        milestoneId = UUID.randomUUID();
        Job job = new Job(CLIENT, FREELANCER, "Landing page", NOW);
        ReflectionTestUtils.setField(job, "id", jobId);
        milestone = new Milestone(jobId, 1, "Landing page", "brief", 30000, "USD", 2, 300, NOW);
        ReflectionTestUtils.setField(milestone, "id", milestoneId);

        MilestoneRepository milestones = mock(MilestoneRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(milestones.findById(milestoneId)).thenReturn(Optional.of(milestone));
        when(milestones.findByIdForUpdate(milestoneId)).thenReturn(Optional.of(milestone));
        when(jobs.findById(jobId)).thenReturn(Optional.of(job));

        eventLog = mock(EventLog.class);
        service = new MilestoneService(milestones, jobs, new MilestoneStateMachine(eventLog), eventLog,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void oneApprovalKeepsDraft() {
        MilestoneView v = service.recordCriteriaApproval(milestoneId, CLIENT);

        assertThat(v.state()).isEqualTo(MilestoneState.DRAFT);
        assertThat(v.clientCriteriaApprovedAt()).isEqualTo(NOW);
        assertThat(v.freelancerCriteriaApprovedAt()).isNull();
        verify(eventLog).record(milestoneId, "CRITERIA_APPROVED", Actor.client(CLIENT), Map.of("by", "client"));
    }

    @Test
    void secondApprovalMovesToCriteriaApprovedThroughTheStateMachine() {
        service.recordCriteriaApproval(milestoneId, CLIENT);
        MilestoneView v = service.recordCriteriaApproval(milestoneId, FREELANCER);

        assertThat(v.state()).isEqualTo(MilestoneState.CRITERIA_APPROVED);
        assertThat(v.yourRole()).isEqualTo(PartyRole.FREELANCER);
        verify(eventLog).record(eq(milestoneId), eq("STATE_CHANGED"), eq(Actor.freelancer(FREELANCER)), anyMap());
    }

    @Test
    void approvingTwiceChangesNothing() {
        service.recordCriteriaApproval(milestoneId, CLIENT);
        service.recordCriteriaApproval(milestoneId, CLIENT);

        assertThat(milestone.state()).isEqualTo(MilestoneState.DRAFT);
        verify(eventLog, times(1)).record(any(), eq("CRITERIA_APPROVED"), any(), anyMap());
    }

    @Test
    void outsiderGetsNotFound() {
        assertThatThrownBy(() -> service.recordCriteriaApproval(milestoneId, OUTSIDER))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(milestoneId, OUTSIDER)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), CLIENT)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void clearingApprovalsResetsBothAndLogsIt() {
        service.recordCriteriaApproval(milestoneId, FREELANCER);
        service.clearCriteriaApprovals(milestoneId, CLIENT);

        assertThat(milestone.clientCriteriaApprovedAt()).isNull();
        assertThat(milestone.freelancerCriteriaApprovedAt()).isNull();
        verify(eventLog).record(milestoneId, "CRITERIA_APPROVALS_CLEARED", Actor.client(CLIENT), Map.of());
    }

    @Test
    void clearingWithNothingApprovedLogsNothing() {
        service.clearCriteriaApprovals(milestoneId, CLIENT);
        verify(eventLog, never()).record(any(), eq("CRITERIA_APPROVALS_CLEARED"), any(), anyMap());
    }

    @Test
    void criteriaAreFrozenOnceApproved() {
        service.recordCriteriaApproval(milestoneId, CLIENT);
        service.recordCriteriaApproval(milestoneId, FREELANCER);

        assertThatThrownBy(() -> service.clearCriteriaApprovals(milestoneId, CLIENT))
                .isInstanceOf(ConflictException.class);
    }
}
