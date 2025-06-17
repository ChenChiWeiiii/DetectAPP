package com.example.detect;

import com.example.detect.model.EmailRequest;
import com.example.detect.model.PrecheckRequest;
import com.example.detect.model.ResetPasswordRequest;
import com.example.detect.model.SignInRequest;
import com.example.detect.model.SignUpRequest;
import com.example.detect.model.ReminderRequest;
import com.example.detect.model.SensitivityRequest;
import com.example.detect.model.SignInResponse;
import com.example.detect.model.VerifyCodeRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.PATCH;

public interface ApiService {

    // 登入
    @POST("/signin")
    Call<SignInResponse> signIn(@Body SignInRequest signInRequest);

    // 註冊
    @POST("/signup")
    Call<Void> signUp(@Body SignUpRequest request);

    // 更新 Reminder
    @PATCH("/reminder")
    Call<Void> updateReminder(@Body ReminderRequest reminderRequest);

    // 更新 Sensitivity
    @PATCH("/sensitivity")
    Call<Void> updateSensitivity(@Body SensitivityRequest sensitivityRequest);

    // 信箱驗證
    @POST("/verify-code")
    Call<Void> verifyCode(@Body VerifyCodeRequest request);

    @POST("/precheck")
    Call<Void> precheck(@Body PrecheckRequest request);

    @POST("/forget-password/request")
    Call<Void> sendForgetCode(@Body EmailRequest request);

    @POST("/forget-password/verify")
    Call<Void> verifyForgetCode(@Body VerifyCodeRequest request);

    @POST("/reset-password")
    Call<Void> resetPassword(@Body ResetPasswordRequest request);


}
