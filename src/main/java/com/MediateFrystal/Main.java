package com.MediateFrystal;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
        loadConfig();

        if (sendTestMailOnStartup) {
            LogUtil.info("准备发送测试邮件......" +
                "\n在 " + userInputTimeoutSeconds + " 秒内按下 回车键 跳过测试邮件的发送！");

            // 等待用户输入
            if (waitForUserInput()) {
                LogUtil.info("诶......不发送测试邮件嘛......那好吧~ （*＾-＾*）");
            } else {
                // 发送测试邮件
                LogUtil.info(">>>>>>测试邮件发送中<<<<<<");
                LiveData testData = new LiveData();
                testData.setRoomID("TEST_ROOM");
                testData.setUid(1234567890L);
                testData.setTitle("这是一封测试邮件");
                testData.setLiveStatus(1);
                EmailSender.sendEmails(new ArrayList<>(emailList), testData);
                LogUtil.info(">>>>>>测试邮件发送完成<<<<<<\n邮箱能不能收到测试邮件呢~ 接下来我要开始工作了哟~ []~(￣▽￣)~*\n");
            }
        } else {
            LogUtil.info("诶......不发送测试邮件嘛......那好吧~ （*＾-＾*）");
        }

        Map<String, Boolean> lastStatus = new HashMap<>();

        for (String liveID : liveIDs) {
            lastStatus.put(liveID.trim(), false);
        }

        scheduler.scheduleAtFixedRate(() -> checkAndSendEmails(lastStatus), 0, retryIntervalSeconds, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LogUtil.info("......");
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
                    LogUtil.live("房间 [" + liveID + "] 的直播状态为 1 并且上次检查时未开播，准备发送邮件......" +
                            "\n在 " + userInputTimeoutSeconds + " 秒内按下 回车键 跳过本房间邮件的发送！");

                    // 等待用户输入
                    if (waitForUserInput()) {
                        LogUtil.live("诶......不发送邮件嘛......那好吧~ （*＾-＾*）");
                    } else {
                        // 发送邮件给所有收件人
                        LogUtil.live(">>>>>>邮件发送中<<<<<<");
                        EmailSender.sendEmails(new ArrayList<>(emailList), data);
                        LogUtil.live(">>>>>>邮件发送完成<<<<<<\n");
                    }
                } else if (wasLive && !isLive) {
                    LogUtil.live("房间 [" + liveID + "] 的直播状态变为 0，直播结束，将在 " + retryIntervalSeconds + " 秒后再次检查。");
                } else if (wasLive && isLive) {
                    LogUtil.info("正在直播，已经发送过邮件，无需再次发送，将在 " + retryIntervalSeconds + " 秒后再次检查。");
                }else {
                    LogUtil.info("直播状态是 0，将在" + retryIntervalSeconds + "秒后再次检查。");
                }
                lastStatus.put(liveID, isLive);
            } catch (Exception e) {
                LogUtil.err("检查房间 " + liveID + " 时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void loadConfig() {
        Properties conf = new Properties();
        File configFile = new File("config.properties");

        if (!configFile.exists()) {
            createConfig(configFile);
        }

        try (FileInputStream input = new FileInputStream(configFile)) {
            conf.load(input);
            String emails = conf.getProperty("emailList");
            emailList.addAll(Arrays.asList(emails.split(",")));
            liveIDs = new ArrayList<>(Arrays.asList(conf.getProperty("liveIDs").split(",")));

            EmailSender.setSmtpConfig(
                    conf.getProperty("smtp.host"),
                    conf.getProperty("smtp.port"),
                    conf.getProperty("smtp.username"),
                    conf.getProperty("smtp.password")
            );
            retryIntervalSeconds = Integer.parseInt(conf.getProperty("retryIntervalSeconds"));
            userInputTimeoutSeconds = Integer.parseInt(conf.getProperty("userInputTimeoutSeconds"));
            sendTestMailOnStartup = Boolean.parseBoolean(conf.getProperty("sendTestMailOnStartup"));
            apiUrl = conf.getProperty("apiUrl");

            // 配置日志级别和输出
            String consoleLevelStr = conf.getProperty("log.console.level").toUpperCase();
            LogUtil.setConsoleLevel(LogUtil.Level.valueOf(consoleLevelStr));

            String fileLevelStr = conf.getProperty("log.file.level").toUpperCase();
            LogUtil.setFileLevel(LogUtil.Level.valueOf(fileLevelStr));

            String liveToFileStr = conf.getProperty("log.liveToFile");
            LogUtil.setLogLiveToFile(Boolean.parseBoolean(liveToFileStr));

            LogUtil.live("日志系统初始化完成，控制台级别: " + consoleLevelStr + "，文件级别: " + fileLevelStr + "，输出至文件：" + liveToFileStr);
        } catch (IOException | IllegalArgumentException e) {
            LogUtil.err("加载配置文件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createConfig(File configFile) {
        try (FileOutputStream output = new FileOutputStream(configFile)) {
            // 使用 LinkedHashMap 维护插入顺序
            Map<String, String> orderedProperties = new LinkedHashMap<>();
            orderedProperties.put("liveIDs", "XXXXXX,XXXXXX,XXXXXX");
            orderedProperties.put("emailList", "example1@example.com,example2@example.com");
            orderedProperties.put("smtp.host", "<smtpHost>");
            orderedProperties.put("smtp.port", "<smtpPort>");
            orderedProperties.put("smtp.username", "<smtpUsername>");
            orderedProperties.put("smtp.password", "<smtpPassword>");
            orderedProperties.put("retryIntervalSeconds", "10");
            orderedProperties.put("userInputTimeoutSeconds", "5");
            orderedProperties.put("sendTestMailOnStartup", "true");
            orderedProperties.put("log.console.level", "INFO");
            orderedProperties.put("log.file.level", "ERROR");
            orderedProperties.put("log.liveToFile", "true");
            orderedProperties.put("apiUrl", "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=");

            // 手动写入文件并添加中文注释和示例邮箱
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write("# Configuration file example\n");
                writer.write("# emailList: List of recipient email addresses, separated by commas\n");
                writer.write("# Example: example1@example.com,example2@example.com\n");
                writer.write("# liveIDs: List of recipient liveIDs, separated by commas too\n");
                writer.write("# Example: XXXXXX,XXXXXX,XXXXX\n");
                for (Map.Entry<String, String> entry : orderedProperties.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                }
            }
            LogUtil.info("配置文件已创建: " + configFile.getAbsolutePath() + " 请在配置完成后再次启动程序 (^_^) ~ ");
            System.exit(0);
        } catch (IOException e) {
            LogUtil.err("创建配置文件时出错: " + e.getMessage());
            e.printStackTrace();
        }
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
