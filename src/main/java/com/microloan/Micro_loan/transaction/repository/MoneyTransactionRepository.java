package com.microloan.Micro_loan.transaction.repository;

import com.microloan.Micro_loan.transaction.entity.MoneyTransaction;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction.Status;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction.Visibility;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoneyTransactionRepository extends JpaRepository<MoneyTransaction, UUID> {

    @Query("select t from MoneyTransaction t join fetch t.payer join fetch t.recipient " +
            "where t.payer.id = :userId and t.status = :status order by t.createdAt desc")
    List<MoneyTransaction> findIncoming(@Param("userId") UUID userId, @Param("status") Status status);

    @Query("select t from MoneyTransaction t join fetch t.payer join fetch t.recipient " +
            "where t.recipient.id = :userId and t.status = :status order by t.createdAt desc")
    List<MoneyTransaction> findOutgoing(@Param("userId") UUID userId, @Param("status") Status status);

    @Query("select t from MoneyTransaction t join fetch t.payer join fetch t.recipient " +
            "where (t.payer.id = :userId or t.recipient.id = :userId) order by t.createdAt desc")
    List<MoneyTransaction> findHistory(@Param("userId") UUID userId);

    @Query("select t from MoneyTransaction t join fetch t.payer join fetch t.recipient where t.id = :id")
    Optional<MoneyTransaction> findWithUsersById(@Param("id") UUID id);

    @Query("select t from MoneyTransaction t join fetch t.payer join fetch t.recipient " +
            "where t.status = :status and t.visibility = :visibility " +
            "and t.payer.id in :userIds and t.recipient.id in :userIds " +
            "order by t.createdAt asc")
    List<MoneyTransaction> findNetworkTransactions(@Param("userIds") Set<UUID> userIds,
                                                   @Param("status") Status status,
                                                   @Param("visibility") Visibility visibility);
}
