package com.milestonepay.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface JobRepository extends JpaRepository<Job, UUID> {
}
