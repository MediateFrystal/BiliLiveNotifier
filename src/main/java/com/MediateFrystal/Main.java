package com.MediateFrystal;

import java.util.*;
import java.util.concurrent.*;

public class Main {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static List<String> roomIDs;
    private static ConfigLoader config;
    public static final String VERSION = "1.3.3";

    public static void main(String[] args) {
        LogUtil.printDivider(VERSION);
        LogUtil.sys("BiliLiveNotifier v" + VERSION + " 正在启动...");

        config = new ConfigLoader();
        roomIDs = config.getRoomIDs();

//        LogUtil.warn("test warn");
//        LogUtil.err("test err");
//        LogUtil.push("test push");
//        LogUtil.check("test check");
//        LogUtil.live("test live");

        LogUtil.cleanOldLogs(config.getMaxHistoryDays());
        NotificationManager.runStartupTests(config);
        setupDailyTasks();

        Map<String, Boolean> lastStatus = new HashMap<>();
        for (String roomID : roomIDs) {
            lastStatus.put(roomID.trim(), false);
        }

        scheduler.scheduleAtFixedRate(() -> {
            for (String roomID : roomIDs) {
                try {
                    LiveStatusChecker.process(roomID, lastStatus, config);
                } catch (Exception e) {
                    LogUtil.err("检查任务执行异常 [" + roomID + "]: " + e.getMessage());
                }
            }
        }, 0, config.getRetryIntervalSeconds(), TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LogUtil.sys("程序正在关闭...");
            scheduler.shutdown();
        }));
    }

    private static void setupDailyTasks() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                LogUtil.sys("正在执行例行日志清理...");
                LogUtil.cleanOldLogs(config.getMaxHistoryDays());
            } catch (Exception e) {
                LogUtil.err("自动清理日志时出现异常: " + e.getMessage());
            }
        }, 24, 24, TimeUnit.HOURS);
    }
}