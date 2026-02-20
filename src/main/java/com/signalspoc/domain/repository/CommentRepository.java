package com.signalspoc.domain.repository;

import com.signalspoc.domain.entity.Comment;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    Optional<Comment> findByExternalIdAndSourceSystem(String externalId, ConnectorType sourceSystem);

    List<Comment> findBySourceSystem(ConnectorType sourceSystem);

    Page<Comment> findBySourceSystem(ConnectorType sourceSystem, Pageable pageable);

    List<Comment> findByTaskId(Long taskId);

    Page<Comment> findByTaskId(Long taskId, Pageable pageable);

    boolean existsByExternalIdAndSourceSystem(String externalId, ConnectorType sourceSystem);
}
