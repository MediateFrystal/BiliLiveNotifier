package com.MediateFrystal;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogUtil {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    private static void writeToFile(String message) {

        File logDir = new File("logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        String fileName = "logs/" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".log";
        try (FileWriter fw = new FileWriter(fileName, true)) {
            fw.write(message + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("[" + LocalDateTime.now().format(formatter) + "] [ERROR] 无法写入日志文件: " + fileName + e.getMessage());
        }
    }
}