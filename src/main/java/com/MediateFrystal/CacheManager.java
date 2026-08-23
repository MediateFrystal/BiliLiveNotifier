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
import java.util.Collection;
import java.util.Properties;

public class CacheManager {
    private static final String CACHE_FILE = "user_cache.properties";
    private static final String CACHE_TMP_FILE = "user_cache.properties.tmp";
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
            LogUtil.sys("已加载本地用户缓存。当前记录数: " + getCacheSize());
        } catch (IOException e) {
            LogUtil.err("读取用户缓存失败: " + e.getMessage());
        }
    }

    /**
     * 将内存中的缓存原子写入磁盘：先写临时文件，再原子替换，避免写入中途崩溃导致缓存文件损坏
     */
    private static void save() {
        Path path = Paths.get(CACHE_FILE);
        Path tmpPath = Paths.get(CACHE_TMP_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(tmpPath, StandardCharsets.UTF_8)) {
            userCache.store(writer, "User Info Cache");
            writer.flush();
        } catch (IOException e) {
            LogUtil.err("保存用户缓存文件失败: " + e.getMessage());
            return;
        }

        try {
            Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            // 文件系统不支持原子移动时回退为普通替换
            try {
                Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LogUtil.err("替换用户缓存文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 清理缓存中不再监控的房间条目（含旧版以 uid 为 Key 的遗留条目），并将结果同步到磁盘
     * @param monitoredRoomIDs 当前配置中正在监控的房间 ID 集合
     */
    public static synchronized void pruneStaleEntries(Collection<String> monitoredRoomIDs) {
        if (monitoredRoomIDs == null || monitoredRoomIDs.isEmpty()) return;

        int before = userCache.size();
        userCache.keySet().removeIf(key -> {
            String keyStr = key.toString();
            int dotIndex = keyStr.lastIndexOf('.');
            if (dotIndex > 0) {
                String roomID = keyStr.substring(0, dotIndex);
                return !monitoredRoomIDs.contains(roomID);
            }
            return false;
        });

        int removed = before - userCache.size();
        if (removed > 0) {
            save();
            LogUtil.sys("用户缓存清理完成，共移除 " + removed + " 条不再监控的条目（含旧版 uid Key 遗留条目）。");
        }
    }

    public static synchronized UserInfo getOrFetchUser(String roomID, String uid) {
        String nameKey = roomID + ".name";
        String faceKey = roomID + ".face";
        String name = userCache.getProperty(nameKey);
        String face = userCache.getProperty(faceKey);

        if (name == null || name.isEmpty()) {
            LogUtil.warn("房间 [" + roomID + "] 的主播数据缺失，准备调用第三方 API...");
            if (fetchAndCache(roomID, uid)) {
                name = userCache.getProperty(nameKey);
                face = userCache.getProperty(faceKey);
            }
        }
        return new UserInfo(name != null ? name : "未知主播", face != null ? face : "");
    }

    private static boolean fetchAndCache(String roomID, String uid) {
        int maxTries = 3;

        for (int i = 1; i <= maxTries; i++) {
            try {
                String api = "https://uapis.cn/api/v1/social/bilibili/userinfo?uid=" + uid;
                URL url = new URI(api).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                try {
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(8000);

                    if (conn.getResponseCode() == 200) {
                        JsonNode root = mapper.readTree(conn.getInputStream());
                        String name = root.path("name").asText();
                        String face = root.path("face").asText();

                        if (name != null && !name.isEmpty()) {
                            setProperty(roomID, name, face);
                            LogUtil.sys("API 更新成功: " + name);
                            return true;
                        }
                    }
                } finally {
                    conn.disconnect();
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

    public static synchronized String getProperty(String key, String defaultValue) {
        return userCache.getProperty(key, defaultValue);
    }

    public static synchronized void setProperty(String roomID, String name, String face) {
        userCache.setProperty(roomID + ".name", name);
        userCache.setProperty(roomID + ".face", face);
        save();
    }

    public static synchronized boolean hasUser(String roomID) {
        return userCache.containsKey(roomID + ".name");
    }

    /**
     * 以「主播数量」为单位统计缓存记录数（统计 .name 键，避免遗留键导致的计数偏差）
     */
    public static synchronized int getCacheSize() {
        int count = 0;
        for (Object key : userCache.keySet()) {
            if (key.toString().endsWith(".name")) count++;
        }
        return count;
    }
}
