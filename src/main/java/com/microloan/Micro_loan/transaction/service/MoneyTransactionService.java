package com.microloan.Micro_loan.transaction.service;

import com.microloan.Micro_loan.balance.service.BalanceSnapshotService;
import com.microloan.Micro_loan.connection.entity.ConnectionRequest.Status;
import com.microloan.Micro_loan.connection.repository.ConnectionRequestRepository;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction.Origin;
import com.microloan.Micro_loan.transaction.entity.MoneyTransaction.Visibility;
import com.microloan.Micro_loan.transaction.repository.MoneyTransactionRepository;
import com.microloan.Micro_loan.user.entity.User;
import com.microloan.Micro_loan.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MoneyTransactionService {

    private final MoneyTransactionRepository transactionRepository;
    private final ConnectionRequestRepository connectionRepository;
    private final UserRepository userRepository;
    private final BalanceSnapshotService balanceSnapshotService;

    public MoneyTransactionService(MoneyTransactionRepository transactionRepository,
                                   ConnectionRequestRepository connectionRepository,
                                   UserRepository userRepository,
                                   BalanceSnapshotService balanceSnapshotService) {
        this.transactionRepository = transactionRepository;
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
        this.balanceSnapshotService = balanceSnapshotService;
    }

    public MoneyTransaction requestMoney(String recipientEmail, String payerEmail, BigDecimal amount,
                                         String currency, String note, boolean incognito) {
        User recipient = findUser(recipientEmail);
        User payer = findUser(payerEmail);
        ensureConnected(recipient, payer);
        return create(payer, recipient, amount, currency, note, incognito, Origin.REQUEST,
                MoneyTransaction.Status.REQUESTED);
    }

    public MoneyTransaction recordOfflinePayment(String payerEmail, String recipientEmail, BigDecimal amount,
                                                 String currency, String note, boolean incognito) {
        User payer = findUser(payerEmail);
        User recipient = findUser(recipientEmail);
        ensureConnected(payer, recipient);
        return create(payer, recipient, amount, currency, note, incognito, Origin.OFFLINE,
                MoneyTransaction.Status.REPORTED_PAID);
    }

    @Transactional(readOnly = true)
    public List<MoneyTransaction> incomingRequests(String email) {
        return transactionRepository.findIncoming(findUser(email).getId(), MoneyTransaction.Status.REQUESTED);
    }

    @Transactional(readOnly = true)
    public List<MoneyTransaction> outgoingRequests(String email) {
        User user = findUser(email);
        // Outgoing requests are those the current user initiated as the recipient (the other person is the payer).
        return transactionRepository.findOutgoing(user.getId(), MoneyTransaction.Status.REQUESTED);
    }

    @Transactional(readOnly = true)
    public List<MoneyTransaction> history(String email) {
        return transactionRepository.findHistory(findUser(email).getId());
    }

    public MoneyTransaction acceptRequest(String email, UUID transactionId) {
        MoneyTransaction transaction = findTransaction(transactionId);
        requireParticipantEmail(transaction.getPayer(), email, "Only the requested payer can accept this request");
        requireStatus(transaction, MoneyTransaction.Status.REQUESTED);
        transaction.setStatus(MoneyTransaction.Status.ACCEPTED);
        return transactionRepository.save(transaction);
    }

    public MoneyTransaction declineRequest(String email, UUID transactionId) {
        MoneyTransaction transaction = findTransaction(transactionId);
        requireParticipantEmail(transaction.getPayer(), email, "Only the requested payer can decline this request");
        requireStatus(transaction, MoneyTransaction.Status.REQUESTED);
        transaction.setStatus(MoneyTransaction.Status.DECLINED);
        return transactionRepository.save(transaction);
    }

    public MoneyTransaction reportPaid(String email, UUID transactionId) {
        MoneyTransaction transaction = findTransaction(transactionId);
        requireParticipantEmail(transaction.getPayer(), email, "Only the payer can report this payment as sent");
        requireStatus(transaction, MoneyTransaction.Status.ACCEPTED);
        transaction.setStatus(MoneyTransaction.Status.REPORTED_PAID);
        return transactionRepository.save(transaction);
    }

    public MoneyTransaction confirm(String email, UUID transactionId) {
        MoneyTransaction transaction = findTransaction(transactionId);
        requireParticipantEmail(transaction.getRecipient(), email, "Only the recipient can confirm receipt");
        requireStatus(transaction, MoneyTransaction.Status.REPORTED_PAID);
        transaction.setStatus(MoneyTransaction.Status.CONFIRMED);
        MoneyTransaction confirmed = transactionRepository.save(transaction);
        if (confirmed.getVisibility() == Visibility.SHARED) {
            transactionRepository.flush();
            balanceSnapshotService.refreshForUser(confirmed.getPayer().getId());
        }
        return confirmed;
    }

    public MoneyTransaction dispute(String email, UUID transactionId) {
        MoneyTransaction transaction = findTransaction(transactionId);
        requireParticipantEmail(transaction.getRecipient(), email, "Only the recipient can dispute this payment");
        requireStatus(transaction, MoneyTransaction.Status.REPORTED_PAID);
        transaction.setStatus(MoneyTransaction.Status.DISPUTED);
        return transactionRepository.save(transaction);
    }

    private MoneyTransaction create(User payer, User recipient, BigDecimal amount, String currency, String note,
                                    boolean incognito, Origin origin, MoneyTransaction.Status status) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive with at most 2 decimal places");
        }
        MoneyTransaction transaction = new MoneyTransaction();
        transaction.setPayer(payer);
        transaction.setRecipient(recipient);
        transaction.setAmount(amount);
        transaction.setCurrency(currency.trim().toUpperCase());
        transaction.setNote(note == null || note.isBlank() ? null : note.trim());
        transaction.setVisibility(incognito ? Visibility.INCOGNITO : Visibility.SHARED);
        transaction.setOrigin(origin);
        transaction.setStatus(status);
        return transactionRepository.save(transaction);
    }

    private void ensureConnected(User first, User second) {
        if (first.getId().equals(second.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A transaction must be between two different users");
        }
        if (!connectionRepository.existsBetweenUsersWithStatus(first.getId(), second.getId(), Status.ACCEPTED)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transactions are only allowed between connected users");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private MoneyTransaction findTransaction(UUID id) {
        return transactionRepository.findWithUsersById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    private static void requireParticipantEmail(User expected, String email, String message) {
        if (!expected.getEmail().equals(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private static void requireStatus(MoneyTransaction transaction, MoneyTransaction.Status status) {
        if (transaction.getStatus() != status) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction is not in the required state");
        }
    }
}
