package com.signalspoc.api.controller;

import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.domain.service.AlertActionExecutor;
import com.signalspoc.domain.service.SyncAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Alerts", description = "Sync discrepancy alerts and notifications")
public class AlertController {

    private final SyncAlertService alertService;
    private final AlertActionExecutor actionExecutor;

    @GetMapping
    @Operation(summary = "Get unresolved alerts")
    public ResponseEntity<Page<SyncAlert>> getAlerts(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(alertService.getUnresolvedAlerts(pageable));
    }

    @GetMapping("/unread")
    @Operation(summary = "Get unread alerts")
    public ResponseEntity<Page<SyncAlert>> getUnreadAlerts(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(alertService.getUnreadAlerts(pageable));
    }

    @GetMapping("/count")
    @Operation(summary = "Get unread alert count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", alertService.getUnreadCount()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark alert as read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        alertService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve an alert")
    public ResponseEntity<Void> resolveAlert(@PathVariable Long id) {
        alertService.resolve(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve and execute alert action")
    public ResponseEntity<Map<String, Object>> approveAlert(@PathVariable Long id) {
        AlertActionExecutor.ActionResult result = actionExecutor.executeAction(id);
        return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "description", result.getDescription() != null ? result.getDescription() : "",
                "actionTaken", result.getActionTaken() != null ? result.getActionTaken() : ""
        ));
    }
}
