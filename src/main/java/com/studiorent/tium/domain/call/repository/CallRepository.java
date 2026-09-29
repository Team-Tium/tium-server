package com.studiorent.tium.domain.call.repository;

import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.enums.CallStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CallRepository extends JpaRepository<Call, Long> {

    @Query("select c from Call c left join fetch c.participants where c.id = :id")
    Optional<Call> findWithParticipantsById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Call c left join fetch c.participants where c.id = :id")
    Optional<Call> findWithParticipantsByIdForUpdate(@Param("id") Long id);

    @Query("""
            select c
            from Call c
            join c.participants p
            where p.memberId = :memberId
              and c.status = :status
              and (:cursor is null or c.id < :cursor)
            order by c.id desc
            """)
    List<Call> findRecentCalls(
            @Param("memberId") Long memberId,
            @Param("status") CallStatus status,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
