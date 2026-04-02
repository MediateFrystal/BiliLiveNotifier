package com.MediateFrystal;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class NotificationManager {
    public static void runStartupTests(ConfigLoader config) {
        if (config.isEmailEnable() && config.isEmailTestOnStartup()) {
            EmailSender.test(new ArrayList<>(config.getEmailList()), config.getUserInputTimeoutSeconds());
        }
        if (config.isBarkEnable() && config.isBarkTestOnStartup()) {
            LogUtil.push("正在发送 Bark 启动测试...");
            BarkSender.send(config.getBarkUrl(), "BiliLiveNotifier",
                    "版本: " + Main.VERSION + "\n已缓存主播数: " + CacheManager.getCacheSize() +
                            "\n直播间数量: " + config.getRoomIDs().size(), null, null, null);
        }
    }

    /**
     * 统一发送开播通知
     */
    public static void sendStartNotification(LiveData data, ConfigLoader config) {
        String roomID = data.getRoomID();
        String uid = String.valueOf(data.getUid());

        String userName = CacheManager.getProperty(uid + ".name", "房间 " + roomID);
        String userFace = CacheManager.getProperty(uid + ".face", "");

        LogUtil.live("检测到房间 [" + roomID + "] 主播 [" + userName + "] 开播！标题: " + data.getTitle());

        if (config.isBarkEnable()) {
            LogUtil.push("正在推送 Bark 开播提醒...");
            String barkTitle = "【" + userName + "】开播啦！";
            String barkContent = data.getTitle();
            BarkSender.send(config.getBarkUrl(), barkTitle, barkContent, roomID, data.getUserCover(), userFace);
        }

        if (config.isEmailEnable()) {
            CompletableFuture.runAsync(() -> {
                try {
                    EmailSender.send(new ArrayList<>(config.getEmailList()), data, userName, userFace);
                    LogUtil.push("开播邮件发送成功。");
                } catch (Exception e) {
                    LogUtil.err("邮件发送失败: " + e.getMessage());
                }
            });
        }
    }

    /**
     * 统一发送下播通知
     * @param lastData 传入 lastData 以保证数据的准确性
     */
    public static void sendEndNotification(LiveData lastData, String duration, ConfigLoader config) {
        String roomID = lastData.getRoomID();
        String uid = String.valueOf(lastData.getUid());

        String userName = CacheManager.getProperty(uid + ".name", "房间 " + roomID);
        String userFace = CacheManager.getProperty(uid + ".face", "");

        LogUtil.live("检测到房间 [" + roomID + "] 主播 [" + userName + "] 下播。时长: " + duration);

        if (config.isBarkEnable() && config.isBarkPushOnEnd()) {
            LogUtil.push("正在推送 Bark 下播提醒...");
            String barkTitle = "【" + userName + "】下播了";
            String barkContent = "直播时长：" + duration;
            BarkSender.send(config.getBarkUrl(), barkTitle, barkContent, roomID, null, userFace);
        }

        if (config.isEmailEnable() && config.isEmailPushOnEnd()) {
            CompletableFuture.runAsync(() -> {
                try {
                    EmailSender.send(new ArrayList<>(config.getEmailList()), lastData, userName, userFace);
                    LogUtil.push("开播邮件发送成功。");
                } catch (Exception e) {
                    LogUtil.err("邮件发送失败: " + e.getMessage());
                }
            });
        }
    }
}
