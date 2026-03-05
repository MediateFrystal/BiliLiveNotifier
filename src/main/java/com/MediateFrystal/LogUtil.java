package com.MediateFrystal;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LogUtil {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_DIR = "logs";

    // 日志级别枚举
    public enum Level {
        INFO(1), WARN(2), ERROR(3), LIVE(4);
        private final int priority;
        Level(int priority) { this.priority = priority; }
        public int getPriority() { return priority; }
    }

    // 默认日志级别开关
    private static Level consoleLevel = Level.INFO;
    private static Level fileLevel = Level.ERROR;
    private static boolean logLiveToFile = true;

    public static void setLogLiveToFile(boolean logLiveToFile) {
        LogUtil.logLiveToFile = logLiveToFile;
    }

    public static void setConsoleLevel(Level level) {
        consoleLevel = level;
    }

    public static void setFileLevel(Level level) {
        fileLevel = level;
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void err(String message) {
        log(Level.ERROR, message);
    }

    public static void warn(String message) {
        log(Level.WARN, message);
    }

    public static void live(String message) {
        log(Level.LIVE, message);
    }

    private static void log(Level level, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logMessage = "[" + timestamp + "] [" + level + "] " + message;
        // 控制台输出
        if (level.getPriority() >= consoleLevel.getPriority()) {
            System.out.println(logMessage);
        }
        // 文件输出
        if (level.getPriority() >= fileLevel.getPriority() && logLiveToFile) {
            writeToFile(logMessage);
        }
    }
    /**
     * 清理指定天数之前的日志文件
     * @param daysBefore 保留多少天内的日志
     */
    public static void cleanOldLogs(int daysBefore) {
        File logDir = new File(LOG_DIR);
        if (!logDir.exists() || !logDir.isDirectory()) {
            return;
        }

        File[] files = logDir.listFiles((dir, name) -> name.endsWith(".log"));
        if (files == null) return;

        long thresholdTime = System.currentTimeMillis() - (daysBefore * 24L * 60 * 60 * 1000);
        int deleteCount = 0;

        for (File file : files) {
            if (file.lastModified() < thresholdTime) {
                if (file.delete()) {
                    deleteCount++;
                }
            }
        }

        if (deleteCount > 0) {
            info("日志清理完成，共删除 " + deleteCount + " 个过期日志文件。");
        }
    }
    private static void writeToFile(String message) {
        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        String fileName = LOG_DIR + "/" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".log";
        try (FileWriter fw = new FileWriter(fileName, true)) {
            fw.write(message + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("[" + LocalDateTime.now().format(formatter) + "] [ERROR] 无法写入日志文件: " + e.getMessage());
        }
    }
}