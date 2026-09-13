package com.studiorent.tium.domain.call.repository;

import com.studiorent.tium.domain.call.entity.Call;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CallRepository extends JpaRepository<Call, Long> {

    @Query("select c from Call c left join fetch c.participants where c.id = :id")
    Optional<Call> findWithParticipantsById(@Param("id") Long id);
}
