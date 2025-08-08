package com.MediateFrystal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class LiveStatusChecker {
    private static int num = 0;

    public static LiveData getLiveData(String apiUrl, String liveID) throws Exception {
        num++;
        System.out.println(num + ":检查直播状态... [" + liveID + "]");

        try {
            // 构建请求 URL
            URL url = new URI(apiUrl + liveID).toURL();
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
                data.setRoomID(liveID);
                data.setLiveStatus(root.path("live_status").asInt());
                data.setTitle(root.path("title").asText());
                data.setUid(root.path("uid").asLong());
                return data;
            }

        } catch (Exception e) {
            System.err.println("邮件配置或 LiveIDs 配置可能出现问题: \n" + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
