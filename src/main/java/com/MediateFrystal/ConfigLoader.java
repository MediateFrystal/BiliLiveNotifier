package com.MediateFrystal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConfigLoader {
    private final String configFile = "config.properties";
    private final Properties conf;
    private final Map<String, String> defaultProperties = new LinkedHashMap<>();

    private List<String> liveIDs;
    private boolean emailEnable;
    private Set<String> emailList;
    private boolean testMailOnStartup;
    private int retryIntervalSeconds;
    private int userInputTimeoutSeconds;
    private boolean barkEnable;
    private String barkUrl;
    private boolean barkTestOnStartup;
    private boolean barkPushOnEnd;
    private LogUtil.Level consoleLevel;
    private LogUtil.Level fileLevel;
    private boolean logToFile;
    private int maxHistoryDays;
    private String apiUrl;

    public ConfigLoader() {
        initDefaults();

        // 2. 使用默认 Properties 初始化主配置对象
        Properties defaultProps = new Properties();
        defaultProperties.forEach(defaultProps::put);
        this.conf = new Properties(defaultProps);

        // 3. 执行加载
        loadConfig();

        LogUtil.live("日志系统初始化完成，控制台级别: " + conf.getProperty("log.console.level", "INFO").toUpperCase() +
                "；文件级别: " + conf.getProperty("log.file.level", "ERROR").toUpperCase() +
                "；输出至文件：" + conf.getProperty("log.toFile", "true") +
                "；日志保留天数：" + maxHistoryDays + " 天" +
                "；邮件推送：" + (emailEnable ? "开启" : "关闭") +
                "；正在监控 " + liveIDs.size() + " 个直播间" +
                "；检查的间隔时间为 " + retryIntervalSeconds + " 秒。");
    }
    private void initDefaults() {
        defaultProperties.put("liveIDs", "123456,234567");
        defaultProperties.put("apiUrl", "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=");
        defaultProperties.put("retryIntervalSeconds", "30");
        defaultProperties.put("userInputTimeoutSeconds", "5");

        defaultProperties.put("log.console.level", "INFO");
        defaultProperties.put("log.file.level", "ERROR");
        defaultProperties.put("log.toFile", "true");
        defaultProperties.put("log.maxHistoryDays", "30");

        defaultProperties.put("email.enable", "true");
        defaultProperties.put("email.list", "example1@mail.com,example2@mail.com");
        defaultProperties.put("email.testOnStartup", "true");
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
        File file = new File(configFile);
        if (file.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                conf.load(reader);
            } catch (IOException e) {
                LogUtil.err("读取配置文件失败: " + e.getMessage());
            }
        } else {
            LogUtil.warn("未找到配置文件，正在创建默认配置...");
            createDefaultConfig(file);
            LogUtil.info("配置文件已创建: " + file.getAbsolutePath() + " 请在配置完成后再次启动程序 (^_^) ~ ");
            System.exit(0);
        }

        this.liveIDs = Arrays.asList(conf.getProperty("liveIDs").split(","));
        this.apiUrl = conf.getProperty("apiUrl");
        this.retryIntervalSeconds = getIntProperty("retryIntervalSeconds", 30);
        this.userInputTimeoutSeconds = getIntProperty("userInputTimeoutSeconds", 5);

        try {
            this.consoleLevel = LogUtil.Level.valueOf(conf.getProperty("log.console.level", "INFO").toUpperCase());
            this.fileLevel = LogUtil.Level.valueOf(conf.getProperty("log.file.level", "ERROR").toUpperCase());
        } catch (IllegalArgumentException e) {
            LogUtil.err("日志级别配置错误，已回退至默认值");
            this.consoleLevel = LogUtil.Level.INFO;
            this.fileLevel = LogUtil.Level.ERROR;
        }
        this.logToFile = Boolean.parseBoolean(conf.getProperty("log.toFile", "true"));
        this.maxHistoryDays = getIntProperty("log.maxHistoryDays", 30);

        this.emailEnable = Boolean.parseBoolean(conf.getProperty("email.enable"));
        this.emailList = new HashSet<>(Arrays.asList(conf.getProperty("email.list").split(",")));
        this.testMailOnStartup = Boolean.parseBoolean(conf.getProperty("email.testOnStartup"));
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

        // 检查并自动补全缺失的配置项
        ensureDefaults(file);
    }

    private int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(conf.getProperty(key));
        } catch (NumberFormatException e) {
            LogUtil.err("配置项 [" + key + "] 格式非法，已使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    private void ensureDefaults(File file) {
        List<String> currentLines = new ArrayList<>();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    currentLines.add(line);
                }
            } catch (IOException e) {
                LogUtil.err("检查配置完整性时读取失败: " + e.getMessage());
            }
        }

        boolean updated = false;
        for (Map.Entry<String, String> entry : defaultProperties.entrySet()) {
            if (!conf.containsKey(entry.getKey())) {
                currentLines.add(entry.getKey() + "=" + entry.getValue());
                String displayValue = entry.getKey().contains("password") ? "******" : entry.getValue();
                LogUtil.warn("配置文件缺少项: " + entry.getKey() + "=" + displayValue);
                updated = true;
            }
        }

        if (updated || !file.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                for (String line : currentLines) {
                    writer.write(line);
                    writer.newLine();
                }
                LogUtil.info("配置文件已自动更新。");
            } catch (IOException e) {
                LogUtil.err("无法写入配置文件: " + e.getMessage());
            }
        }
    }

    private void createDefaultConfig(File file) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
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
    public List<String> getLiveIDs() { return liveIDs; }
    public String getApiUrl() { return apiUrl; }
    public int getRetryIntervalSeconds() { return retryIntervalSeconds; }
    public int getUserInputTimeoutSeconds() { return userInputTimeoutSeconds; }

    public LogUtil.Level getConsoleLevel() { return consoleLevel; }
    public LogUtil.Level getFileLevel() { return fileLevel; }
    public boolean isLogToFile() { return logToFile; }
    public int getMaxHistoryDays() { return maxHistoryDays; }

    public boolean isEmailEnable() { return emailEnable; }
    public Set<String> getEmailList() { return emailList; }
    public boolean isTestMailOnStartup() { return testMailOnStartup; }

    public boolean isBarkEnable() { return barkEnable; }
    public String getBarkUrl() { return barkUrl; }
    public boolean isBarkTestOnStartup() { return barkTestOnStartup; }
    public boolean isBarkPushOnEnd() { return barkPushOnEnd; }



}

