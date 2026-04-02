package com.MediateFrystal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public class CacheManager {
    private static final String CACHE_FILE = "user_cache.properties";
    private static final Properties userCache = new Properties();
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        load();
    }

    private static void load() {
        Path path = Paths.get(CACHE_FILE);
        if (!Files.exists(path)) {
            LogUtil.sys("未找到本地缓存文件，将创建新缓存。");
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            userCache.clear();
            userCache.load(reader);
            LogUtil.sys("已加载本地用户缓存。当前记录数: " + userCache.size() / 2);
        } catch (IOException e) {
            LogUtil.err("读取用户缓存失败: " + e.getMessage());
        }
    }

    private static void save() {
        Path path = Paths.get(CACHE_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            userCache.store(writer, "User Info Cache");
        } catch (IOException e) {
            LogUtil.err("保存用户缓存文件失败: " + e.getMessage());
        }
    }

    public static UserInfo getOrFetchUser(String uid) {
        String nameKey = uid + ".name";
        String faceKey = uid + ".face";
        String name = userCache.getProperty(nameKey);
        String face = userCache.getProperty(faceKey);

        if (name == null || name.isEmpty()) {
            LogUtil.warn("UID [" + uid + "] 的数据缺失，准备调用第三方 API...");
            if (fetchAndCache(uid)) {
                name = userCache.getProperty(nameKey);
                face = userCache.getProperty(faceKey);
            }
        }
        return new UserInfo(name != null ? name : "未知主播", face != null ? face : "");
    }

    private static boolean fetchAndCache(String uid) {
        int maxTries = 3;

        for (int i = 1; i <= maxTries; i++) {
            try {
                String api = "https://uapis.cn/api/v1/social/bilibili/userinfo?uid=" + uid;
                URL url = new URI(api).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);

                if (conn.getResponseCode() == 200) {
                    JsonNode root = mapper.readTree(conn.getInputStream());
                    String name = root.path("name").asText();
                    String face = root.path("face").asText();

                    if (name != null && !name.isEmpty()) {
                        setProperty(uid, name, face);
                        LogUtil.sys("API 更新成功: " + name);
                        return true;
                    }
                }
            } catch (Exception e) {
                String detail = (e instanceof IOException) ? e.getMessage() : "未知错误";
                LogUtil.warn("获取 UID [" + uid + "] 资料失败，原因: " + detail + "，重试中 (" + i + "/" + maxTries + ")...");
                if (i == maxTries) LogUtil.err("达到最大重试次数，放弃获取 UID [" + uid + "] 的资料。");
            }
        }
        return false;
    }
    public record UserInfo(String name, String face) {}

    public static String getProperty(String key, String defaultValue) {
        return userCache.getProperty(key, defaultValue);
    }

    public static void setProperty(String uid, String name, String face) {
        userCache.setProperty(uid + ".name", name);
        userCache.setProperty(uid + ".face", face);
        save();
    }

    public static boolean hasUser(String uid) {
        return userCache.containsKey(uid + ".name");
    }

    public static int getCacheSize() {
        return userCache.size() / 2;
    }
}