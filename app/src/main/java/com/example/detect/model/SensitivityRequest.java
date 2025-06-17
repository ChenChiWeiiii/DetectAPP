package com.example.detect.model;

public class SensitivityRequest {
    private String user_id;
    private int value;

    public SensitivityRequest(String user_id, int value) {
        this.user_id = user_id;
        this.value = value;
    }

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
