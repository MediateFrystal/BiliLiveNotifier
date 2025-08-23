package com.MediateFrystal;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static Set<String> emailList = new HashSet<>();
    private static List<String> liveIDs;
    private static int retryIntervalSeconds;
    private static int userInputTimeoutSeconds;
    private static boolean sendTestMailOnStartup;
    private static String apiUrl;

    public static void main(String[] args) throws GeneralSecurityException {
        LogUtil.info("版本：v1.2.0 正在启动...");

        loadConfig();

        if (sendTestMailOnStartup) {
            LogUtil.info("准备发送测试邮件..." +
                "\n在 " + userInputTimeoutSeconds + " 秒内按下 回车键 跳过测试邮件的发送！");

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
                    LogUtil.live("房间 [" + liveID + "] 的直播状态为 1 并且上次检查时未开播，准备发送邮件..." +
                            "\n在 " + userInputTimeoutSeconds + " 秒内按下 回车键 跳过本房间邮件的发送！");

                    // 等待用户输入
                    if (waitForUserInput()) {
                        LogUtil.live("发送邮件已取消... ~ （*＾-＾*）\n");
                    } else {
                        // 发送邮件给所有收件人
                        LogUtil.live(">>>>>>邮件发送中<<<<<<");
                        EmailSender.sendEmails(new ArrayList<>(emailList), data);
                        LogUtil.live(">>>>>>邮件发送完成<<<<<<\n");
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
        retryIntervalSeconds = config.getRetryIntervalSeconds();
        userInputTimeoutSeconds = config.getUserInputTimeoutSeconds();
        sendTestMailOnStartup = config.isSendTestMailOnStartup();
        apiUrl = config.getApiUrl();
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
