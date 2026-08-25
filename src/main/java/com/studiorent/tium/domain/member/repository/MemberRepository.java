package com.studiorent.tium.domain.member.repository;

import com.studiorent.tium.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
