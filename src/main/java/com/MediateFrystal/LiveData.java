package com.MediateFrystal;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LiveData {
    @JsonProperty("live_status")
    private int liveStatus;

    @JsonProperty("title")
    private String title;

    @JsonProperty("uid")
    private String uid;

    @JsonProperty("room_id")
    private String roomID;

    @JsonProperty("user_cover")
    private String userCover;

    @JsonProperty("user_name")
    private String userName;

    private final long startTime;
    public LiveData() {
        this.startTime = System.currentTimeMillis();
    }

    public int getLiveStatus() {
        return liveStatus;
    }
    public long getStartTime() {
        return startTime;
    }
    public String getTitle() {
        return title;
    }
    public String getUid() {
        return uid;
    }
    public String getRoomID() {
        return roomID;
    }
    public String getUserCover() {
        return userCover;
    }
    public String getUserName() {
        return (userName != null && !userName.isEmpty()) ? userName : "房间 " + roomID;
    }
    public void setLiveStatus(int liveStatus) {
        this.liveStatus = liveStatus;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public void setUid(String uid) {
        this.uid = uid;
    }
    public void setRoomID(String roomID) {
        this.roomID = roomID;
    }
    public void setUserCover(String userCover) {
        this.userCover = userCover;
    }
    public void setUserName(String userName) {
        this.userName = userName;
    }
}
