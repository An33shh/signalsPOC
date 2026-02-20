package com.signalspoc.ai.util;

import com.signalspoc.domain.entity.Task;
import com.signalspoc.connector.github.dto.GitHubPullRequestDto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class AnalysisChecksumUtil {

    private AnalysisChecksumUtil() {}

    public static String computeChecksum(GitHubPullRequestDto pr, Task task) {
        StringBuilder sb = new StringBuilder();

        if (pr != null) {
            sb.append(pr.getTitle()).append('|');
            sb.append(pr.getState()).append('|');
            sb.append(pr.getMerged()).append('|');
            sb.append(pr.getDraft()).append('|');
            sb.append(pr.getUpdatedAt()).append('|');
        }

        if (task != null) {
            sb.append(task.getTitle()).append('|');
            sb.append(task.getStatus()).append('|');
            sb.append(task.getExternalModifiedAt()).append('|');
            if (task.getAssignee() != null) {
                sb.append(task.getAssignee().getName());
            }
        }

        return sha256(sb.toString());
    }

    public static String buildEntityId(GitHubPullRequestDto pr, Task task) {
        String prPart = pr != null ? "PR:" + pr.getNumber() : "PR:?";
        String taskPart = task != null ? "TASK:" + task.getExternalId() : "TASK:?";
        return prPart + "|" + taskPart;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
