package com.MediateFrystal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;


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
    private static Set<Tag> consoleTags = EnumSet.of(Tag.ALL);
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
            return EnumSet.of(Tag.ALL);
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
            case CHECK -> RESET;
            case LIVE -> GREEN;
            case PUSH -> CYAN;
            case WARN -> YELLOW;
            case ERROR -> RED;
            default -> RESET;
        };
    }

    private static void writeToFile(String message) {
        try {
            Path logDirPath = Paths.get(LOG_DIR);
            Files.createDirectories(logDirPath);

            String fileName = LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".log";
            Path filePath = logDirPath.resolve(fileName);

            Files.writeString(
                    filePath,
                    message + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            err("无法写入日志文件: " + e.getMessage());
        }
    }

    /**
     * 清理指定天数之前的日志文件
     * @param daysBefore 保留多少天内的日志
     */
    public static void cleanOldLogs(int daysBefore) {
        Path logDirPath = Paths.get(LOG_DIR);
        if (!Files.exists(logDirPath) || !Files.isDirectory(logDirPath)) return;
        Instant threshold = Instant.now().minus(daysBefore, ChronoUnit.DAYS);
        java.util.concurrent.atomic.AtomicInteger deleteCount = new java.util.concurrent.atomic.AtomicInteger(0);

        try (Stream<Path> files = Files.walk(logDirPath, 1)) {
            files.filter(path -> path.toString().endsWith(".log"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().isBefore(threshold);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            if (Files.deleteIfExists(path)) {
                                deleteCount.incrementAndGet();
                            }
                        } catch (IOException e) {
                            err("删除文件失败: " + path + " -> " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            err("清理日志时发生错误: " + e.getMessage());
        }

        if (deleteCount.get() > 0) {
            sys("日志清理完成，共删除 " + deleteCount.get() + " 个 " + daysBefore + " 天前的过期日志。");
        } else {
            sys("未发现超过 " + daysBefore + " 天的过期日志。");
        }
    }
}