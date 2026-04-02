package com.MediateFrystal;

import com.sun.mail.util.MailSSLSocketFactory;
import jakarta.mail.*;
import jakarta.mail.internet.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

public class EmailSender {
    private static String smtpHost;
    private static String smtpPort;
    private static String smtpUsername;
    private static String smtpPassword;

    public static void setSmtpConfig(String host, String port, String username, String password) {
        smtpHost = host;
        smtpPort = port;
        smtpUsername = username;
        smtpPassword = password;
    }

    /**
     * 发送测试邮件
     */
    public static void test(List<String> emailList, int timeout) {
        // 启动测试邮件逻辑
        LogUtil.sys("准备发送测试邮件... 在 " + timeout + " 秒内按下回车键跳过！");
        if (waitForUserInput(timeout)) {
            LogUtil.sys("已跳过测试邮件的发送... ~ （*＾-＾*）\n");
        } else {
            LiveData testData = new LiveData();
            testData.setRoomID("TEST_ROOM");
            testData.setUid("172888798");
            testData.setTitle("这是一封功能测试邮件");
            testData.setLiveStatus(1);
            testData.setUserCover(null);

            CompletableFuture.runAsync(() -> {
                try {
                    // 测试邮件使用占位昵称和默认头像
                    send(new ArrayList<>(emailList), testData, "测试", "https://static.hdslb.com/images/akari.jpg");
                } catch (Exception e) {
                    LogUtil.err("测试邮件发送异步任务失败: " + e.getMessage());
                }
            });
        }
        // else { LogUtil.sys("邮件推送开关已关闭，跳过启动测试。"); }
    }

    /**
     * 发送正式邮件
     */
    public static void send(List<String> recipients, LiveData data, String userName, String userFace) throws GeneralSecurityException {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        MailSSLSocketFactory sf = new MailSSLSocketFactory();
        sf.setTrustAllHosts(true);
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.socketFactory", sf);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpUsername));

            // 添加所有收件人
            InternetAddress[] addresses = new InternetAddress[recipients.size()];
            for (int i = 0; i < recipients.size(); i++) {
                addresses[i] = new InternetAddress(recipients.get(i));
            }
            message.setRecipients(Message.RecipientType.TO, addresses);

            // 邮件主题：包含主播名和标题
            String safeName = (userName != null) ? userName : "主播";
            message.setSubject("【开播】" + safeName + "：" + data.getTitle());
            String webLink = "https://live.bilibili.com/" + data.getRoomID();

            String htmlContent = "<!DOCTYPE html><html>" +
                    "<head><style>" +
                    "    .container { font-family: 'Microsoft YaHei', -apple-system, sans-serif; max-width: 500px; margin: 20px auto; border: 1px solid #f0f0f0; border-radius: 12px; overflow: hidden; text-align: center; box-shadow: 0 4px 15px rgba(0,0,0,0.05); }" +
                    "    .user-area { padding: 30px 20px 10px; }" +
                    "    .avatar { width: 80px; height: 80px; border-radius: 40px; border: 1px solid #eee; }" +
                    "    .username { font-size: 18px; font-weight: bold; color: #18191c; margin-top: 10px; }" +
                    "    .status-tag { display: inline-block; background: #fb7299; color: white; font-size: 12px; padding: 2px 8px; border-radius: 4px; vertical-align: middle; margin-bottom: 2px; }" +
                    "    .content { padding: 0 25px 30px; }" +
                    "    .live-title { font-size: 16px; color: #61666d; margin: 15px 0; line-height: 1.4; }" +
                    "    .cover-box { margin-bottom: 20px; }" +
                    "    .cover-img { width: 100%; border-radius: 8px; display: block; }" +
                    "    .btn-group { margin-top: 20px; }" +
                    "    .btn { display: inline-block; padding: 10px 20px; border-radius: 20px; font-weight: bold; text-decoration: none; margin: 5px; }" +
                    "    .btn-main { background-color: #fb7299; color: #ffffff !important; box-shadow: 0 4px 10px rgba(251,114,153,0.3); }" +
                    "    .footer { background: #f6f7f9; padding: 20px; font-size: 12px; color: #999; line-height: 1.6; }" +
                    "</style></head>" +
                    "<body>" +
                    "<div class='container'>" +
                    "    <div class='user-area'>" +
                    "        <img src='" + (userFace != null && !userFace.isEmpty() ? userFace : "https://static.hdslb.com/images/akari.jpg") + "' class='avatar'>" +
                    "        <div class='username'><span class='status-tag'>直播中</span> " + safeName + "</div>" +
                    "    </div>" +
                    "    <div class='content'>" +
                    "        <div class='live-title'>" + data.getTitle() + "</div>" +
                    "        <div class='cover-box'>" +
                    "            <a href='" + webLink + "'><img src='" + data.getUserCover() + "' class='cover-img'></a>" +
                    "        </div>" +
                    "        <div class='btn-group'>" +
                    "            <a href='" + webLink + "' class='btn btn-main'>⚡ 浏览器打开</a>" +
                    "        </div>" +
                    "    </div>" +
                    "    <div class='footer'>" +
                    "        房间 ID: " + data.getRoomID() + "<br>" +
                    "        UID: " + data.getUid() + "<br><br>" +
                    "        BiliLiveNotifier v" + Main.VERSION + "<br>" +
                    "        <span style='font-size:11px;'>如果您不想再接收此类提醒，请修改程序配置。</span>" +
                    "    </div>" +
                    "</div></body></html>";

            message.setContent(htmlContent, "text/html; charset=UTF-8");
            Transport.send(message);
            LogUtil.push(">>>>>>邮件发送完成<<<<<<\n[" + data.getRoomID() + "] 的邮件已成功发送给 " + recipients.size() + " 位收件人。  []~(￣▽￣)~*\n");

        } catch (MessagingException e) {
            throw new RuntimeException("邮件发送核心过程出错", e);
        }
    }

    private static boolean waitForUserInput(int timeout) {
        long endTime = System.currentTimeMillis() + timeout * 1000L;
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
}
