package com.milestonepay.jobs;

import com.milestonepay.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/jobs")
class JobController {

    private final JobService jobs;

    JobController(JobService jobs) {
        this.jobs = jobs;
    }

    /** The caller becomes the job's client; all milestones start in draft. */
    @PostMapping
    ResponseEntity<JobView> create(@Valid @RequestBody CreateJobRequest req, CurrentUser caller) {
        JobView job = jobs.create(caller.id(), req);
        return ResponseEntity.created(URI.create("/jobs/" + job.id())).body(job);
    }

    /** Client or freelancer of the job; anyone else gets 404. */
    @GetMapping("/{jobId}")
    JobView get(@PathVariable UUID jobId, CurrentUser caller) {
        return jobs.get(jobId, caller.id());
    }
}
