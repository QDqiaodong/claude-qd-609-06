package com.archery.range.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.archery.range.domain.GuardianHandoverResource;

public interface GuardianHandoverResourceRepository extends JpaRepository<GuardianHandoverResource, Long> {

    boolean existsByRequestId(String requestId);
}
