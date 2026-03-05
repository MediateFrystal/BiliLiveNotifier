package com.MediateFrystal;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class BarkSender {
    public static void send(String baseUrl, String title, String content, String roomId, String imageUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) return;
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String encodedContent = URLEncoder.encode(content, StandardCharsets.UTF_8);

            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            if (!baseUrl.endsWith("/")) urlBuilder.append("/");
            urlBuilder.append(encodedTitle).append("/").append(encodedContent);
            urlBuilder.append("?level=timeSensitive");

            if (roomId != null) {
                urlBuilder.append("&url=").append(URLEncoder.encode("bilibili://live/" + roomId, StandardCharsets.UTF_8));
            }
            if (imageUrl != null && !imageUrl.isEmpty()) {
                urlBuilder.append("&image=").append(URLEncoder.encode(imageUrl, StandardCharsets.UTF_8));
            }

            URL url = new URI(urlBuilder.toString()).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            //LogUtil.info("Full URL: " + urlBuilder.toString());
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