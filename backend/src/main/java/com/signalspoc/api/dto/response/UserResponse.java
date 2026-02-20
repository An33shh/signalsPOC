package com.signalspoc.api.dto.response;

import com.signalspoc.domain.entity.User;
import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String externalId;
    private ConnectorType sourceSystem;
    private String name;
    private String email;
    private Boolean isActive;
    private LocalDateTime syncedAt;
    private LocalDateTime lastSyncedAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .externalId(user.getExternalId())
                .sourceSystem(user.getSourceSystem())
                .name(user.getName())
                .email(user.getEmail())
                .isActive(user.getIsActive())
                .syncedAt(user.getSyncedAt())
                .lastSyncedAt(user.getLastSyncedAt())
                .build();
    }
}
