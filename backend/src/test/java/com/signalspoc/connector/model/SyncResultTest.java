package com.signalspoc.connector.model;

import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.SyncStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncResultTest {

    @Test
    void empty_createsResultWithZeroCountersAndSuccessStatus() {
        SyncResult result = SyncResult.empty(ConnectorType.ASANA);

        assertThat(result.getConnectorType()).isEqualTo(ConnectorType.ASANA);
        assertThat(result.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.getProjectsCreated()).isZero();
        assertThat(result.getProjectsUpdated()).isZero();
        assertThat(result.getTasksCreated()).isZero();
        assertThat(result.getTasksUpdated()).isZero();
        assertThat(result.getUsersCreated()).isZero();
        assertThat(result.getUsersUpdated()).isZero();
        assertThat(result.getCommentsCreated()).isZero();
        assertThat(result.getCommentsUpdated()).isZero();
        assertThat(result.getSyncStartTime()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void merge_accumulatesAllCounters() {
        SyncResult base = SyncResult.builder()
                .connectorType(ConnectorType.ASANA)
                .projectsCreated(1).projectsUpdated(2)
                .tasksCreated(3).tasksUpdated(4)
                .usersCreated(5).usersUpdated(6)
                .commentsCreated(7).commentsUpdated(8)
                .build();

        SyncResult other = SyncResult.builder()
                .connectorType(ConnectorType.ASANA)
                .projectsCreated(10).projectsUpdated(20)
                .tasksCreated(30).tasksUpdated(40)
                .usersCreated(50).usersUpdated(60)
                .commentsCreated(70).commentsUpdated(80)
                .build();

        SyncResult merged = base.merge(other);

        assertThat(merged).isSameAs(base);
        assertThat(merged.getProjectsCreated()).isEqualTo(11);
        assertThat(merged.getProjectsUpdated()).isEqualTo(22);
        assertThat(merged.getTasksCreated()).isEqualTo(33);
        assertThat(merged.getTasksUpdated()).isEqualTo(44);
        assertThat(merged.getUsersCreated()).isEqualTo(55);
        assertThat(merged.getUsersUpdated()).isEqualTo(66);
        assertThat(merged.getCommentsCreated()).isEqualTo(77);
        assertThat(merged.getCommentsUpdated()).isEqualTo(88);
    }

    @Test
    void merge_withEmptyOther_isIdempotent() {
        SyncResult base = SyncResult.empty(ConnectorType.LINEAR);
        base.setTasksCreated(5);
        base.setUsersUpdated(3);

        base.merge(SyncResult.empty(ConnectorType.LINEAR));

        assertThat(base.getTasksCreated()).isEqualTo(5);
        assertThat(base.getUsersUpdated()).isEqualTo(3);
    }

    @Test
    void merge_canChain() {
        SyncResult base = SyncResult.empty(ConnectorType.ASANA);
        SyncResult a = SyncResult.builder().tasksCreated(2).build();
        SyncResult b = SyncResult.builder().tasksCreated(3).build();

        base.merge(a).merge(b);

        assertThat(base.getTasksCreated()).isEqualTo(5);
    }
}
