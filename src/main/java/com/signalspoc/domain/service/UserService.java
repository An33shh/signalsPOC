package com.signalspoc.domain.service;

import com.signalspoc.connector.model.ConnectorUser;
import com.signalspoc.domain.entity.User;
import com.signalspoc.domain.repository.UserRepository;
import com.signalspoc.shared.exception.Exceptions.ResourceNotFoundException;
import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByExternalId(String externalId, ConnectorType sourceSystem) {
        return userRepository.findByExternalIdAndSourceSystem(externalId, sourceSystem);
    }

    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<User> findBySourceSystem(ConnectorType sourceSystem, Pageable pageable) {
        return userRepository.findBySourceSystem(sourceSystem, pageable);
    }

    @Transactional
    public UpsertResult<User> upsert(ConnectorUser connectorUser) {
        Optional<User> existing = userRepository.findByExternalIdAndSourceSystem(
                connectorUser.getExternalId(),
                connectorUser.getSourceSystem());

        if (existing.isPresent()) {
            User user = existing.get();
            updateUserFields(user, connectorUser);
            User saved = userRepository.save(user);
            log.debug("Updated user: {} ({})", saved.getName(), saved.getExternalId());
            return new UpsertResult<>(saved, false);
        } else {
            User user = createUser(connectorUser);
            User saved = userRepository.save(user);
            log.debug("Created user: {} ({})", saved.getName(), saved.getExternalId());
            return new UpsertResult<>(saved, true);
        }
    }

    @Transactional
    public List<UpsertResult<User>> upsertAll(List<ConnectorUser> connectorUsers) {
        return connectorUsers.stream()
                .map(this::upsert)
                .toList();
    }

    private User createUser(ConnectorUser connectorUser) {
        return User.builder()
                .externalId(connectorUser.getExternalId())
                .sourceSystem(connectorUser.getSourceSystem())
                .name(connectorUser.getName())
                .email(connectorUser.getEmail())
                .isActive(connectorUser.getIsActive())
                .build();
    }

    private void updateUserFields(User user, ConnectorUser connectorUser) {
        user.setName(connectorUser.getName());
        user.setEmail(connectorUser.getEmail());
        user.setIsActive(connectorUser.getIsActive());
    }

    public record UpsertResult<T>(T entity, boolean created) {}
}
