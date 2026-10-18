package com.milestonepay.criteria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface CriterionRepository extends JpaRepository<Criterion, UUID> {

    List<Criterion> findByMilestoneIdOrderByPosition(UUID milestoneId);

    long countByMilestoneId(UUID milestoneId);

    /**
     * A bulk delete runs straight away. A derived delete would be queued behind the new inserts at flush,
     * and the new rows would hit the (milestone_id, position) unique key of the old ones.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from Criterion c where c.milestoneId = :milestoneId")
    int deleteByMilestoneId(@Param("milestoneId") UUID milestoneId);
}
