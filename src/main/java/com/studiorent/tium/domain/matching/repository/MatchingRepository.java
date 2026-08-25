package com.studiorent.tium.domain.matching.repository;

import com.studiorent.tium.domain.matching.entity.Matching;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingRepository extends JpaRepository<Matching, Long> {
}
