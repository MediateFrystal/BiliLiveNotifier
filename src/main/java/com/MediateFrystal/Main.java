package com.MediateFrystal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static Set<String> emailList = new HashSet<>();
    private static List<String> LiveIDs;
    private static int retryIntervalSeconds;
    private static int userInputTimeoutSeconds;
    private static boolean sendTestMailOnStartup;
    private static String apiUrl;

    public static void main(String[] args) throws GeneralSecurityException {
        loadConfig();

        if (sendTestMailOnStartup) {
            System.out.println("准备发送测试邮件......" +
                "\n在 " + userInputTimeoutSeconds + " 秒内按下 回车键 跳过测试邮件的发送！");

            // 等待用户输入
            if (waitForUserInput()) {
                System.out.println("诶......不发送测试邮件嘛......那好吧~ （*＾-＾*）");
            } else {
                // 发送测试邮件
                System.out.println(">>>>>>测试邮件发送中<<<<<<");
                LiveData testData = new LiveData();
                testData.setRoomID("TEST_ROOM");
                testData.setUid(1234567890L);
                testData.setTitle("这是一封测试邮件");
                testData.setLiveStatus(1); // 状态可以随便设，反正只是测试用
                EmailSender.sendEmails(new ArrayList<>(emailList), testData);
                System.out.println(">>>>>>测试邮件发送完成<<<<<<\n邮箱能不能收到测试邮件呢~ 接下来我要开始工作了哟~ []~(￣▽￣)~*\n");
            }
        } else {
            System.out.println("诶......不发送测试邮件嘛......那好吧~ （*＾-＾*）");
        }

        Map<String, Boolean> lastStatus = new HashMap<>();

        for (String liveID : LiveIDs) {
            lastStatus.put(liveID.trim(), false);
        }

        scheduler.scheduleAtFixedRate(() -> checkAndSendEmails(lastStatus), 0, retryIntervalSeconds, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("......");
            scheduler.shutdown();
        }));
    }

    private static void checkAndSendEmails(Map<String, Boolean> lastStatus) {
        for (String liveID : LiveIDs) {
            liveID = liveID.trim();
            try {
                LiveData data = LiveStatusChecker.getLiveData(apiUrl, liveID);
                boolean isLive = Objects.requireNonNull(data).getLiveStatus() == 1;
                boolean wasLive = lastStatus.get(liveID);

                // 检查直播状态
                if (!wasLive && isLive) {
                    System.out.println("房间 [" + liveID + "] 的直播状态为 1 并且上次检查时未开播，准备发送邮件......" +
                            "\n在 " + userInputTimeoutSeconds + " 秒内按下 回车键 跳过本房间邮件的发送！");

                    // 等待用户输入
                    if (waitForUserInput()) {
                        System.out.println("诶......不发送邮件嘛......那好吧~ （*＾-＾*）");
                    } else {
                        // 发送邮件给所有收件
                        System.out.println(">>>>>>邮件发送中<<<<<<");
                        EmailSender.sendEmails(new ArrayList<>(emailList), data);
                        System.out.println(">>>>>>邮件发送完成<<<<<<\n");
                    }
                } else if (wasLive && isLive) {
                    System.out.println("正在直播，已经发送过邮件，无需再次发送。");
                } else {
                    System.out.println("直播状态是 0，将在" + retryIntervalSeconds + "秒后再次检查。");
                }
                lastStatus.put(liveID, isLive);
            } catch (Exception e) {
                System.err.println("检查房间 " + liveID + " 时出错(T_T): " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void loadConfig() {
        Properties properties = new Properties();
        File configFile = new File("config.properties");

        if (!configFile.exists()) {
            createConfig(configFile);
        }

        try (FileInputStream input = new FileInputStream(configFile)) {
            properties.load(input);
            String emails = properties.getProperty("EmailList");
            emailList.addAll(Arrays.asList(emails.split(",")));
            LiveIDs = new ArrayList<>(Arrays.asList(properties.getProperty("LiveIDs").split(",")));

            EmailSender.setSmtpConfig(
                    properties.getProperty("smtpHost"),
                    properties.getProperty("smtpPort"),
                    properties.getProperty("smtpUsername"),
                    properties.getProperty("smtpPassword")
            );
            retryIntervalSeconds = Integer.parseInt(properties.getProperty("retryIntervalSeconds"));
            userInputTimeoutSeconds = Integer.parseInt(properties.getProperty("userInputTimeoutSeconds"));
            sendTestMailOnStartup = Boolean.parseBoolean(properties.getProperty("sendTestMailOnStartup"));
            apiUrl = properties.getProperty("apiUrl");
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("加载配置文件时出错(＃°Д°): " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createConfig(File configFile) {
        try (FileOutputStream output = new FileOutputStream(configFile)) {
            // 使用 LinkedHashMap 维护插入顺序
            Map<String, String> orderedProperties = new LinkedHashMap<>();
            orderedProperties.put("LiveIDs", "XXXXXX,XXXXXX,XXXXXX");
            orderedProperties.put("EmailList", "example1@example.com,example2@example.com");
            orderedProperties.put("smtpHost", "<smtpHost>");
            orderedProperties.put("smtpPort", "<smtpPort>");
            orderedProperties.put("smtpUsername", "<smtpUsername>");
            orderedProperties.put("smtpPassword", "<smtpPassword>");
            orderedProperties.put("retryIntervalSeconds", "10");
            orderedProperties.put("userInputTimeoutSeconds", "5");
            orderedProperties.put("sendTestMailOnStartup", "true");
            orderedProperties.put("apiUrl", "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=");

            // 手动写入文件并添加中文注释和示例邮箱
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write("# Configuration file example\n");
                writer.write("# EmailList: List of recipient email addresses, separated by commas\n");
                writer.write("# Example: example1@example.com,example2@example.com\n");
                writer.write("# LiveIDs: List of recipient LiveIDs, separated by commas too\n");
                writer.write("# Example: XXXXXX,XXXXXX,XXXXX\n");
                for (Map.Entry<String, String> entry : orderedProperties.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                }
            }
            System.out.println("配置文件已创建: " + configFile.getAbsolutePath() + " 请在配置完成后再次启动程序 (^_^) ~ ");
            System.exit(0);
        } catch (IOException e) {
            System.err.println("创建配置文件时出错(＃°Д°): " + e.getMessage());
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
