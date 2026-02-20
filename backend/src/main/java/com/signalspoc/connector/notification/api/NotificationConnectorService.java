package com.signalspoc.connector.notification.api;

import com.signalspoc.connector.api.ConnectorService;

/**
 * Communication tools (Slack, Teams, Discord). Implemented when notification module is built.
 */
public interface NotificationConnectorService extends ConnectorService {
    // void sendAlert(SyncAlert alert, String channelId);
}
