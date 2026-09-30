package com.archery.range.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.archery.range.domain.GuardianHandover;

import jakarta.persistence.LockModeType;

public interface GuardianHandoverRepository extends JpaRepository<GuardianHandover, Long> {

    Optional<GuardianHandover> findByMinorIdAndStageNotOrderByIdDesc(Long minorId, String stage);

    Optional<GuardianHandover> findFirstByMinorIdAndStageNotOrderByIdDesc(Long minorId, String stage);

    List<GuardianHandover> findByMinorIdOrderByIdDesc(Long minorId);

    List<GuardianHandover> findByStageNotOrderByIdDesc(String stage);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from GuardianHandover h where h.id = :id")
    Optional<GuardianHandover> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from GuardianHandover h where h.minorId = :minorId and h.stage <> 'RETURNED' order by h.id desc")
    List<GuardianHandover> findOpenByMinorIdForUpdate(@Param("minorId") Long minorId);

    boolean existsByMinorIdAndStageNot(Long minorId, String stage);

    boolean existsByHandoverNo(String handoverNo);
}
