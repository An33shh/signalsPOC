package com.signalspoc.ai.model;

import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiActionRecommendation {

    public enum ActionType {
        UPDATE_TASK_STATUS,
        COMPLETE_TASK,
        ADD_COMMENT,
        ADD_PR_COMMENT,
        UPDATE_PR_LABELS,
        APPROVE_PR,
        NO_ACTION,
        MANUAL_REVIEW
    }

    private ActionType actionType;
    private ConnectorType targetPlatform;
    private String targetEntityId;
    private Map<String, String> parameters;
    private String reasoning;
    private double confidence;
}
