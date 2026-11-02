package com.microloan.Micro_loan.connection.repository;

import com.microloan.Micro_loan.connection.entity.ConnectionRequest;
import com.microloan.Micro_loan.connection.entity.ConnectionRequest.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConnectionRequestRepository extends JpaRepository<ConnectionRequest, UUID> {

    boolean existsByRequester_IdAndRecipient_IdAndStatus(UUID requesterId, UUID recipientId, Status status);

    @Query("select (count(r) > 0) from ConnectionRequest r where r.status = :status and " +
            "((r.requester.id = :firstId and r.recipient.id = :secondId) or " +
            "(r.requester.id = :secondId and r.recipient.id = :firstId))")
    boolean existsBetweenUsersWithStatus(@Param("firstId") UUID firstId,
                                         @Param("secondId") UUID secondId,
                                         @Param("status") Status status);

    @Query("select r from ConnectionRequest r join fetch r.requester join fetch r.recipient " +
            "where r.recipient.id = :userId and r.status = :status order by r.createdAt desc")
    List<ConnectionRequest> findForRecipient(@Param("userId") UUID userId, @Param("status") Status status);

    @Query("select r from ConnectionRequest r join fetch r.requester join fetch r.recipient " +
            "where r.requester.id = :userId and r.status = :status order by r.createdAt desc")
    List<ConnectionRequest> findForRequester(@Param("userId") UUID userId, @Param("status") Status status);

    @Query("select r from ConnectionRequest r join fetch r.requester join fetch r.recipient " +
            "where r.id = :requestId")
    Optional<ConnectionRequest> findWithUsersById(@Param("requestId") UUID requestId);

    @Query("select r from ConnectionRequest r join fetch r.requester join fetch r.recipient " +
            "where r.status = :status and (r.requester.id = :userId or r.recipient.id = :userId) " +
            "order by r.updatedAt desc")
    List<ConnectionRequest> findConnections(@Param("userId") UUID userId, @Param("status") Status status);

    @Query("select r from ConnectionRequest r where r.status = :status and " +
            "((r.requester.id = :firstId and r.recipient.id = :secondId) or " +
            "(r.requester.id = :secondId and r.recipient.id = :firstId))")
    Optional<ConnectionRequest> findBetweenUsersWithStatus(@Param("firstId") UUID firstId,
                                                           @Param("secondId") UUID secondId,
                                                           @Param("status") Status status);

    @Query(value = "WITH RECURSIVE network(user_id) AS (" +
            "SELECT CAST(:userId AS UUID) " +
            "UNION " +
            "SELECT CASE WHEN c.requester_id = n.user_id THEN c.recipient_id ELSE c.requester_id END " +
            "FROM connection_request c JOIN network n " +
            "ON c.requester_id = n.user_id OR c.recipient_id = n.user_id " +
            "WHERE c.status = 'ACCEPTED'" +
            ") SELECT user_id FROM network", nativeQuery = true)
    List<UUID> findConnectedUserIds(@Param("userId") UUID userId);
}
