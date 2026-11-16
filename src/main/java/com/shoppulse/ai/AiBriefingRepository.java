package com.shoppulse.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Storage for generated briefings; the newest week wins, and within a week the newest run. */
public interface AiBriefingRepository extends JpaRepository<AiBriefing, Long> {

    Optional<AiBriefing> findFirstByOrderByWeekStartDescCreatedAtDesc();
}
