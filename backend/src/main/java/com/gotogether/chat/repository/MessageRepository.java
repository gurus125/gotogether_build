package com.gotogether.chat.repository;

import com.gotogether.chat.entity.Message;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findFirstByChatRoomIdOrderBySequenceNumberDesc(UUID chatRoomId);

    List<Message> findByChatRoomIdOrderBySequenceNumberDesc(UUID chatRoomId, PageRequest pageRequest);

    List<Message> findByChatRoomIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(UUID chatRoomId, long beforeSequence, PageRequest pageRequest);

    long countByChatRoomIdAndSequenceNumberGreaterThan(UUID chatRoomId, long sequenceNumber);
}
