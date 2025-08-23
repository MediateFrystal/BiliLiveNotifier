package com.MediateFrystal;

import com.sun.mail.util.MailSSLSocketFactory;
import jakarta.mail.*;
import jakarta.mail.internet.*;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Properties;

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

    public static void emailSender(List<String> recipients, String emailBody, String emailSubject, LiveData data) throws GeneralSecurityException {
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

        for (String recipient : recipients) {
            try {
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(smtpUsername));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
                message.setSubject(emailSubject);
                message.setContent(emailBody, "text/html; charset=utf-8");

                Transport.send(message);
                LogUtil.live("成功发送房间 [" + data.getRoomID() + "] 的开播通知至 " + recipient);
            } catch (MessagingException e) {
                LogUtil.err(" 无法发送邮件至" + recipient + "，邮件配置可能出现问题: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void sendEmails(List<String> recipients, LiveData data) throws GeneralSecurityException {
        String emailBody = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f3f3f3; padding: 20px; }" +
                ".container { background-color: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0, 0, 0, 0.1); }" +
                "h1 { color: #0078d7; background-color: #f0f0f0; padding: 10px; border-radius: 4px; }" +
                ".info-block { background-color: #e6e6fa; padding: 10px; border-radius: 4px; margin: 0; }" +
                "p { color: #333333; margin: 0; }" +
                "a { color: #0078d7; text-decoration: none; }" +
                "a:hover { text-decoration: underline; }" +
                ".image-container { text-align: center; margin: 20px 0; }" +
                ".image-container img { max-width: 100%; height: auto; border-radius: 8px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='image-container'><img src='" + data.getUserCover() + "' alt='User Cover'></div>" +
                "<h1>直播信息通知</h1>" +
                "<div class='info-block'>" +
                "<p><strong>标题:</strong> " + data.getTitle() + "</p>" +
                "<p><strong>UID:</strong> " + data.getUid() + "</p>" +
                "<p><strong>房间ID:</strong> " + data.getRoomID() + "</p>" +
                "<p><strong>直播状态:</strong> " + data.getLiveStatus() + "</p>" +
                "<p><strong>用户空间:</strong> <a href='https://space.bilibili.com/" + data.getUid() + "'>点击打开</a></p>" +
                "<p><strong>直播链接:</strong> <a href='https://live.bilibili.com/" + data.getRoomID() + "'>点击打开</a></p>" +
                "<p>如果您不想再接收此类邮件，请联系管理员。</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
        String emailSubject = "【开播提醒】" + data.getUid();

        emailSender(recipients, emailBody, emailSubject, data);
    }
}
