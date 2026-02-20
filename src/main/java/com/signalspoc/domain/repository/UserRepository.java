package com.signalspoc.domain.repository;

import com.signalspoc.domain.entity.User;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByExternalIdAndSourceSystem(String externalId, ConnectorType sourceSystem);

    List<User> findBySourceSystem(ConnectorType sourceSystem);

    Page<User> findBySourceSystem(ConnectorType sourceSystem, Pageable pageable);

    boolean existsByExternalIdAndSourceSystem(String externalId, ConnectorType sourceSystem);
}
