# 📺 BiliLiveNotifier

将哔哩哔哩直播通知发送到邮箱与 Bark 的自动化工具。

基于原项目 [FireworkRocket/BiliLiveSendToMail](https://github.com/FireworkRocket/BiliLiveSendToMail)，并根据个人需求进行了重构。

## ✨ 功能特性

- 📡 **定时检测**：实时监控 B 站主播是否开播。
- 📧 **邮件通知**：自动发送详细开播通知邮件（含封面图），支持多收件人。
- 🔔 **Bark 推送**：支持 iOS Bark 推送（含时效性通知、点击跳转及头像和封面的展示）。
- 👥 **分组推送**：**[v1.4.0]** 支持按分组配置，将不同直播间映射到不同的邮件/Bark 接收方，各分组独立控制推送开关与接收人。
- 🗂️ **Bark 通知分组**：**[v1.4.0]** 推送时按主播昵称自动归组（URL 编码），Bark App 中通知展示更清爽。
- 🖼️ **视觉增强**：通过第三方 API^，Bark 及邮件推送均可以显示主播头像和用户名。
- 💾 **智能缓存**：主播信息仅在缺失时通过第三方 API 获取一次，缓存至本地，有效节省 API 使用量。
- 📊 **时长统计**：下播时自动计算并记录本次直播时长。
- 🎨 **彩色日志**：**[v1.3.2+]** 控制台中能根据不同类型（开播、推送、系统）分类彩色输出。
- 🏷️ **标签化日志**：**[v1.3.2+]** 使用标签集合过滤，支持精细化控制哪些信息显示在控制台或写入的文件。
- 🧹 **自动清理**：定时清理过期日志文件，保持磁盘整洁。
- 🔄 **启动自检**：启动时可发送邮件或 Bark 测试信息，验证配置是否生效。

## 🚀 使用方法

首次运行时会自动生成配置文件 `config.properties`，示例如下：

```properties
# BiliLiveNotifier Configuration File
# Created at: Sun Mar 29 14:20:32 HKT 2026

apiUrl=https://api.live.bilibili.com/room/v1/Room/get_info?room_id=
retryIntervalSeconds=30
userInputTimeoutSeconds=5
log.console.level=ALL
log.file.level=SYSTEM,LIVE,PUSH,WARN,ERROR
log.maxHistoryDays=30
smtp.host=smtp.qq.com
smtp.port=465
smtp.username=<smtp.username>
smtp.password=<smtp.password>

# ---- 分组配置：按房间划分独立的推送渠道与接收人（至少配置一个分组） ----
group.1.rooms=123456,234567
group.1.email.enable=true
group.1.email.list=example1@mail.com,example2@mail.com
group.1.email.testOnStartup=true
group.1.email.pushOnEnd=true
group.1.bark.enable=false
group.1.bark.key=<bark.key>
group.1.bark.testOnStartup=true
group.1.bark.pushOnEnd=true

# ---- 分组 2 示例（可选）：多数用户只需一个分组，多分组时取消注释并按需填写 ----
# group.2.rooms=32562724
# group.2.email.enable=false
# group.2.email.list=user@example.com
# group.2.email.testOnStartup=true
# group.2.email.pushOnEnd=true
# group.2.bark.enable=true
# group.2.bark.key=KeyForGroup2
# group.2.bark.testOnStartup=true
# group.2.bark.pushOnEnd=true
```

## 📖 配置说明

> **[v1.4.0]** 旧版全局推送配置（`roomIDs`、`email.*`、`bark.*`）已废弃，推送配置统一使用 `group.N.*` 分组格式。

### 基础配置

| **配置项**                   | **说明**                         |
|---------------------------|--------------------------------|
| `apiUrl`                  | B 站直播 API 地址                   |
| `retryIntervalSeconds`    | 轮询检查间隔（秒）                      |
| `userInputTimeoutSeconds` | 启动时跳过测试邮件的等待时间（秒）              |
| `log.console.level`       | 控制台显示的日志标签                     |
| `log.file.level`          | 文件输出的日志标签                      |
| `log.maxHistoryDays`      | 日志保留天数，过期的日志文件将被自动删除           |
| `smtp.*`                  | SMTP 服务器及身份验证配置                |

### 分组配置（`group.N.*`，N 为分组编号，从 1 开始）

| **配置项**                     | **说明**                                        |
|-----------------------------|-----------------------------------------------|
| `group.N.rooms`             | 该分组监控的直播间 ID，多个用英文逗号分隔                       |
| `group.N.email.enable`      | 该分组是否启用邮件推送                                 |
| `group.N.email.list`        | 该分组的收件邮箱地址，多个用英文逗号分隔                        |
| `group.N.email.testOnStartup` | 该分组启动时是否尝试发送测试邮件                          |
| `group.N.email.pushOnEnd`   | 该分组下播时是否推送邮件                                |
| `group.N.bark.enable`       | 该分组是否启用 Bark 推送                             |
| `group.N.bark.key`          | 该分组的 Bark Key（自动拼接为 `https://api.day.app/{key}/`） |
| `group.N.bark.url`          | （可选）自建 Bark 服务完整地址（含 Key），优先于 `bark.key`      |
| `group.N.bark.testOnStartup`  | 该分组启动时是否尝试发送测试 Bark 通知                   |
| `group.N.bark.pushOnEnd`    | 该分组下播时是否推送 Bark 通知                           |

## 📌 日志标签说明 (v1.3.2+)

不再使用 `INFO > WARN` 的优先级，而是通过标签名来匹配（括号中为显示的颜色）：

- **`SYSTEM`** (BLUE)：程序启动、配置加载等。
- **`CHECK`** (DEFAULT)：检查房间状态。
- **`LIVE`** (GREEN)：开播、下播。
- **`PUSH`** (CYAN)：推送行为。
- **`WARN`** (YELLOW)：非致命异常。
- **`ERROR`** (RED)：致命错误。
- **`ALL`**：开启所有标签。

日志文件会以日期为文件名输出至当前目录下的 `logs` 文件夹，如`2026-03-29.log`。

## 💾 本地缓存与 API 说明

^：本项目使用 **[UApiPro (uapis.cn)](https://uapis.cn/)** 获取主播头像和用户名。（不是广告！）

- **积分消耗**：约 4 积分/次。
- **免费额度**：访客用户每月约 1500 积分（完全足够支持数百名主播的首次抓取！）。

为了提升载入速度并节省 API 积分，程序会自动创建 `user_cache.properties` 文件用于存储用户信息。  
程序仅在缓存中找不到该房间（roomID）的信息时，才会调用第三方接口。**[v1.4.0]** 启动时会自动清理不再监控的房间缓存条目；若主播更改了头像或昵称，程序**不会**实时同步。如需更新资料，**请手动删除该文件中对应的行后重启程序**。

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

然后，执行 `systemctl daemon-reload` 重载配置。使用这些命令来管理：

- **启动**: `systemctl start bln`  
- **关闭**: `systemctl stop bln`  
- **重启**: `systemctl restart bln`  
- **开机自启**: `systemctl enable bln`  
- **取消开机自启**: `systemctl disable bln`  
- **查看状态**: `systemctl status bln`  
- **查看实时日志**: `journalctl -u bln -f`

### Linux 后台运行

```bash
nohup java -jar BiliLiveNotifier.jar &
```

### Windows

终端直接运行 JAR 文件即可

```cmd
java -jar BiliLiveNotifier.jar
```

## 🧪 测试环境

- **发送端**：Windows 11, Windows 10, fnOS
- **接收端**：iOS (Bark App), QQ 邮箱, Outlook 邮箱
- **JDK**：Zulu 17, Zulu 21

✅ 以上环境运行正常  
⚠️ 其他环境暂未测试
