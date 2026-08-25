package com.studiorent.tium.domain.chat.repository;

import com.studiorent.tium.domain.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository extends JpaRepository<Chat, Long> {
}
