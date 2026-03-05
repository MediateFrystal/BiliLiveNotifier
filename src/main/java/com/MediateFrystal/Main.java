package com.MediateFrystal;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    public static Set<String> emailList = new HashSet<>();
    private static List<String> liveIDs;
    private static boolean emailEnable;
    private static int retryIntervalSeconds;
    private static int userInputTimeoutSeconds;
    private static boolean sendTestMailOnStartup;
    private static String apiUrl;
    private static int maxHistoryDays;

    public static void main(String[] args) throws GeneralSecurityException {
        LogUtil.live("版本：v1.2.1 正在启动...");

        loadConfig();
        LogUtil.cleanOldLogs(maxHistoryDays);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                LogUtil.info("正在执行每日例行日志清理...");
                LogUtil.cleanOldLogs(maxHistoryDays);
            } catch (Exception e) {
                LogUtil.err("自动清理日志时出现异常: " + e.getMessage());
            }
        }, 24, 24, TimeUnit.HOURS);

        if (sendTestMailOnStartup && emailEnable) {
            LogUtil.info("准备发送测试邮件..." + "\n在 " + userInputTimeoutSeconds + " 秒内按下 回车键 跳过测试邮件的发送！");
            // 等待用户输入
            if (waitForUserInput()) {
                LogUtil.info("已取消发送测试邮件... ~ （*＾-＾*）\n");
            } else {
                // 发送测试邮件
                LogUtil.info(">>>>>>测试邮件发送中<<<<<<");
                LiveData testData = new LiveData();
                testData.setRoomID("TEST_ROOM");
                testData.setUid(1234567890L);
                testData.setTitle("这是一封测试邮件");
                testData.setLiveStatus(1);
                EmailSender.sendEmails(new ArrayList<>(emailList), testData);
                LogUtil.info(">>>>>>测试邮件发送完成<<<<<<\n []~(￣▽￣)~*\n");
            }
        } if (!emailEnable) {
            LogUtil.info("邮件推送开关已关闭，跳过启动测试邮件。");
        } else {
            LogUtil.info("已取消发送测试邮件... ~ （*＾-＾*）\n");
        }

        Map<String, Boolean> lastStatus = new HashMap<>();

        for (String liveID : liveIDs) {
            lastStatus.put(liveID.trim(), false);
        }

        scheduler.scheduleAtFixedRate(() -> checkAndSendEmails(lastStatus), 0, retryIntervalSeconds, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LogUtil.info("关闭中...");
            scheduler.shutdown();
        }));
    }

    private static void checkAndSendEmails(Map<String, Boolean> lastStatus) {
        for (String liveID : liveIDs) {
            liveID = liveID.trim();
            try {
                LiveData data = LiveStatusChecker.getLiveData(apiUrl, liveID);
                boolean isLive = Objects.requireNonNull(data).getLiveStatus() == 1;
                boolean wasLive = lastStatus.get(liveID);

                // 检查直播状态
                if (!wasLive && isLive) {
                    LogUtil.live("检测到房间 [" + liveID + "] 开播！");
                    if (emailEnable) {
                        LogUtil.live("准备发送邮件..." + "\n在 " + userInputTimeoutSeconds + " 秒内按下 回车键 跳过本房间邮件的发送！");
                        // 等待用户输入
                        if (waitForUserInput()) {
                            LogUtil.live("发送邮件已取消... ~ （*＾-＾*）\n");
                        } else {
                            // 发送邮件给所有收件人
                            LogUtil.live(">>>>>>邮件发送中<<<<<<");
                            EmailSender.sendEmails(new ArrayList<>(emailList), data);
                            LogUtil.live(">>>>>>邮件发送完成<<<<<<\n");
                        }
                    } else {
                        LogUtil.info("邮件推送已关闭，仅记录日志。");
                    }

                } else if (wasLive && !isLive) {
                    LogUtil.live("房间 [" + liveID + "] 的直播状态变为 0，直播结束。");
                } else if (wasLive && isLive) {
                    LogUtil.info("房间 [" + liveID + "] 正在直播。");
                }else {
                    LogUtil.info("房间 [" + liveID + "] 没有直播。");
                }
                lastStatus.put(liveID, isLive);
            } catch (Exception e) {
                LogUtil.err("检查房间 " + liveID + " 时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void loadConfig() {
        ConfigLoader config = new ConfigLoader();
        liveIDs = config.getLiveIDs();
        emailList = config.getEmailList();
        emailEnable = config.isEmailEnable();
        retryIntervalSeconds = config.getRetryIntervalSeconds();
        userInputTimeoutSeconds = config.getUserInputTimeoutSeconds();
        sendTestMailOnStartup = config.isSendTestMailOnStartup();
        apiUrl = config.getApiUrl();
        maxHistoryDays = config.getMaxHistoryDays();
    }

    private static boolean waitForUserInput() {
        long endTime = System.currentTimeMillis() + userInputTimeoutSeconds * 1000L;
        try {
            while (System.currentTimeMillis() < endTime) {
                if (System.in.available() > 0) {
                    new Scanner(System.in).nextLine();
                    return true;
                }
                Thread.sleep(100); // Short sleep to avoid busy-waiting
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return false;
    }
}
