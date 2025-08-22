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
liveIDs=XXXXXX,XXXXXX,XXXXXX
emailList=example1@example.com,example2@example.com
smtp.host=<smtpHost>
smtp.port=<smtpPort>
smtp.username=<smtpUsername>
smtp.password=<smtpPassword>
retryIntervalSeconds=10
userInputTimeoutSeconds=10
sendTestMailOnStartup=true
log.console.level=INFO
log.file.level=ERROR
log.liveToFile=true
apiUrl=https://api.live.bilibili.com/room/v1/Room/get_info?room_id=
```

### 翻译对照

```
liveIDs=填写你要监听的主播，如有多个，用英文的逗号分隔
emailList=填写你要接受推送的邮箱，如有多个，用英文的逗号分隔
smtp.host=SMTP服务器
smtp.port=SMTP端口
smtp.username=SMTP用户名
smtp.password=SMTP密码
retryIntervalSeconds=检测到未开播时多久再重试（秒）
userInputTimeoutSeconds=检测到开播后手动跳过发送邮件的超时时长（秒）
sendTestMailOnStartup=启动时是否发送一封测试邮件
log.console.level=控制台日志显示级别
log.file.level=文件记录日志级别
log.liveToFile=日志是否输出到文件
apiUrl=哔哩哔哩直播API
```

级别大小为：INFO > WARN > ERROR > LIVE  
默认的级别为：控制台输出全部日志（INFO），文件输出错误及直播的日志（ERROR）

Linux后台运行命令：
`
nohup java -jar .\BiliLiveNotifier.jar &
`

Windows随便挂在那里就行

## 测试
Windows11 + Zulu17，使用QQ邮箱，可以正常运行

其他情况未测试，有bug我也不管了，我能用就行（）