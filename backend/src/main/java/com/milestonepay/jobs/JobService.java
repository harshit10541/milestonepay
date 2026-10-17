package com.milestonepay.jobs;

import com.milestonepay.auth.UserService;
import com.milestonepay.common.Actor;
import com.milestonepay.common.Money;
import com.milestonepay.common.NotFoundException;
import com.milestonepay.events.EventLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates and reads jobs. Every read checks the caller is the job's client or freelancer. */
@Service
public class JobService {

    static final String MILESTONE_CREATED = "MILESTONE_CREATED";

    private final JobRepository jobs;
    private final MilestoneRepository milestones;
    private final UserService users;
    private final EventLog eventLog;
    private final MilestoneDefaults defaults;
    private final Clock clock;

    JobService(JobRepository jobs, MilestoneRepository milestones, UserService users, EventLog eventLog,
               MilestoneDefaults defaults, Clock clock) {
        this.jobs = jobs;
        this.milestones = milestones;
        this.users = users;
        this.eventLog = eventLog;
        this.defaults = defaults;
        this.clock = clock;
    }

    /** The caller becomes the client. Every milestone starts in {@code draft}. */
    @Transactional
    public JobView create(UUID clientId, CreateJobRequest req) {
        if (req.freelancerId().equals(clientId)) {
            throw new IllegalArgumentException("The freelancer must be a different user from the client");
        }
        if (!users.exists(req.freelancerId())) {
            throw new IllegalArgumentException("freelancerId is not a known user");
        }
        Instant now = clock.instant();
        Job job = jobs.save(new Job(clientId, req.freelancerId(), req.title().trim(), now));

        List<Milestone> created = new ArrayList<>();
        int position = 1;
        for (CreateJobRequest.NewMilestone in : req.milestones()) {
            Money amount = Money.of(in.amountMinor(), in.currency()); // rejects unknown currencies
            created.add(new Milestone(job.id(), position++, in.title().trim(), in.brief().trim(),
                    amount.minor(), amount.currency(), revisionRounds(in), reviewWindowSeconds(in), now));
        }
        // Flush so the rows exist before EventLog inserts events that reference them.
        milestones.saveAllAndFlush(created);

        Actor actor = Actor.client(clientId);
        for (Milestone m : created) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("jobId", job.id().toString());
            data.put("position", m.position());
            data.put("amountMinor", m.amountMinor());
            data.put("currency", m.currency());
            data.put("reviewWindowSeconds", m.reviewWindowSeconds());
            data.put("revisionRoundsAllowed", m.revisionRoundsAllowed());
            eventLog.record(m.id(), MILESTONE_CREATED, actor, data);
        }
        return view(job, PartyRole.CLIENT, created);
    }

    @Transactional(readOnly = true)
    public JobView get(UUID jobId, UUID callerId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Job not found"));
        PartyRole role = job.roleOf(callerId);
        if (role == null) {
            throw new NotFoundException("Job not found");
        }
        return view(job, role, milestones.findByJobIdOrderByPosition(jobId));
    }

    private int reviewWindowSeconds(CreateJobRequest.NewMilestone in) {
        if (in.reviewWindowSeconds() != null) {
            return in.reviewWindowSeconds();
        }
        long seconds = defaults.reviewWindow().toSeconds();
        if (seconds < MilestoneDefaults.MIN_REVIEW_WINDOW_SECONDS || seconds > MilestoneDefaults.MAX_REVIEW_WINDOW_SECONDS) {
            throw new IllegalStateException("milestonepay.milestones.review-window is out of range: " + seconds + "s");
        }
        return (int) seconds;
    }

    private int revisionRounds(CreateJobRequest.NewMilestone in) {
        return in.revisionRoundsAllowed() != null ? in.revisionRoundsAllowed() : defaults.revisionRounds();
    }

    private static JobView view(Job job, PartyRole role, List<Milestone> ms) {
        return new JobView(job.id(), job.title(), job.clientId(), job.freelancerId(), role, job.createdAt(),
                ms.stream().map(JobView.MilestoneSummary::of).toList());
    }
}
