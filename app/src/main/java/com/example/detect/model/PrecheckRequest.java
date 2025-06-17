package com.example.detect.model;

public class PrecheckRequest {
    private String user_id;
    private String email;

    public PrecheckRequest(String user_id, String email) {
        this.user_id = user_id;
        this.email = email;
    }

    public String getUser_id() {
        return user_id;
    }

    public String getEmail() {
        return email;
    }
}
