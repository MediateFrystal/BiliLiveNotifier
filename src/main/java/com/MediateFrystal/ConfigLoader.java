package com.MediateFrystal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConfigLoader {
    private final Properties conf = new Properties();
    private final String configFile = "config.properties";
    private final Map<String, String> defaultProperties = new LinkedHashMap<>();
    private List<String> liveIDs;
    private Set<String> emailList;
    private int retryIntervalSeconds;
    private int userInputTimeoutSeconds;
    private boolean sendTestMailOnStartup;
    private String apiUrl;

    public ConfigLoader() {
        emailList = new HashSet<>();
        // 默认配置
        defaultProperties.put("liveIDs", "123456,234567,345678");
        defaultProperties.put("emailList", "example1@example.com,example2@example.com");
        defaultProperties.put("smtp.host", "<smtpHost>");
        defaultProperties.put("smtp.port", "<smtpPort>");
        defaultProperties.put("smtp.username", "<smtpUsername>");
        defaultProperties.put("smtp.password", "<smtpPassword>");
        defaultProperties.put("retryIntervalSeconds", "10");
        defaultProperties.put("userInputTimeoutSeconds", "5");
        defaultProperties.put("sendTestMailOnStartup", "true");
        defaultProperties.put("log.console.level", "INFO");
        defaultProperties.put("log.file.level", "ERROR");
        defaultProperties.put("log.toFile", "true");
        defaultProperties.put("apiUrl", "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=");

        checkAndCreateConfig(); // 如果文件不存在，创建文件
        loadConfig();           // 加载配置
        checkAndUpdateConfig(); // 自动补全缺失项
    }

    private void checkAndCreateConfig() {
        File file = new File(configFile);
        if (!file.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writer.write("# Configuration file example\n");
                writer.write("# emailList: List of recipient email addresses, separated by commas\n");
                writer.write("# liveIDs: List of recipient liveIDs, separated by commas\n");

                for (Map.Entry<String, String> entry : defaultProperties.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                }
                writer.flush(); // 强制刷新

                LogUtil.info("配置文件已创建: " + file.getAbsolutePath() +
                        " 请在配置完成后再次启动程序 (^_^) ~ ");
                System.exit(0);
            } catch (IOException e) {
                LogUtil.err("创建配置文件时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void loadConfig() {
        try (FileInputStream input = new FileInputStream(configFile)) {
            conf.load(input);

            emailList.addAll(Arrays.asList(conf.getProperty("emailList", "").split(",")));

            //获取主播房间ID列表，并校验输入是否合法
            liveIDs = new ArrayList<>();
            for (String id : conf.getProperty("liveIDs", "").split(",")) {
                if (id.trim().matches("\\d+")) {
                    liveIDs.add(id.trim());
                } else {
                    LogUtil.err("配置文件中无效的LiveID: " + id + ", 已跳过");
                }
            }

            retryIntervalSeconds = Integer.parseInt(conf.getProperty("retryIntervalSeconds", "10"));
            userInputTimeoutSeconds = Integer.parseInt(conf.getProperty("userInputTimeoutSeconds", "5"));
            sendTestMailOnStartup = Boolean.parseBoolean(conf.getProperty("sendTestMailOnStartup", "true"));
            apiUrl = conf.getProperty("apiUrl", "");

            EmailSender.setSmtpConfig(
                    conf.getProperty("smtp.host"),
                    conf.getProperty("smtp.port"),
                    conf.getProperty("smtp.username"),
                    conf.getProperty("smtp.password")
            );

            String consoleLevelStr = conf.getProperty("log.console.level", "INFO").toUpperCase();
            LogUtil.setConsoleLevel(LogUtil.Level.valueOf(consoleLevelStr));

            String fileLevelStr = conf.getProperty("log.file.level", "ERROR").toUpperCase();
            LogUtil.setFileLevel(LogUtil.Level.valueOf(fileLevelStr));

            LogUtil.setLogLiveToFile(Boolean.parseBoolean(conf.getProperty("log.toFile", "true")));

            LogUtil.live("日志系统初始化完成，控制台级别: " + consoleLevelStr + "，文件级别: " + fileLevelStr +
                    "，输出至文件：" + conf.getProperty("log.toFile", "true"));
            LogUtil.live("检查的间隔时间为 " + retryIntervalSeconds + " 秒。");
        } catch (IOException | IllegalArgumentException e) {
            LogUtil.err("加载配置文件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void checkAndUpdateConfig() {
        try {
            // 读取原始配置文件内容
            List<String> lines = new ArrayList<>();
            File conf = new File(configFile);
            if (conf.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(conf), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
            }

            // 检查缺失项
            List<String> newLines = new ArrayList<>(lines);
            boolean updated = false;

            for (Map.Entry<String, String> entry : defaultProperties.entrySet()) {
                boolean exists = false;
                for (String line : lines) {
                    if (line.trim().startsWith(entry.getKey() + "=")) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    newLines.add(entry.getKey() + "=" + entry.getValue());
                    LogUtil.info("配置文件缺少项，已添加默认值: " + entry.getKey() + "=" + entry.getValue());
                    updated = true;
                }
            }

            // 写回文件
            if (updated) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(conf), StandardCharsets.UTF_8))) {
                    for (String line : newLines) {
                        writer.write(line);
                        writer.newLine();
                    }
                    LogUtil.info("配置文件已更新，缺失项已补全。");
                }
            }
        } catch (IOException e) {
            LogUtil.err("更新配置文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<String> getLiveIDs() {
        return liveIDs;
    }

    public Set<String> getEmailList() {
        return emailList;
    }

    public int getRetryIntervalSeconds() {
        return retryIntervalSeconds;
    }

    public int getUserInputTimeoutSeconds() {
        return userInputTimeoutSeconds;
    }

    public boolean isSendTestMailOnStartup() {
        return sendTestMailOnStartup;
    }

    public String getApiUrl() {
        return apiUrl;
    }
}
