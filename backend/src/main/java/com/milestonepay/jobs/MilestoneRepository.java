package com.milestonepay.jobs;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    List<Milestone> findByJobIdOrderByPosition(UUID jobId);

    /**
     * {@code SELECT ... FOR UPDATE}: holds the row until the transaction ends, so two requests changing the
     * same milestone (e.g. client and freelancer approving at once) run one after the other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Milestone m where m.id = :id")
    Optional<Milestone> findByIdForUpdate(@Param("id") UUID id);
}
