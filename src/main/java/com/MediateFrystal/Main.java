package com.MediateFrystal;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static List<String> liveIDs;
    private static boolean emailEnable;
    public static Set<String> emailList = new HashSet<>();
    private static boolean testMailOnStartup;
    private static int retryIntervalSeconds;
    private static int userInputTimeoutSeconds;
    private static int maxHistoryDays;

    private static final Map<String, LiveData> activeLives = new ConcurrentHashMap<>();
    private static ConfigLoader config;
    private static final String VERSION = "1.3.2";

    // 用户信息缓存
    private static final Properties userCache = new Properties();
    private static final String CACHE_FILE = "user_cache.properties";

    public static void main(String[] args) throws GeneralSecurityException {
        config = new ConfigLoader();
        loadConfig();

        LogUtil.printDivider(VERSION);
        LogUtil.sys("BiliLiveNotifier v" + VERSION + " 正在启动...");

        loadUserCache();
        LogUtil.cleanOldLogs(maxHistoryDays);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                LogUtil.sys("正在执行每日例行日志清理...");
                LogUtil.cleanOldLogs(maxHistoryDays);
            } catch (Exception e) {
                LogUtil.err("自动清理日志时出现异常: " + e.getMessage());
            }
        }, 24, 24, TimeUnit.HOURS);

        // 启动测试邮件逻辑
        if (emailEnable) {
            if (testMailOnStartup) {
                LogUtil.push("准备发送测试邮件... 在 " + userInputTimeoutSeconds + " 秒内按下回车键跳过！");
                if (waitForUserInput()) {
                    LogUtil.push("已取消发送测试邮件... ~ （*＾-＾*）\n");
                } else {
                    EmailSender.test(new ArrayList<>(emailList), VERSION);
                }
            }
        } else {
            LogUtil.sys("邮件推送开关已关闭，跳过启动测试。");
        }

        // Bark 启动测试
        if (config.isBarkEnable() && config.isBarkTestOnStartup()) {
            LogUtil.push("正在发送 Bark 启动测试...");
            BarkSender.send(config.getBarkUrl(), "BiliLiveNotifier",
                    "版本: " + VERSION + "\n已缓存主播数: " + userCache.size()/2 +
                            "\n直播间数量: " + liveIDs.size(), null, null, null);
        }

        Map<String, Boolean> lastStatus = new HashMap<>();
        for (String liveID : liveIDs) {
            lastStatus.put(liveID.trim(), false);
        }

        scheduler.scheduleAtFixedRate(() -> checkAndNotify(lastStatus), 0, retryIntervalSeconds, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LogUtil.sys("程序正在关闭...");
            scheduler.shutdown();
        }));
    }

    private static void checkAndNotify(Map<String, Boolean> lastStatus) {
        for (String liveID : liveIDs) {
            liveID = liveID.trim();
            try {
                LiveData data = LiveStatusChecker.getLiveData(config.getApiUrl(), liveID);
                if (data == null) {
                    LogUtil.warn("无法获取房间 [" + liveID + "] 的状态数据。");
                    continue;
                }
                boolean isLive = data.getLiveStatus() == 1;
                boolean wasLive = lastStatus.getOrDefault(liveID, false);

                if (!wasLive && isLive) {
                    //LogUtil.live("检测到房间 [" + liveID + "] 开播！标题: " + data.getTitle());
                    activeLives.put(liveID, data);

                    // 获取并缓存用户信息
                    String uidStr = String.valueOf(data.getUid());
                    if (!userCache.containsKey(uidStr + ".name")) {
                        fetchAndCacheUserInfoWithThirdParty(data.getUid());
                    }

                    String userName = userCache.getProperty(uidStr + ".name", "房间 " + liveID);
                    String userFace = userCache.getProperty(uidStr + ".face", "");

                    LogUtil.live("检测到 房间 [" + liveID + "] " + "主播 [" + userName + "] 开播！标题: " + data.getTitle());

                    if (config.isBarkEnable()) {
                        LogUtil.push("正在推送 Bark 开播提醒...");
                        String barkTitle = "【" + userName + "】开播啦！";
                        String barkContent = data.getTitle();
                        BarkSender.send(config.getBarkUrl(), barkTitle, barkContent, liveID, data.getUserCover(), userFace);
                    }

                    if (emailEnable) {
                        LogUtil.push("准备发送邮件... 在 " + userInputTimeoutSeconds + " 秒内按下回车键跳过！");
                        if (!waitForUserInput()) {
                            CompletableFuture.runAsync(() -> {
                                try { EmailSender.send(new ArrayList<>(emailList), data, userName, userFace, VERSION); }
                                catch (Exception e) { LogUtil.err("邮件发送失败: " + e.getMessage()); }
                            });
                        } else {
                            LogUtil.push("发送邮件已取消 ~ （*＾-＾*）\n");
                        }
                    }
                } else if (wasLive && !isLive) {
                    // 下播逻辑
                    LiveData lastData = activeLives.remove(liveID);
                    String durationStr = formatDuration(lastData != null ? lastData.getStartTime() : System.currentTimeMillis());
                    String uidStr = String.valueOf(lastData != null ? lastData.getUid() : "");
                    String userName = userCache.getProperty(uidStr + ".name", liveID);

                    LogUtil.live("房间 [" + liveID + "] 主播 [" + userName + "] 下播。时长: " + durationStr);

                    if (config.isBarkEnable() && config.isBarkPushOnEnd()) {
                        LogUtil.push("正在推送 Bark 下播提醒...");
                        String barkTitle = "【" + userName + "】下播了";
                        String barkContent = "直播时长：" + durationStr;
                        String userFace = userCache.getProperty(uidStr + ".face", "");
                        BarkSender.send(config.getBarkUrl(), barkTitle, barkContent, liveID, null, userFace);
                    }
                } else if (wasLive && isLive) {
                    LogUtil.check("房间 [" + liveID + "] 正在直播。");
                } else {
                    LogUtil.check("房间 [" + liveID + "] 没有直播。");
                }
                lastStatus.put(liveID, isLive);
            } catch (Exception e) {
                LogUtil.err("检查房间 [" + liveID + "] 出错: " + e.getMessage());
            }
        }
    }

    private static void fetchAndCacheUserInfoWithThirdParty(long uid) {
        int maxTries = 3;
        int currentTry = 0;
        boolean success = false;

        while (currentTry < maxTries && !success) {
            currentTry++;
            String lastError = "未知错误";
            try {
                String apiEndpoint = "https://uapis.cn/api/v1/social/bilibili/userinfo?uid=" + uid;
                URL url = new URI(apiEndpoint).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                InputStream is = (conn.getResponseCode() >= 400) ? conn.getErrorStream() : conn.getInputStream();

                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String responseLine = reader.lines().collect(Collectors.joining());
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(responseLine);

                        if (root.has("error") && !root.get("error").isNull()) {
                            lastError = root.get("error").asText();
                            throw new IOException(lastError);
                        }

                        String name = root.path("name").asText();
                        String face = root.path("face").asText();

                        if (name != null && !name.isEmpty()) {
                            userCache.setProperty(uid + ".name", name);
                            userCache.setProperty(uid + ".face", face);
                            try (OutputStream os = new FileOutputStream(CACHE_FILE)) {
                                userCache.store(os, "User Info Cache");
                            } catch (IOException e) {
                                LogUtil.err("保存用户缓存失败: " + e.getMessage());
                            }
                            LogUtil.sys("已通过第三方 API 更新主播资料: " + name);
                            success = true;
                        } else {
                            lastError = "API 返回数据字段缺失";
                        }
                    }
                } else {
                    lastError = "无法连接到 API 服务器 (HTTP " + conn.getResponseCode() + ")";
                }
            } catch (Exception e) {
                // 这里会打印具体的 lastError 或者 Exception 的信息
                String detail = (e instanceof IOException) ? e.getMessage() : lastError;
                LogUtil.warn("获取 UID [" + uid + "] 资料失败，原因: " + detail + "，重试中 (" + currentTry + "/" + maxTries + ")...");

                if (currentTry < maxTries) {
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    LogUtil.err("达到最大重试次数，放弃获取 UID [" + uid + "] 的资料。");
                }
            }
        }
    }

    private static void loadUserCache() {
        File file = new File(CACHE_FILE);
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                userCache.load(is);
                LogUtil.sys("已加载本地用户缓存。");
            } catch (IOException e) {
                LogUtil.err("读取用户缓存文件失败: " + e.getMessage());
            }
        }
    }

    private static void loadConfig() {
        liveIDs = config.getLiveIDs();
        emailEnable = config.isEmailEnable();
        emailList = config.getEmailList();
        testMailOnStartup = config.isTestMailOnStartup();
        retryIntervalSeconds = config.getRetryIntervalSeconds();
        userInputTimeoutSeconds = config.getUserInputTimeoutSeconds();
        maxHistoryDays = config.getMaxHistoryDays();
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
        }
        return false;
    }

    private static String formatDuration(long startMillis) {
        long minutes = (System.currentTimeMillis() - startMillis) / (1000 * 60);
        if (minutes < 60) return minutes + " 分钟";
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }
}