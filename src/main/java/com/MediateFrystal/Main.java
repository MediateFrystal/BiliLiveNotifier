package com.MediateFrystal;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static List<String> liveIDs;
    private static boolean emailEnable;
    public static Set<String> emailList = new HashSet<>();
    private static boolean testMailOnStartup;
    private static int retryIntervalSeconds;
    private static int userInputTimeoutSeconds;
    private static int maxHistoryDays;
    // 用于记录正在直播的房间信息
    private static final Map<String, LiveData> activeLives = new ConcurrentHashMap<>();
    private static ConfigLoader config;
    private static final String VERSION = "1.3.0";

    public static void main(String[] args) throws GeneralSecurityException {
        LogUtil.live("版本："+ VERSION +" 正在启动...");

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

        if (testMailOnStartup && emailEnable) {
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
                CompletableFuture.runAsync(() -> {
                    try {
                        EmailSender.send(new ArrayList<>(emailList), testData);
                    } catch (Exception e) {
                        LogUtil.err("邮件发送异步任务失败: " + e.getMessage());
                    }
                });
                LogUtil.info(">>>>>>测试邮件发送完成<<<<<<\n []~(￣▽￣)~*\n");
            }
        } if (!emailEnable) {
            LogUtil.info("邮件推送开关已关闭，跳过启动测试邮件。");
        } else {
            LogUtil.info("已取消发送测试邮件... ~ （*＾-＾*）\n");
        }

        if (config.isBarkEnable() && config.isBarkTestOnStartup()) {
            LogUtil.info("正在发送 Bark 启动测试...");
            BarkSender.send(
                    config.getBarkUrl(),
                    "BiliLiveNotifier",
                    "当前版本: "+ VERSION + "\n监控直播间数量: " + liveIDs.size(),
                    null,
                    null
            );
        }

        Map<String, Boolean> lastStatus = new HashMap<>();

        for (String liveID : liveIDs) {
            lastStatus.put(liveID.trim(), false);
        }

        scheduler.scheduleAtFixedRate(() -> checkAndNotify(lastStatus), 0, retryIntervalSeconds, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LogUtil.info("关闭中...");
            scheduler.shutdown();
        }));
    }

    private static void checkAndNotify(Map<String, Boolean> lastStatus) {
        for (String liveID : liveIDs) {
            liveID = liveID.trim();
            try {
                LiveData data = LiveStatusChecker.getLiveData(config.getApiUrl(), liveID);
                boolean isLive = Objects.requireNonNull(data).getLiveStatus() == 1;
                boolean wasLive = lastStatus.getOrDefault(liveID, false);

                // 检查直播状态
                if (!wasLive && isLive) {
                    LogUtil.live("检测到房间 [" + liveID + "] 开播！标题: " + data.getTitle());
                    activeLives.put(liveID, data);
                    if (config.isBarkEnable()) {
                        BarkSender.send(
                                config.getBarkUrl(),
                                "【开播】" + data.getTitle(),
                                "主播 " + data.getUid() + " 开启了直播",
                                liveID,               // 用于跳转
                                data.getUserCover()   // 用于显示图片
                        );
                    }
                    if (emailEnable) {
                        LogUtil.live("准备发送邮件..." + "\n在 " + userInputTimeoutSeconds + " 秒内按下 回车键 跳过本房间邮件的发送！");
                        // 等待用户输入
                        if (waitForUserInput()) {
                            LogUtil.live("发送邮件已取消... ~ （*＾-＾*）\n");
                        } else {
                            // 发送邮件给所有收件人
                            LogUtil.live(">>>>>>邮件发送中<<<<<<");
                            CompletableFuture.runAsync(() -> {
                                try {
                                    EmailSender.send(new ArrayList<>(emailList), data);
                                } catch (Exception e) {
                                    LogUtil.err("邮件发送异步任务失败: " + e.getMessage());
                                }
                            });
                            LogUtil.live(">>>>>>邮件发送完成<<<<<<\n");
                        }
                    } else {
                        LogUtil.info("邮件推送已关闭。");
                    }
                } else if (wasLive && !isLive) {
                    LiveData lastData = activeLives.remove(liveID);
                    String durationStr = formatDuration(lastData.getStartTime());

                    String endMsg = "房间 [" + liveID + "] 直播结束。本次直播时长: " + durationStr;
                    LogUtil.live(endMsg);

                    if (config.isBarkEnable() && config.isBarkPushOnEnd()) {
                        if (config.isBarkEnable() && config.isBarkPushOnEnd()) {
                            BarkSender.send(
                                    config.getBarkUrl(),
                                    "【下播】直播已结束",
                                    "本次直播时长：" + durationStr,
                                    liveID,
                                    null                  // 下播通常不需要封面图
                            );
                        }
                    }
                } else if (wasLive && isLive) {
                    LogUtil.info("房间 [" + liveID + "] 正在直播。");
                } else {
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
        config = new ConfigLoader();
        LogUtil.setConsoleLevel(config.getConsoleLevel());
        LogUtil.setFileLevel(config.getFileLevel());
        LogUtil.setLogLiveToFile(config.isLogToFile());

        liveIDs = config.getLiveIDs();
        emailEnable = config.isEmailEnable();
        emailList = config.getEmailList();
        testMailOnStartup = config.isTestMailOnStartup();
        retryIntervalSeconds = config.getRetryIntervalSeconds();
        userInputTimeoutSeconds = config.getUserInputTimeoutSeconds();
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
    private static String formatDuration(long startMillis) {
        long minutes = (System.currentTimeMillis() - startMillis) / (1000 * 60);
        if (minutes < 60) return minutes + " 分钟";
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }
}
