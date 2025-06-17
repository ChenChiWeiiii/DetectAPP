package com.example.detect.model;

import com.google.gson.annotations.SerializedName;

public class SignInResponse {
    @SerializedName("message")
    private String message;

    @SerializedName("user_id")
    private String userId;

    public String getMessage() {
        return message;
    }

    public String getUserId() {
        return userId;
    }
}
