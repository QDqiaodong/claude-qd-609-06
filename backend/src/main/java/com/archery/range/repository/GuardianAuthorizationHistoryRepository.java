package com.archery.range.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.archery.range.domain.GuardianAuthorizationHistory;

public interface GuardianAuthorizationHistoryRepository extends JpaRepository<GuardianAuthorizationHistory, Long> {

    List<GuardianAuthorizationHistory> findByAuthorizationIdOrderByIdAsc(Long authorizationId);

    List<GuardianAuthorizationHistory> findByVersionIdOrderByIdAsc(Long versionId);
}
