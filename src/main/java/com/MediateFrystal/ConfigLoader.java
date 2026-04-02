package com.MediateFrystal;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ConfigLoader {
    private final String configFile = "config.properties";
    private final Properties conf;
    private final Map<String, String> defaultProperties = new LinkedHashMap<>();

    private List<String> roomIDs;
    private int retryIntervalSeconds;
    private int userInputTimeoutSeconds;

    private String consoleLevel;
    private String fileLevel;
    private int maxHistoryDays;

    private boolean emailEnable;
    private Set<String> emailList;
    private boolean emailTestOnStartup;
    private boolean emailPushOnEnd;

    private boolean barkEnable;
    private String barkUrl;
    private boolean barkTestOnStartup;
    private boolean barkPushOnEnd;

    private String apiUrl;

    public ConfigLoader() {
        Properties defaultProps = new Properties();
        defaultProperties.forEach(defaultProps::put);
        this.conf = new Properties(defaultProps);

        loadConfig();

        LogUtil.sys("配置加载完成，正在监控 " + roomIDs.size() + " 个直播间" +
                "；检查间隔: " + retryIntervalSeconds + "s" +
                "；日志保留: " + maxHistoryDays + "天" +
                "；邮件推送: " + (emailEnable ? "开启" : "关闭") +
                "；Bark 推送: " + (barkEnable ? "开启" : "关闭"));
    }

    private void initDefaults() {
        defaultProperties.put("roomIDs", "123456,234567");
        defaultProperties.put("apiUrl", "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=");
        defaultProperties.put("retryIntervalSeconds", "30");
        defaultProperties.put("userInputTimeoutSeconds", "5");

        defaultProperties.put("log.console.level", "ALL");
        defaultProperties.put("log.file.level", "SYSTEM,LIVE,PUSH,WARN,ERROR");
        defaultProperties.put("log.maxHistoryDays", "30");

        defaultProperties.put("email.enable", "true");
        defaultProperties.put("email.list", "example1@mail.com,example2@mail.com");
        defaultProperties.put("email.testOnStartup", "true");
        defaultProperties.put("email.pushOnEnd", "true");
        defaultProperties.put("smtp.host", "smtp.qq.com");
        defaultProperties.put("smtp.port", "465");
        defaultProperties.put("smtp.username", "<smtp.username>");
        defaultProperties.put("smtp.password", "<smtp.password>");

        defaultProperties.put("bark.enable", "false");
        defaultProperties.put("bark.url", "https://api.day.app/your_key/");
        defaultProperties.put("bark.testOnStartup", "true");
        defaultProperties.put("bark.pushOnEnd", "true");
    }

    public void loadConfig() {
        Path path = Paths.get(configFile);
        if (!Files.exists(path)) {
            LogUtil.warn("未找到配置文件，正在创建默认配置...");
            createDefaultConfig(path);
            LogUtil.sys("配置文件已创建: " + path.toAbsolutePath() + " 请在配置完成后再次启动程序 (^_^) ~ ");
            System.exit(0);
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            conf.load(reader);
        } catch (IOException e) {
            LogUtil.err("读取配置文件失败: " + e.getMessage());
        }

        String rawIDs = conf.getProperty("roomIDs", "");
        String[] idArray = rawIDs.split(",");
        Set<String> distinctIDs = new LinkedHashSet<>();
        for (String id : idArray) {
            String trimmedId = id.trim();
            if (!trimmedId.isEmpty()) {
                if (!distinctIDs.add(trimmedId)) {
                    LogUtil.warn("发现重复的房间号并已自动过滤: [" + trimmedId + "]");
                }
            }
        }
        this.roomIDs = new ArrayList<>(distinctIDs);
        this.apiUrl = conf.getProperty("apiUrl");
        this.retryIntervalSeconds = getIntProperty("retryIntervalSeconds", 30);
        this.userInputTimeoutSeconds = getIntProperty("userInputTimeoutSeconds", 5);

        this.consoleLevel = conf.getProperty("log.console.level", "ALL");
        this.fileLevel = conf.getProperty("log.file.level", "SYSTEM,LIVE,PUSH,WARN,ERROR");
        LogUtil.setConsoleTags(this.consoleLevel);
        LogUtil.setFileTags(this.fileLevel);
        this.maxHistoryDays = getIntProperty("log.maxHistoryDays", 30);

        this.emailEnable = Boolean.parseBoolean(conf.getProperty("email.enable"));
        this.emailList = new HashSet<>(Arrays.asList(conf.getProperty("email.list").split(",")));
        this.emailTestOnStartup = Boolean.parseBoolean(conf.getProperty("email.testOnStartup"));
        this.emailPushOnEnd = Boolean.parseBoolean(conf.getProperty("email.pushOnEnd"));

        EmailSender.setSmtpConfig(
                conf.getProperty("smtp.host"),
                conf.getProperty("smtp.port"),
                conf.getProperty("smtp.username"),
                conf.getProperty("smtp.password")
        );

        this.barkEnable = Boolean.parseBoolean(conf.getProperty("bark.enable"));
        this.barkUrl = conf.getProperty("bark.url");
        this.barkTestOnStartup = Boolean.parseBoolean(conf.getProperty("bark.testOnStartup"));
        this.barkPushOnEnd = Boolean.parseBoolean(conf.getProperty("bark.pushOnEnd"));

        ensureDefaults(path);
    }

    private int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(conf.getProperty(key));
        } catch (NumberFormatException e) {
            LogUtil.err("配置项 [" + key + "] 格式非法，已使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    private void ensureDefaults(Path path) {
        List<String> currentLines = new ArrayList<>();
        if (Files.exists(path)) {
            try {
                currentLines = Files.readAllLines(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LogUtil.err("检查配置完整性时读取失败: " + e.getMessage());
            }
        }

        boolean updated = false;
        for (Map.Entry<String, String> entry : defaultProperties.entrySet()) {
            if (!conf.containsKey(entry.getKey())) {
                currentLines.add(entry.getKey() + "=" + entry.getValue());
                String displayValue = entry.getKey().contains("password") ? "******" : entry.getValue();
                LogUtil.warn("配置文件自动补全项: " + entry.getKey() + "=" + displayValue);
                updated = true;
            }
        }

        if (updated || !Files.exists(path)) {
            try {
                Files.write(path, currentLines, StandardCharsets.UTF_8);
                LogUtil.sys("配置文件已自动更新补全缺失项。");
            } catch (IOException e) {
                LogUtil.err("无法写入配置文件: " + e.getMessage());
            }
        }
    }

    private void createDefaultConfig(Path path) {
        initDefaults();
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("# BiliLiveNotifier Configuration File");
            writer.newLine();
            writer.write("# Created at: " + new java.util.Date());
            writer.newLine();
            writer.newLine();
            for (Map.Entry<String, String> entry : defaultProperties.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            LogUtil.err("创建配置文件失败: " + e.getMessage());
        }
    }

    // --- Getters ---
    public List<String> getRoomIDs() { return roomIDs; }
    public String getApiUrl() { return apiUrl; }
    public int getRetryIntervalSeconds() { return retryIntervalSeconds; }
    public int getUserInputTimeoutSeconds() { return userInputTimeoutSeconds; }
    public int getMaxHistoryDays() { return maxHistoryDays; }
    public boolean isEmailEnable() { return emailEnable; }
    public Set<String> getEmailList() { return emailList; }
    public boolean isEmailTestOnStartup() { return emailTestOnStartup; }
    public boolean isEmailPushOnEnd() { return emailPushOnEnd; }
    public boolean isBarkEnable() { return barkEnable; }
    public String getBarkUrl() { return barkUrl; }
    public boolean isBarkTestOnStartup() { return barkTestOnStartup; }
    public boolean isBarkPushOnEnd() { return barkPushOnEnd; }
}