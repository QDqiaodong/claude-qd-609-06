package com.archery.range.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.archery.range.domain.GuardianAuthorization;

import jakarta.persistence.LockModeType;

public interface GuardianAuthorizationRepository extends JpaRepository<GuardianAuthorization, Long> {

    Optional<GuardianAuthorization> findByMinorId(Long minorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GuardianAuthorization a where a.id = :id")
    Optional<GuardianAuthorization> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GuardianAuthorization a where a.minorId = :minorId")
    Optional<GuardianAuthorization> findByMinorIdForUpdate(@Param("minorId") Long minorId);

    boolean existsByMinorId(Long minorId);
}
