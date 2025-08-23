# 📺 BiliLiveNotifier

将哔哩哔哩直播开播通知发送到邮箱的工具  

基于原项目 [FireworkRocket/BiliLiveSendToMail](https://github.com/FireworkRocket/BiliLiveSendToMail)，并根据个人需求进行了修改和优化。

---

## ✨ 功能特性
- 📡 定时检测 B 站主播是否开播  
- 📧 自动发送开播通知邮件到指定邮箱  
- ⚙️ 可自定义多个主播、多邮箱接收人  
- 📝 支持控制台与文件日志输出，等级可配置  
- 🔄 启动时可发送测试邮件，验证配置是否正确  

---

## 🚀 使用方法

首次运行时会自动生成配置文件 `config.properties`，示例如下：

```properties
# Configuration file example
# emailList: List of recipient email addresses, separated by commas
# Example: example1@example.com,example2@example.com
# liveIDs: List of recipient liveIDs, separated by commas too
# Example: XXXXXX,XXXXXX,XXXXX
liveIDs=XXXXXX,XXXXXX,XXXXXX
emailList=example1@example.com,example2@example.com
smtp.host=<smtpHost>
smtp.port=<smtpPort>
smtp.username=<smtpUsername>
smtp.password=<smtpPassword>
retryIntervalSeconds=10
userInputTimeoutSeconds=5
sendTestMailOnStartup=true
log.console.level=INFO
log.file.level=ERROR
log.liveToFile=true
apiUrl=https://api.live.bilibili.com/room/v1/Room/get_info?room_id=
````

---

## 📖 配置说明

| 配置项                       | 说明                      |
|---------------------------|-------------------------|
| `liveIDs`                 | 要监听的主播房间号，多个用英文逗号分隔     |
| `emailList`               | 接收通知的邮箱，多个用英文逗号分隔       |
| `smtp.host`               | SMTP 服务器地址              |
| `smtp.port`               | SMTP 端口号                |
| `smtp.username`           | SMTP 用户名                |
| `smtp.password`           | SMTP 密码                 |
| `retryIntervalSeconds`    | 未开播时多久重试（秒）             |
| `userInputTimeoutSeconds` | 检测到开播后，手动跳过发送邮件的超时时长（秒） |
| `sendTestMailOnStartup`   | 启动时是否发送测试邮件             |
| `log.console.level`       | 控制台日志级别                 |
| `log.file.level`          | 文件日志级别                  |
| `log.liveToFile`          | 是否将日志输出到文件              |
| `apiUrl`                  | B 站直播 API 地址            |

📌 日志等级：`INFO > WARN > ERROR > LIVE`

* 控制台默认输出：`INFO`（全部）
* 文件默认输出：`ERROR`（包含 ERROR 与 LIVE）

---

## 🖥️ 运行方式

### Linux 后台运行

```bash
nohup java -jar BiliLiveNotifier.jar &
```

### Windows

直接运行 JAR 文件即可
```
java -jar BiliLiveNotifier.jar
```

---

## 🧪 测试环境

* 系统：Windows 11，Windows 10
* JDK：Zulu 17，Zulu 21
* 邮箱：QQ 邮箱

✅ 以上环境运行正常
⚠️ 其他环境暂未测试

---
