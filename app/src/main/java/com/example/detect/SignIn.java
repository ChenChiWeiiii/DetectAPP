package com.example.detect;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.detect.model.EmailRequest;
import com.example.detect.model.ResetPasswordRequest;
import com.example.detect.model.SignInRequest;
import com.example.detect.model.SignInResponse;
import com.example.detect.model.VerifyCodeRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignIn extends AppCompatActivity {

    private EditText etAccount, etPassword;
    private View btnSignIn;        // 按鈕容器：LinearLayout
    private TextView btnSignUp, tvforgotPassword;    // 註冊跳轉：TextView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 清除舊的 prefs
        getSharedPreferences("user_prefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        // 檢查是否已登入
        SharedPreferences loginPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userId = loginPrefs.getString("user_id", null);
        if (userId != null) {
            // 已登入，直接進 MainActivity
            startActivity(new Intent(SignIn.this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_sign_in);

        // 用正確的 ID 與類型對應
        etAccount        = findViewById(R.id.et_signin_email);
        etPassword       = findViewById(R.id.et_signin_password);
        btnSignIn        = findViewById(R.id.btn_signin_signin);
        btnSignUp        = findViewById(R.id.tv_signin_signup);
        tvforgotPassword = findViewById(R.id.forgotPassword);

        // 跳轉到 SignUp 頁面
        btnSignUp.setOnClickListener(v -> {
            startActivity(new Intent(SignIn.this, SignUp.class));
        });

        // 跳出忘記密碼 dialog
        tvforgotPassword.setOnClickListener(v -> showForgetPasswordDialog());

        // 處理登入
        btnSignIn.setOnClickListener(v -> {
            String email    = etAccount.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(SignIn.this, "請輸入帳號和密碼", Toast.LENGTH_SHORT).show();
                return;
            }

            SignInRequest request = new SignInRequest(email, password);
            ApiService api = RetrofitClient
                    .getInstance()
                    .create(ApiService.class);

            api.signIn(request).enqueue(new Callback<SignInResponse>() {
                @Override
                public void onResponse(Call<SignInResponse> call, Response<SignInResponse> resp) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String userId = resp.body().getUserId();
                        Toast.makeText(SignIn.this, "登入成功：" + userId, Toast.LENGTH_SHORT).show();

                        // 存 user_id
                        getSharedPreferences("user_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("user_id", userId)
                                .apply();

                        startActivity(new Intent(SignIn.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(SignIn.this, "帳號或密碼錯誤", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<SignInResponse> call, Throwable t) {
                    Toast.makeText(SignIn.this, "連線失敗：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void showForgetPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(SignIn.this);
        View view = getLayoutInflater().inflate(R.layout.dialog_forget_password, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();

        EditText etEmail = view.findViewById(R.id.et_forget_email);
        EditText etCode = view.findViewById(R.id.et_forget_code);
        TextView emailDisplay = view.findViewById(R.id.forgetemailDisplay);
        Button btnSend = view.findViewById(R.id.btn_send_code);
        Button btnVerify = view.findViewById(R.id.btnforgetVerify);
        ImageButton btnClose = view.findViewById(R.id.btnCloseDialog);

        // 關閉忘記密碼
        btnClose.setOnClickListener(v -> dialog.dismiss());

        CountDownTimer[] timer = new CountDownTimer[1];
        final boolean[] canResend = {true};

        btnSend.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(SignIn.this, "請輸入信箱", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSend.setEnabled(false);
            btnSend.setAlpha(0.5f);

            emailDisplay.setText("正在發送驗證碼到：" + email);

            ApiService api = RetrofitClient.getInstance().create(ApiService.class);
            EmailRequest request = new EmailRequest(email);
            api.sendForgetCode(request).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(SignIn.this, "驗證碼已寄出", Toast.LENGTH_SHORT).show();
                        emailDisplay.setText("驗證碼已寄至：" + email);

                        new CountDownTimer(60000, 1000) {
                            public void onTick(long millisUntilFinished) {
                                btnSend.setText("請稍候 (" + millisUntilFinished / 1000 + " 秒)");
                            }

                            public void onFinish() {
                                btnSend.setEnabled(true);
                                btnSend.setAlpha(1.0f);
                                btnSend.setText("發送驗證碼");
                            }
                        }.start();

                    } else {
                        Toast.makeText(SignIn.this, "信箱尚未註冊", Toast.LENGTH_SHORT).show();
                        btnSend.setEnabled(true);
                        btnSend.setAlpha(1.0f);
                        btnSend.setText("發送驗證碼");
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(SignIn.this, "連線失敗：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSend.setEnabled(true);
                    btnSend.setAlpha(1.0f);
                    btnSend.setText("發送驗證碼");
                }
            });
        });


        btnVerify.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String code = etCode.getText().toString().trim();

            if (email.isEmpty() || code.isEmpty()) {
                Toast.makeText(SignIn.this, "請輸入信箱和驗證碼", Toast.LENGTH_SHORT).show();
                return;
            }

            ApiService api = RetrofitClient.getInstance().create(ApiService.class);
            VerifyCodeRequest request = new VerifyCodeRequest(email, code);

            api.verifyForgetCode(request).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        dialog.dismiss();
                        dialog.dismiss(); // 關掉驗證碼 dialog
                        showResetPasswordDialog(email); // 跳出密碼重設 dialog
                    } else {
                        Toast.makeText(SignIn.this, "驗證碼錯誤或已過期", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(SignIn.this, "連線失敗：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
    private void showResetPasswordDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(SignIn.this);
        View view = getLayoutInflater().inflate(R.layout.dialog_reset_password, null);
        builder.setView(view);
        AlertDialog resetDialog = builder.create();
        resetDialog.setCancelable(false);
        resetDialog.show();

        EditText etNewPwd = view.findViewById(R.id.et_new_password);
        EditText etConfirmPwd = view.findViewById(R.id.et_confirm_password);
        Button btnConfirm = view.findViewById(R.id.btn_confirm_reset);

        btnConfirm.setOnClickListener(v -> {
            String newPwd = etNewPwd.getText().toString().trim();
            String confirmPwd = etConfirmPwd.getText().toString().trim();

            if (newPwd.isEmpty() || confirmPwd.isEmpty()) {
                Toast.makeText(SignIn.this, "請輸入新密碼與確認密碼", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPwd.equals(confirmPwd)) {
                Toast.makeText(SignIn.this, "兩次密碼不一致", Toast.LENGTH_SHORT).show();
                return;
            }

            // 呼叫後端 API 更新密碼
            ApiService api = RetrofitClient.getInstance().create(ApiService.class);
            ResetPasswordRequest request = new ResetPasswordRequest(email, newPwd);
            api.resetPassword(request).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(SignIn.this, "密碼已成功重設，請重新登入", Toast.LENGTH_LONG).show();
                        resetDialog.dismiss();
                    } else {
                        Toast.makeText(SignIn.this, "密碼重設失敗", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(SignIn.this, "連線失敗：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        });
    }

}
