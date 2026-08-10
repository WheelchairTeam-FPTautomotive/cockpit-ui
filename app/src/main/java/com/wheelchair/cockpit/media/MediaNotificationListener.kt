package com.wheelchair.cockpit.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Enabled notification listener lets [MediaControllerRepository] call
 * MediaSessionManager.getActiveSessions(ComponentName) on Studio installs
 * that cannot hold privileged MEDIA_CONTENT_CONTROL (no /system remount).
 */
class MediaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "Notification listener connected — cross-app media sessions available")
        MediaControllerRepository.requestRefreshFromListener()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Media sessions are discovered via MediaSessionManager; no NLS parsing needed.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    companion object {
        private const val TAG = "MediaNotifListener"
    }
}
