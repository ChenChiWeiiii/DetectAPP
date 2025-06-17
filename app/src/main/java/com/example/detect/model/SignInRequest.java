package com.example.detect.model;

public class SignInRequest {
    private String email;
    private String password;

    // Constructor
    public SignInRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    // Getters
    public String getEmail() {
        return email;
    }


    public String getPassword() {
        return password;
    }
}
