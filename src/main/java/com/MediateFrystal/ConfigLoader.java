package com.MediateFrystal;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigLoader {
    private final String configFile = "config.properties";
    private final Properties conf;

    private int retryIntervalSeconds;
    private int userInputTimeoutSeconds;

    private String consoleLevel;
    private String fileLevel;
    private int maxHistoryDays;

    private String apiUrl;

    /** 分组配置列表与 roomID -> 分组 的映射 */
    private final List<Group> groups = new ArrayList<>();
    private final Map<String, Group> groupByRoom = new HashMap<>();
    private static final Pattern GROUP_ROOMS_PATTERN = Pattern.compile("^group\\.(\\d+)\\.rooms$");

    public ConfigLoader() {
        this.conf = new Properties();
        loadConfig();

        LogUtil.sys("配置加载完成，正在监控 " + getRoomIDs().size() + " 个直播间" +
                "；分组数量: " + groups.size() +
                "；检查间隔: " + retryIntervalSeconds + "s" +
                "；日志保留: " + maxHistoryDays + "天");
    }

    public void loadConfig() {
        Path path = Paths.get(configFile);
        if (!Files.exists(path)) {
            LogUtil.warn("未找到配置文件，正在创建默认配置...");
            createDefaultConfig(path);
            LogUtil.sys("配置文件已创建: " + path.toAbsolutePath() + " 请在配置完成后再次启动程序 (^_^) ~ ");
            System.exit(0);
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            conf.load(reader);
        } catch (IOException e) {
            LogUtil.err("读取配置文件失败: " + e.getMessage());
        }

        checkConfigFormat();
        warnDeprecatedGlobals();

        this.apiUrl = conf.getProperty("apiUrl", "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=");
        this.retryIntervalSeconds = getIntProperty("retryIntervalSeconds", 30);
        this.userInputTimeoutSeconds = getIntProperty("userInputTimeoutSeconds", 5);

        this.consoleLevel = conf.getProperty("log.console.level", "ALL");
        this.fileLevel = conf.getProperty("log.file.level", "SYSTEM,LIVE,PUSH,WARN,ERROR");
        LogUtil.setConsoleTags(this.consoleLevel);
        LogUtil.setFileTags(this.fileLevel);
        this.maxHistoryDays = getIntProperty("log.maxHistoryDays", 30);

        EmailSender.setSmtpConfig(
                conf.getProperty("smtp.host", "smtp.qq.com"),
                conf.getProperty("smtp.port", "465"),
                conf.getProperty("smtp.username", ""),
                conf.getProperty("smtp.password", "")
        );

        parseGroups();
    }

    /**
     * 旧版全局推送配置（roomIDs / email.* / bark.*）已废弃：
     * 配置文件中必须显式包含 group.N.* 分组配置，否则提示迁移后退出程序
     */
    private void checkConfigFormat() {
        boolean hasGroup = false;
        for (String key : conf.stringPropertyNames()) {
            if (key.startsWith("group.")) {
                hasGroup = true;
                break;
            }
        }
        if (hasGroup) return;

        LogUtil.err("未检测到 group.N.* 分组配置。旧版全局推送配置已废弃，请将配置文件迁移为分组格式后重新启动：");
        LogUtil.warn("  roomIDs=...              -> group.1.rooms=...");
        LogUtil.warn("  email.enable=...         -> group.1.email.enable=...");
        LogUtil.warn("  email.list=...           -> group.1.email.list=...");
        LogUtil.warn("  email.testOnStartup=...  -> group.1.email.testOnStartup=...");
        LogUtil.warn("  email.pushOnEnd=...      -> group.1.email.pushOnEnd=...");
        LogUtil.warn("  bark.enable=...          -> group.1.bark.enable=...");
        LogUtil.warn("  bark.url / bark.key=...  -> group.1.bark.url 或 group.1.bark.key");
        LogUtil.warn("  bark.testOnStartup=...   -> group.1.bark.testOnStartup=...");
        LogUtil.warn("  bark.pushOnEnd=...       -> group.1.bark.pushOnEnd=...");
        LogUtil.sys("apiUrl、retryIntervalSeconds、log.*、smtp.* 等基础配置保持不变，无需迁移。");
        System.exit(0);
    }

    /**
     * 配置中同时存在 group.N.* 与已废弃的全局推送键时，忽略全局键并提示
     */
    private void warnDeprecatedGlobals() {
        List<String> deprecated = new ArrayList<>();
        for (String key : conf.stringPropertyNames()) {
            if (key.equals("roomIDs") || key.startsWith("email.") || key.startsWith("bark.")) {
                deprecated.add(key);
            }
        }
        if (!deprecated.isEmpty()) {
            LogUtil.warn("检测到已废弃的全局配置项 " + deprecated + "，已忽略，请改用 group.N.* 格式。");
        }
    }

    private int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(conf.getProperty(key));
        } catch (NumberFormatException e) {
            LogUtil.err("配置项 [" + key + "] 格式非法，已使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    private boolean isEmailListBlank(Set<String> list) {
        return list == null || list.isEmpty() || list.stream().allMatch(String::isBlank);
    }

    /**
     * 解析分组配置（group.N.rooms / group.N.email.* / group.N.bark.*）
     * 分组内未显式配置的项使用安全默认值；配置了 bark.key 时自动拼接 Bark 服务地址
     */
    private void parseGroups() {
        groups.clear();
        groupByRoom.clear();

        Set<Integer> indices = new TreeSet<>();
        for (String key : conf.stringPropertyNames()) {
            Matcher matcher = GROUP_ROOMS_PATTERN.matcher(key);
            if (matcher.matches()) {
                indices.add(Integer.parseInt(matcher.group(1)));
            }
        }

        for (Integer index : indices) {
            String prefix = "group." + index + ".";
            List<String> groupRooms = new ArrayList<>();
            for (String room : conf.getProperty(prefix + "rooms", "").split(",")) {
                String trimmed = room.trim();
                if (!trimmed.isEmpty()) groupRooms.add(trimmed);
            }
            if (groupRooms.isEmpty()) {
                LogUtil.warn("分组 [" + index + "] 未配置任何房间，已忽略该分组。");
                continue;
            }

            Group group = new Group(String.valueOf(index), groupRooms);
            group.setEmailEnable(Boolean.parseBoolean(conf.getProperty(prefix + "email.enable", "false")));
            String rawEmailList = conf.getProperty(prefix + "email.list", "");
            group.setEmailList(rawEmailList.isEmpty()
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(Arrays.asList(rawEmailList.split(","))));
            if (group.isEmailEnable() && isEmailListBlank(group.getEmailList())) {
                LogUtil.warn("分组 [" + group.getId() + "] 邮件推送已开启，但 email.list 为空，邮件将无法发送，请检查配置。");
            }
            group.setEmailTestOnStartup(Boolean.parseBoolean(conf.getProperty(prefix + "email.testOnStartup", "false")));
            group.setEmailPushOnEnd(Boolean.parseBoolean(conf.getProperty(prefix + "email.pushOnEnd", "true")));
            group.setBarkEnable(Boolean.parseBoolean(conf.getProperty(prefix + "bark.enable", "false")));

            String groupBarkUrl = conf.getProperty(prefix + "bark.url");
            if (groupBarkUrl == null || groupBarkUrl.isEmpty()) {
                String barkKey = conf.getProperty(prefix + "bark.key", "").trim();
                groupBarkUrl = barkKey.isEmpty() ? null : "https://api.day.app/" + barkKey + "/";
            }
            group.setBarkUrl(groupBarkUrl);
            if (group.isBarkEnable() && (group.getBarkUrl() == null || group.getBarkUrl().isEmpty())) {
                LogUtil.warn("分组 [" + group.getId() + "] Bark 推送已开启，但未配置 bark.url 或 bark.key，Bark 推送将无法发送，请检查配置。");
            }
            group.setBarkTestOnStartup(Boolean.parseBoolean(conf.getProperty(prefix + "bark.testOnStartup", "false")));
            group.setBarkPushOnEnd(Boolean.parseBoolean(conf.getProperty(prefix + "bark.pushOnEnd", "true")));

            groups.add(group);
            for (String room : groupRooms) {
                Group previous = groupByRoom.putIfAbsent(room, group);
                if (previous != null) {
                    LogUtil.warn("房间 [" + room + "] 同时存在于分组 [" + previous.getId() + "] 与 [" + group.getId() + "] 中，将使用分组 [" + previous.getId() + "] 的配置。");
                }
            }

            LogUtil.sys("分组 [" + group.getId() + "] 已加载: " + groupRooms.size() + " 个房间" +
                    "；邮件: " + (group.isEmailEnable() ? "开启" : "关闭") +
                    "；Bark: " + (group.isBarkEnable() ? "开启" : "关闭"));
        }
    }

    /**
     * 按固定顺序、分组段落排版生成默认配置文件
     * （group.2.* 以注释形式提供多分组模板，多数用户只需一个分组）
     */
    private void createDefaultConfig(Path path) {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("# BiliLiveNotifier Configuration File");
            writer.newLine();
            writer.write("# Created at: " + new java.util.Date());
            writer.newLine();
            writer.newLine();

            writer.write("apiUrl=https://api.live.bilibili.com/room/v1/Room/get_info?room_id=");
            writer.newLine();
            writer.write("retryIntervalSeconds=30");
            writer.newLine();
            writer.write("userInputTimeoutSeconds=5");
            writer.newLine();
            writer.newLine();

            writer.write("log.console.level=ALL");
            writer.newLine();
            writer.write("log.file.level=SYSTEM,LIVE,PUSH,WARN,ERROR");
            writer.newLine();
            writer.write("log.maxHistoryDays=30");
            writer.newLine();
            writer.newLine();

            writer.write("smtp.host=smtp.qq.com");
            writer.newLine();
            writer.write("smtp.port=465");
            writer.newLine();
            writer.write("smtp.username=<smtp.username>");
            writer.newLine();
            writer.write("smtp.password=<smtp.password>");
            writer.newLine();
            writer.newLine();

            writer.write("# ---- 分组配置：按房间划分独立的推送渠道与接收人（至少配置一个分组） ----");
            writer.newLine();
            writer.write("group.1.rooms=123456,234567");
            writer.newLine();
            writer.write("group.1.email.enable=true");
            writer.newLine();
            writer.write("group.1.email.list=example1@mail.com,example2@mail.com");
            writer.newLine();
            writer.write("group.1.email.testOnStartup=true");
            writer.newLine();
            writer.write("group.1.email.pushOnEnd=true");
            writer.newLine();
            writer.write("group.1.bark.enable=false");
            writer.newLine();
            writer.write("group.1.bark.key=<bark.key>");
            writer.newLine();
            writer.write("group.1.bark.testOnStartup=true");
            writer.newLine();
            writer.write("group.1.bark.pushOnEnd=true");
            writer.newLine();
            writer.newLine();

            writer.write("# ---- 分组 2 示例（可选）：多数用户只需一个分组，多分组时取消注释并按需填写 ----");
            writer.newLine();
            writer.write("# group.2.rooms=32562724");
            writer.newLine();
            writer.write("# group.2.email.enable=false");
            writer.newLine();
            writer.write("# group.2.email.list=user@example.com");
            writer.newLine();
            writer.write("# group.2.email.testOnStartup=true");
            writer.newLine();
            writer.write("# group.2.email.pushOnEnd=true");
            writer.newLine();
            writer.write("# group.2.bark.enable=true");
            writer.newLine();
            writer.write("# group.2.bark.key=KeyForGroup2");
            writer.newLine();
            writer.write("# group.2.bark.testOnStartup=true");
            writer.newLine();
            writer.write("# group.2.bark.pushOnEnd=true");
            writer.newLine();
        } catch (IOException e) {
            LogUtil.err("创建配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 返回某房间所属的分组；房间未配置在任何分组中时返回 null
     */
    public Group getGroupForRoom(String roomID) {
        return (roomID == null) ? null : groupByRoom.get(roomID.trim());
    }

    public List<Group> getGroups() { return groups; }

    // --- Getters ---
    public List<String> getRoomIDs() {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        for (Group group : groups) all.addAll(group.getRooms());
        return new ArrayList<>(all);
    }
    public String getApiUrl() { return apiUrl; }
    public int getRetryIntervalSeconds() { return retryIntervalSeconds; }
    public int getUserInputTimeoutSeconds() { return userInputTimeoutSeconds; }
    public int getMaxHistoryDays() { return maxHistoryDays; }

    /**
     * 分组配置：一组房间及其独立的推送渠道与接收人
     */
    public static class Group {
        private final String id;
        private final List<String> rooms;
        private boolean emailEnable;
        private Set<String> emailList;
        private boolean emailTestOnStartup;
        private boolean emailPushOnEnd;
        private boolean barkEnable;
        private String barkUrl;
        private boolean barkTestOnStartup;
        private boolean barkPushOnEnd;

        public Group(String id, List<String> rooms) {
            this.id = id;
            this.rooms = rooms;
        }

        public String getId() { return id; }
        public List<String> getRooms() { return rooms; }
        public boolean isEmailEnable() { return emailEnable; }
        public void setEmailEnable(boolean emailEnable) { this.emailEnable = emailEnable; }
        public Set<String> getEmailList() { return emailList; }
        public void setEmailList(Set<String> emailList) { this.emailList = emailList; }
        public boolean isEmailTestOnStartup() { return emailTestOnStartup; }
        public void setEmailTestOnStartup(boolean emailTestOnStartup) { this.emailTestOnStartup = emailTestOnStartup; }
        public boolean isEmailPushOnEnd() { return emailPushOnEnd; }
        public void setEmailPushOnEnd(boolean emailPushOnEnd) { this.emailPushOnEnd = emailPushOnEnd; }
        public boolean isBarkEnable() { return barkEnable; }
        public void setBarkEnable(boolean barkEnable) { this.barkEnable = barkEnable; }
        public String getBarkUrl() { return barkUrl; }
        public void setBarkUrl(String barkUrl) { this.barkUrl = barkUrl; }
        public boolean isBarkTestOnStartup() { return barkTestOnStartup; }
        public void setBarkTestOnStartup(boolean barkTestOnStartup) { this.barkTestOnStartup = barkTestOnStartup; }
        public boolean isBarkPushOnEnd() { return barkPushOnEnd; }
        public void setBarkPushOnEnd(boolean barkPushOnEnd) { this.barkPushOnEnd = barkPushOnEnd; }
    }
}
