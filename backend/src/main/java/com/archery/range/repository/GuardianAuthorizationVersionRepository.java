package com.archery.range.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.archery.range.domain.GuardianAuthorizationVersion;

import jakarta.persistence.LockModeType;

public interface GuardianAuthorizationVersionRepository extends JpaRepository<GuardianAuthorizationVersion, Long> {

    List<GuardianAuthorizationVersion> findByAuthorizationIdOrderByVersionNoDescIdDesc(Long authorizationId);

    List<GuardianAuthorizationVersion> findByMinorIdOrderByVersionNoDescIdDesc(Long minorId);

    Optional<GuardianAuthorizationVersion> findFirstByAuthorizationIdOrderByVersionNoDesc(Long authorizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from GuardianAuthorizationVersion v where v.id = :id")
    Optional<GuardianAuthorizationVersion> findByIdForUpdate(@Param("id") Long id);

    long countByAuthorizationIdAndStatus(Long authorizationId, String status);
}
