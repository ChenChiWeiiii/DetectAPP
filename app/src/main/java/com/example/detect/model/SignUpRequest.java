package com.example.detect.model;

public class SignUpRequest {
    private String user_id;
    private String email;
    private String password;
    private String code;

    // Constructor
    public SignUpRequest(String user_id, String email, String password, String code) {
        this.user_id = user_id;
        this.email = email;
        this.password = password;
        this.code = code;
    }

    // Getters
    public String getUser_id() {
        return user_id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
    public String getCode() { return code; }
}
