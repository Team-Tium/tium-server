package com.studiorent.tium.domain.call.repository;

import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.enums.CallStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface CallRepository extends JpaRepository<Call, Long> {

    @Query("select c from Call c left join fetch c.participants where c.id = :id")
    Optional<Call> findWithParticipantsById(@Param("id") Long id);

    /** Locks the call row so acceptance and terminal transitions serialize until commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Call> findWithLockById(Long id);

    @Query("""
            select count(c)
            from Call c
            join c.participants p
            where c.status = :status
              and p.memberId in :memberIds
            """)
    long countActiveCallsForAnyMember(
            @Param("memberIds") Collection<Long> memberIds,
            @Param("status") CallStatus status
    );
}
