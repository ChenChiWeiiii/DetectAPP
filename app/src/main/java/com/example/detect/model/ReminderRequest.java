package com.example.detect.model;

public class ReminderRequest {
    private String user_id;
    private int voice_reminder;
    private int shock_reminder;

    public ReminderRequest(String user_id, int voice_reminder, int shock_reminder) {
        this.user_id = user_id;
        this.voice_reminder = voice_reminder;
        this.shock_reminder = shock_reminder;
    }

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public int getVoice_reminder() {
        return voice_reminder;
    }

    public void setVoice_reminder(int voice_reminder) {
        this.voice_reminder = voice_reminder;
    }

    public int getShock_reminder() {
        return shock_reminder;
    }

    public void setShock_reminder(int shock_reminder) {
        this.shock_reminder = shock_reminder;
    }
}
