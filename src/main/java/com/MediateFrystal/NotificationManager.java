package com.MediateFrystal;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class NotificationManager {
    /**
     * 按分组执行启动测试：仅对「渠道开启 && testOnStartup=true」的分组发送对应测试
     */
    public static void runStartupTests(ConfigLoader config) {
        for (ConfigLoader.Group group : config.getGroups()) {
            if (group.isEmailEnable() && group.isEmailTestOnStartup()) {
                LogUtil.sys("正在对分组 [" + group.getId() + "] 执行邮件启动测试...");
                EmailSender.test(new ArrayList<>(group.getEmailList()), config.getUserInputTimeoutSeconds());
            }
            if (group.isBarkEnable() && group.isBarkTestOnStartup()) {
                LogUtil.push("正在对分组 [" + group.getId() + "] 发送 Bark 启动测试...");
                BarkSender.send(group.getBarkUrl(), "BiliLiveNotifier",
                        "版本: " + Main.VERSION + "\n分组: " + group.getId() +
                                "\n直播间数量: " + group.getRooms().size(), null, null, null, null);
            }
        }
    }

    /**
     * 统一发送开播通知（仅使用该房间所属分组的推送渠道与接收人）
     */
    public static void sendStartNotification(LiveData data, ConfigLoader config) {
        String roomID = data.getRoomID();
        ConfigLoader.Group group = config.getGroupForRoom(roomID);
        if (group == null) {
            LogUtil.warn("房间 [" + roomID + "] 未归属任何分组，已跳过推送。");
            return;
        }

        String userName = CacheManager.getProperty(roomID + ".name", "");
        String userFace = CacheManager.getProperty(roomID + ".face", "");

        String displayName = userName.isEmpty() ? "房间 " + roomID : userName;
        LogUtil.live("检测到房间 [" + roomID + "] 主播 [" + displayName + "] 开播！标题: " + data.getTitle());

        if (group.isBarkEnable()) {
            LogUtil.push("正在推送 Bark 开播提醒...");
            String barkTitle = "【" + displayName + "】开播啦！";
            String barkContent = data.getTitle();
            // Bark 通知分组：成功获取到主播昵称则按其昵称分组，否则使用默认分组
            String barkGroup = userName.isEmpty() ? null : userName;
            BarkSender.send(group.getBarkUrl(), barkTitle, barkContent, roomID, data.getUserCover(), userFace, barkGroup);
        }

        if (group.isEmailEnable()) {
            CompletableFuture.runAsync(() -> {
                try {
                    EmailSender.send(new ArrayList<>(group.getEmailList()), data, displayName, userFace);
                    LogUtil.push("开播邮件发送成功。");
                } catch (Exception e) {
                    LogUtil.err("邮件发送失败: " + e.getMessage());
                }
            });
        }
    }

    /**
     * 统一发送下播通知（仅使用该房间所属分组的推送渠道与接收人）
     * @param lastData 传入 lastData 以保证数据的准确性
     */
    public static void sendEndNotification(LiveData lastData, String duration, ConfigLoader config) {
        String roomID = lastData.getRoomID();
        ConfigLoader.Group group = config.getGroupForRoom(roomID);
        if (group == null) {
            LogUtil.warn("房间 [" + roomID + "] 未归属任何分组，已跳过推送。");
            return;
        }

        String userName = CacheManager.getProperty(roomID + ".name", "");
        String userFace = CacheManager.getProperty(roomID + ".face", "");

        String displayName = userName.isEmpty() ? "房间 " + roomID : userName;
        LogUtil.live("检测到房间 [" + roomID + "] 主播 [" + displayName + "] 下播。时长: " + duration);

        if (group.isBarkEnable() && group.isBarkPushOnEnd()) {
            LogUtil.push("正在推送 Bark 下播提醒...");
            String barkTitle = "【" + displayName + "】下播了";
            String barkContent = "直播时长：" + duration;
            // Bark 通知分组：成功获取到主播昵称则按其昵称分组，否则使用默认分组
            String barkGroup = userName.isEmpty() ? null : userName;
            BarkSender.send(group.getBarkUrl(), barkTitle, barkContent, roomID, null, userFace, barkGroup);
        }

        if (group.isEmailEnable() && group.isEmailPushOnEnd()) {
            CompletableFuture.runAsync(() -> {
                try {
                    EmailSender.send(new ArrayList<>(group.getEmailList()), lastData, displayName, userFace);
                    LogUtil.push("开播邮件发送成功。");
                } catch (Exception e) {
                    LogUtil.err("邮件发送失败: " + e.getMessage());
                }
            });
        }
    }
}
