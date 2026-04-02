package com.MediateFrystal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LiveStatusChecker {
    private static int num = 0;
    private static final Map<String, LiveData> activeLives = new ConcurrentHashMap<>();
    /**
     * 核心业务：检查状态并触发通知
     */
    public static void process(String roomID, Map<String, Boolean> lastStatus, ConfigLoader config) {

        roomID = roomID.trim();
        try {
            LiveData data = getLiveData(config.getApiUrl(), roomID);

            if (data == null) {
                LogUtil.warn("无法获取房间 [" + roomID + "] 的状态数据，获取到的数据为空。");
                return;
            }

            boolean isLive = data.getLiveStatus() == 1;
            boolean wasLive = lastStatus.getOrDefault(roomID, false);

            if (!wasLive && isLive) {
                streamStart(data, config);
            } else if (wasLive && !isLive) {
                streamEnd(data, config);
            } else {
                LogUtil.check("房间 [" + roomID + "] " + (isLive ? "正在直播。" : "没有直播。"));
            }
            lastStatus.put(roomID, isLive);
        } catch (Exception e) {
            LogUtil.err("检查房间 [" + roomID + "] 出错: " + e.getMessage());
        }
    }

    private static void streamStart(LiveData data, ConfigLoader config) {
        activeLives.put(data.getRoomID(), data);
        CacheManager.UserInfo user = CacheManager.getOrFetchUser(data.getUid());
        data.setUserName(user.name());

        if (user.name().equals("未知主播")) {
            LogUtil.err("无法获取 UID [" + data.getUid() + "] 的昵称");
        }

        NotificationManager.sendStartNotification(data, config);
    }
    private static void streamEnd(LiveData data, ConfigLoader config) {
        LiveData lastData = activeLives.remove(data.getRoomID());
        long startTime = (lastData != null) ? lastData.getStartTime() : System.currentTimeMillis();
        String duration = formatDuration(startTime);

        NotificationManager.sendEndNotification(lastData != null ? lastData : data, duration, config);
    }

    private static String formatDuration(long startMillis) {
        long minutes = (System.currentTimeMillis() - startMillis) / (1000 * 60);
        if (minutes < 60) return minutes + " 分钟";
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }
    public static LiveData getLiveData(String apiUrl, String roomID) {
        num++;
        LogUtil.check(num + "\t: 检查房间 [" + roomID + "] 直播状态... ");

        try {
            // 构建请求 URL
            URL url = new URI(apiUrl + roomID).toURL();
            HttpURLConnection request = (HttpURLConnection) url.openConnection();
            request.setRequestMethod("GET");
            request.setConnectTimeout(5000);
            request.setReadTimeout(5000);
            request.connect();

            // 将输入流转换为 JSON 对象
            ObjectMapper mapper = new ObjectMapper();
            try (InputStream response = request.getInputStream()) {
                JsonNode root = mapper.readTree(response).path("data");
                LiveData data = new LiveData();
                data.setRoomID(roomID);
                data.setLiveStatus(root.path("live_status").asInt());
                data.setTitle(root.path("title").asText());
                data.setUid(root.path("uid").asText());
                data.setUserCover(root.path("user_cover").asText());
                return data;
            }

        } catch (Exception e) {
            LogUtil.err("检查房间 [" + roomID + "] 状态失败: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
