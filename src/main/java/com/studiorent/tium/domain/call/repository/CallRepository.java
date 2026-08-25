package com.studiorent.tium.domain.call.repository;

import com.studiorent.tium.domain.call.entity.Call;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallRepository extends JpaRepository<Call, Long> {
}
