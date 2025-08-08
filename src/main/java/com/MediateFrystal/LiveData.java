package com.MediateFrystal;

import com.google.gson.annotations.SerializedName;

public class LiveData {
    @SerializedName("live_status")
    private int liveStatus;
    @SerializedName("title")
    private String title;
    @SerializedName("uid")
    private long uid;
    @SerializedName("room_id")
    private String roomID;

    @SerializedName("user_cover")
    private String userCover;

    public int getLiveStatus() {
        return liveStatus;
    }

    public String getTitle() {
        return title;
    }

    public long getUid() {
        return uid;
    }

    public String getRoomID() {
        return roomID;
    }
    public String getUserCover() {
        return userCover;
    }
    public void setLiveStatus(int liveStatus) {
        this.liveStatus = liveStatus;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    public void setRoomID(String roomID) {
        this.roomID = roomID;
    }

    public void setUserCover(String userCover) {
        this.userCover = userCover;
    }
}
