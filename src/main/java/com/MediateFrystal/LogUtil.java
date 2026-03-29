package com.MediateFrystal;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

public class LogUtil {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_DIR = "logs";

    public enum Tag {
        SYSTEM,  // 系统生命周期（启动、配置、关闭）
        CHECK,   // 轮询心跳（正在检查房间...）
        LIVE,    // 核心业务（开播、下播）
        PUSH,    // 推送结果（Bark、邮件）
        WARN,    // 非致命异常（重试、网络抖动）
        ERROR,   // 致命错误
        ALL      // 特殊标记：开启全部
    }

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String PURPLE = "\u001B[35m";

    // 当前启用的标签集合
    private static Set<Tag> consoleTags = EnumSet.of(Tag.SYSTEM, Tag.LIVE, Tag.ERROR);
    private static Set<Tag> fileTags = EnumSet.of(Tag.ALL);

    public static void setConsoleTags(String config) {
        consoleTags = parseTags(config);
    }

    public static void setFileTags(String config) {
        fileTags = parseTags(config);
    }

    private static Set<Tag> parseTags(String config) {
        if (config == null || config.equalsIgnoreCase("ALL")) return EnumSet.of(Tag.ALL);
        try {
            Set<Tag> set = EnumSet.noneOf(Tag.class);
            for (String s : config.split(",")) {
                set.add(Tag.valueOf(s.trim().toUpperCase()));
            }
            return set;
        } catch (Exception e) {
            System.err.println("日志配置解析失败，回退到默认设置: " + e.getMessage());
            return EnumSet.of(Tag.SYSTEM, Tag.LIVE, Tag.PUSH, Tag.WARN, Tag.ERROR);
        }
    }

    public static void sys(String msg) { log(Tag.SYSTEM, msg); }
    public static void check(String msg) { log(Tag.CHECK, msg); }
    public static void live(String msg) { log(Tag.LIVE, msg); }
    public static void push(String msg) { log(Tag.PUSH, msg); }
    public static void warn(String msg) { log(Tag.WARN, msg); }
    public static void err(String msg) { log(Tag.ERROR, msg); }

    public static void printDivider(String version) {
        String divider = "\n" + "=".repeat(20) + " Session Started: " + LocalDateTime.now().format(formatter) + " (v" + version + ") " + "=".repeat(20);
        System.out.println(PURPLE + divider + RESET);
        writeToFile(divider);
    }

    private static void log(Tag tag, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String plainMsg = String.format("[%s] [%s] %s", timestamp, tag, message);

        // 控制台输出（带颜色）
        if (consoleTags.contains(Tag.ALL) || consoleTags.contains(tag)) {
            System.out.println(getColorByTag(tag) + plainMsg + RESET);
        }

        // 文件输出（不带颜色）
        if (fileTags.contains(Tag.ALL) || fileTags.contains(tag)) {
            writeToFile(plainMsg);
        }
    }

    private static String getColorByTag(Tag tag) {
        return switch (tag) {
            case SYSTEM -> BLUE;
            case LIVE -> GREEN;
            case CHECK -> RESET;
            case PUSH -> CYAN;
            case WARN -> YELLOW;
            case ERROR -> RED;
            default -> RESET;
        };
    }

    private static void writeToFile(String message) {
        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) logDir.mkdirs();
        String fileName = LOG_DIR + "/" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".log";
        try (FileWriter fw = new FileWriter(fileName, true)) {
            fw.write(message + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("无法写入日志文件: " + e.getMessage());
        }
    }

    /**
     * 清理指定天数之前的日志文件
     * @param daysBefore 保留多少天内的日志
     */
    public static void cleanOldLogs(int daysBefore) {
        File logDir = new File(LOG_DIR);
        if (!logDir.exists() || !logDir.isDirectory()) return;

        File[] files = logDir.listFiles((dir, name) -> name.endsWith(".log"));
        if (files == null) return;

        long threshold = System.currentTimeMillis() - (daysBefore * 24L * 60 * 60 * 1000);
        int deleteCount = 0;

        for (File file : files) {
            if (file.lastModified() < threshold && file.delete()) deleteCount++;
        }

        if (deleteCount > 0) sys("日志清理完成，共删除 " + deleteCount + " 个过期日志文件。");
    }
}