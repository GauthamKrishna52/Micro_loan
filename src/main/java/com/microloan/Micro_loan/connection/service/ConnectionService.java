package com.microloan.Micro_loan.connection.service;

import com.microloan.Micro_loan.connection.entity.ConnectionRequest;
import com.microloan.Micro_loan.connection.entity.ConnectionRequest.Status;
import com.microloan.Micro_loan.connection.repository.ConnectionRequestRepository;
import com.microloan.Micro_loan.user.entity.User;
import com.microloan.Micro_loan.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ConnectionService {

    private final ConnectionRequestRepository requestRepository;
    private final UserRepository userRepository;

    public ConnectionService(ConnectionRequestRepository requestRepository, UserRepository userRepository) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    public ConnectionRequest sendRequest(String requesterEmail, String recipientEmail) {
        User requester = findUser(requesterEmail);
        User recipient = findUser(recipientEmail);
        if (requester.getId().equals(recipient.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot send a connection request to yourself");
        }
        if (requestRepository.existsBetweenUsersWithStatus(requester.getId(), recipient.getId(), Status.ACCEPTED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already connected");
        }
        if (requestRepository.existsBetweenUsersWithStatus(requester.getId(), recipient.getId(), Status.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending connection request already exists");
        }

        ConnectionRequest request = new ConnectionRequest();
        request.setRequester(requester);
        request.setRecipient(recipient);
        return requestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<ConnectionRequest> incoming(String email) {
        User user = findUser(email);
        return requestRepository.findForRecipient(user.getId(), Status.PENDING);
    }

    @Transactional(readOnly = true)
    public List<ConnectionRequest> outgoing(String email) {
        User user = findUser(email);
        return requestRepository.findForRequester(user.getId(), Status.PENDING);
    }

    public ConnectionRequest respond(String email, UUID requestId, boolean accept) {
        User recipient = findUser(email);
        ConnectionRequest request = requestRepository.findWithUsersById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection request not found"));
        if (!request.getRecipient().getId().equals(recipient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can respond to this request");
        }
        if (request.getStatus() != Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Connection request is no longer pending");
        }
        request.setStatus(accept ? Status.ACCEPTED : Status.DECLINED);
        return requestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<ConnectionRequest> connections(String email) {
        User user = findUser(email);
        return requestRepository.findConnections(user.getId(), Status.ACCEPTED);
    }

    public void removeConnection(String email, UUID otherUserId) {
        User user = findUser(email);
        ConnectionRequest connection = requestRepository.findBetweenUsersWithStatus(user.getId(), otherUserId, Status.ACCEPTED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));
        requestRepository.delete(connection);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
