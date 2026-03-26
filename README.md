# 📺 BiliLiveNotifier

将哔哩哔哩直播通知发送到邮箱的工具。

基于原项目 [FireworkRocket/BiliLiveSendToMail](https://github.com/FireworkRocket/BiliLiveSendToMail)，并根据个人需求进行了重构。

## ✨ 功能特性

- 📡 **定时检测**：实时监控 B 站主播是否开播。
- 📧 **邮件通知**：自动发送详细开播通知邮件，支持多收件人。
- 🔔 **Bark 推送**：支持 iOS Bark 推送（含时效性通知、点击跳转及封面展示）。
- 📊 **时长统计**：下播时自动计算并记录本次直播时长。
- 🖼️ **视觉增强**：Bark 及邮件推送均支持显示主播头像和用户名（通过第三方 API）。
- 💾 **智能缓存**：主播信息仅在缺失时通过第三方 API 获取一次，缓存至本地，有效节省 API 使用量。
- ⚙️ **灵活配置**：支持静默模式、日志等级自定义。
- 🧹 **自动清理**：定时清理过期日志文件，保持磁盘整洁。
- 🔄 **启动自检**：启动时可发送邮件或 Bark 测试信息，验证配置是否生效。

## 🚀 使用方法

首次运行时会自动生成配置文件 `config.properties`，示例如下：

```properties
# BiliLiveNotifier Configuration File
# Created at: Fri Mar 06 00:15:45 HKT 2026

liveIDs=123456,234567
apiUrl=https://api.live.bilibili.com/room/v1/Room/get_info?room_id=
retryIntervalSeconds=30
userInputTimeoutSeconds=5
log.console.level=INFO
log.file.level=ERROR
log.toFile=true
log.maxHistoryDays=30
email.enable=true
email.list=example1@mail.com,example2@mail.com
email.testOnStartup=true
smtp.host=smtp.qq.com
smtp.port=465
smtp.username=<smtp.username>
smtp.password=<smtp.password>
bark.enable=false
bark.url=https://api.day.app/your_key/
bark.testOnStartup=true
bark.pushOnEnd=true
```

## 📖 配置说明

| **配置项**                   | **说明**                     |
|---------------------------|----------------------------|
| `liveIDs`                 | 监控的直播间 ID，多个用逗号分隔          |
| `apiUrl`                  | B 站直播 API 地址               |
| `retryIntervalSeconds`    | 轮询检查间隔（秒）                  |
| `userInputTimeoutSeconds` | 启动时跳过测试邮件的等待时间（秒）          |
| `log.console.level`       | 控制台日志级别                    |
| `log.file.level`          | 文件日志级别                     |
| `log.toFile`              | 是否将日志输出到文件                 |
| `log.maxHistoryDays`      | 日志保留天数，过期的 `.log` 文件将被自动删除 |
| `email.enable`            | 邮件推送总开关 (`true`/`false`)   |
| `email.list`              | 接收通知的邮箱地址，多个用逗号分隔          |
| `email.testOnStartup`     | 启动时是否尝试发送测试邮件              |
| `smtp.*`                  | 发件箱 SMTP 服务器及身份验证配置        |
| `bark.enable`             | 是否启用 Bark 推送               |
| `bark.url`                | Bark 推送的 API 地址（含 Key）     |
| `bark.testOnStartup`      | 启动时是否尝试发送测试 Bark 通知        |
| `bark.pushOnEnd`          | 下播时是否推送 Bark 通知            |

📌 日志等级：`INFO > WARN > ERROR > LIVE`

- 控制台默认输出：`INFO`（全部）
- 文件默认输出：`ERROR`（包含 ERROR 与 LIVE）

日志文件会以日期为文件名输出至当前目录下的 `logs` 文件夹。

## 💾 本地缓存说明

本项目使用 **[UApiPro (uapis.cn)](https://uapis.cn/)** 提供的接口来获取主播公开资料，包括头像和用户名。

- **积分消耗**：约 4 积分/次。
- **免费额度**：访客用户每月约 1500 积分（足够支持数百名主播的首次抓取）。

为了提升加载速度并节省第三方 API 积分，程序会自动创建 `user_cache.properties` 文件用于存储用户信息。  
程序仅在缓存中找不到该 UID 的信息时，才会调用第三方接口。若主播更改了头像或昵称，程序**不会**实时同步。如需更新资料，请手动删除该文件中对应的行，或直接删除整个文件后重启程序。

## 🖥️ 运行方式

### Linux 服务部署 (推荐)

在 `/etc/systemd/system` 处新建 `bln.service`，添加如下内容，
其中 `YOUR_JAVA_HOME` 为你的 Java 目录，`/path/to/your/BiliLiveNotifier` 为 BiliLiveNotifier 所在的路径

```bash
[Unit]
Description=BiliLiveNotifier Service
After=network.target
StartLimitIntervalSec=0

[Service]
Type=simple
WorkingDirectory=/path/to/your/BiliLiveNotifier
ExecStart=/YOUR_JAVA_HOME/bin/java -jar path/to/your/BiliLiveNotifier.jar

Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

然后，执行 `systemctl daemon-reload` 重载配置，使用这些命令来管理程序：

- **启动**: `systemctl start bln`  
- **关闭**: `systemctl stop bln`  
- **重启**: `systemctl restart bln`  
- **开机自启**: `systemctl enable bln`  
- **取消开机自启**: `systemctl disable bln`  
- **查看状态**: `systemctl status bln`  
- **查看实时日志**: `journalctl -u bln -f`

### Linux (后台运行)

```bash
nohup java -jar BiliLiveNotifier.jar &
```

### Windows

直接运行 JAR 文件即可

```cmd
java -jar BiliLiveNotifier.jar
```

## 🧪 测试环境

- **系统**：Windows 11, Windows 10, fnOS 0.9.21
- **JDK**：Zulu 17, Zulu 21
- **接收端**: iOS (Bark App), QQ 邮箱, Outlook 邮箱

✅ 以上环境运行正常  
⚠️ 其他环境暂未测试
