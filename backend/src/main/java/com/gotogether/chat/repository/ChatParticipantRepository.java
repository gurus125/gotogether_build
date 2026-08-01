package com.gotogether.chat.repository;

import com.gotogether.chat.entity.ChatParticipant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, UUID> {

    Optional<ChatParticipant> findByChatRoomIdAndUserId(UUID chatRoomId, UUID userId);

    boolean existsByChatRoomIdAndUserId(UUID chatRoomId, UUID userId);

    List<ChatParticipant> findByUserId(UUID userId);

    List<ChatParticipant> findByChatRoomId(UUID chatRoomId);
}
