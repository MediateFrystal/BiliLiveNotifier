# BiliLiveNotifier
哔哩哔哩直播消息发送到邮箱

原项目是 [FireworkRocket/BiliLiveSendToMail](https://github.com/FireworkRocket/BiliLiveSendToMail)，我修改了以满足自己的需求

## 使用方法

用Java在首次运行后会创建一个配置文件：

```
# Configuration file example
# EmailList: List of recipient email addresses, separated by commas
# Example: example1@example.com,example2@example.com
# LiveIDs: List of recipient LiveIDs, separated by commas too
# Example: XXXXXX,XXXXXX,XXXXX
LiveIDs=XXXXXX,XXXXXX,XXXXXX
EmailList=example1@example.com,example2@example.com
smtpHost=<smtpHost>
smtpPort=<smtpPort>
smtpUsername=<smtpUsername>
smtpPassword=<smtpPassword>
retryIntervalSeconds=10
userInputTimeoutSeconds=10
sendTestMailOnStartup=true
apiUrl=https://api.live.bilibili.com/room/v1/Room/get_info?room_id=
```

### 翻译对照

```
LiveIDs=填写你要监听的主播，如有多个，用英文的逗号分隔
EmailList=填写你要接受推送的邮箱，如有多个，用英文的逗号分隔
smtpHost=SMTP服务器
smtpPort=SMTP端口
smtpUsername=SMTP用户名
smtpPassword=SMTP密码
retryIntervalSeconds=检测到未开播时多久再重试（秒）
userInputTimeoutSeconds=检测到开播后手动跳过发送邮件的超时时长（秒）
sendTestMailOnStartup=启动时发送一封测试邮件
apiUrl=哔哩哔哩直播API
```

Linux后台运行命令：
`
nohup java -jar .\BiliLiveNotifier.jar &
`

Windows随便挂在那里就行

## 测试
Windows11 + Zulu17，使用QQ邮箱，可以正常运行

其他情况未测试，有bug我也不管了，我能用就行（）