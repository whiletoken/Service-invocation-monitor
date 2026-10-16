package com.caijy.plugin.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;

/**
 * @Description 右下角通知提示，适配现代与最新 IntelliJ 平台
 */
public class MessageUtil {
    public static final String GROUP_ID = "Service Invocation Monitor";

    public static void info(String message) {
        notify(message, NotificationType.INFORMATION);
    }

    public static void error(String message) {
        notify(message, NotificationType.ERROR);
    }

    public static void warn(String message) {
        notify(message, NotificationType.WARNING);
    }

    private static void notify(String message, NotificationType type) {
        try {
            NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID);
            if (group != null) {
                group.createNotification(message, type).notify(null);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            new Notification(GROUP_ID, "Service Invocation Monitor", message, type).notify(null);
        } catch (Throwable ignored) {
        }
    }
}
