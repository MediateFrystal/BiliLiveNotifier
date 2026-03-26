package com.MediateFrystal;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class BarkSender {
    public static void send(String baseUrl, String title, String content, String roomId, String imageUrl, String iconUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) return;
        try {
            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            if (!baseUrl.endsWith("/")) urlBuilder.append("/");
            urlBuilder.append(URLEncoder.encode(title, StandardCharsets.UTF_8))
                    .append("/")
                    .append(URLEncoder.encode(content, StandardCharsets.UTF_8));
            urlBuilder.append("?level=timeSensitive");

            // 跳转直播间
            if (roomId != null) {
                urlBuilder.append("&url=").append(URLEncoder.encode("bilibili://live/" + roomId, StandardCharsets.UTF_8));
            }
            // 推送大图（直播封面）
            if (imageUrl != null && !imageUrl.isEmpty()) {
                urlBuilder.append("&image=").append(URLEncoder.encode(imageUrl, StandardCharsets.UTF_8));
            }
            // 推送图标（主播头像）
            if (iconUrl != null && !iconUrl.isEmpty()) {
                urlBuilder.append("&icon=").append(URLEncoder.encode(iconUrl, StandardCharsets.UTF_8));
            }

            URL url = new URI(urlBuilder.toString()).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);

            int code = conn.getResponseCode();
            if (code == 200) {
                LogUtil.info("Bark 推送成功: " + title);
            } else {
                LogUtil.err("Bark 推送失败，HTTP 响应码: " + code);
            }
        } catch (Exception e) {
            LogUtil.err("Bark 推送异常: " + e.getMessage());
        }
    }
}