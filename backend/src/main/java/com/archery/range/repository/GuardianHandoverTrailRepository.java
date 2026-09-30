package com.archery.range.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.archery.range.domain.GuardianHandoverTrail;

public interface GuardianHandoverTrailRepository extends JpaRepository<GuardianHandoverTrail, Long> {

    List<GuardianHandoverTrail> findByHandoverIdOrderByIdAsc(Long handoverId);
}
