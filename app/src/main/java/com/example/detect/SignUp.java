package com.example.detect;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.detect.model.PrecheckRequest;
import com.example.detect.model.SignUpRequest;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.Color;

public class SignUp extends AppCompatActivity {

    private EditText etUserID, etAccount, etPassword, etCheckPassword;
    private View btnSubmit;      // LinearLayout btn_signup_signup
    private TextView btnBack;    // TextView tv_back

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        etUserID        = findViewById(R.id.et_signup_userID);
        etAccount       = findViewById(R.id.et_signup_email);
        etPassword      = findViewById(R.id.et_signup_password);
        etCheckPassword = findViewById(R.id.et_signup_checkpassword);
        btnSubmit       = findViewById(R.id.btn_signup_signup);
        btnBack         = findViewById(R.id.tv_back);

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(SignUp.this, SignIn.class));
            finish();
        });

        btnSubmit.setOnClickListener(v -> {
            String userId        = etUserID.getText().toString().trim();
            String email         = etAccount.getText().toString().trim();
            String password      = etPassword.getText().toString();
            String checkPassword = etCheckPassword.getText().toString();

            if (password.length() < 8 || password.length() > 16) {
                Toast.makeText(SignUp.this, "密碼長度需介於 8 到 16 個字元", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(checkPassword)) {
                Toast.makeText(SignUp.this, "密碼不一致", Toast.LENGTH_SHORT).show();
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(SignUp.this);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_vertify, null);
            builder.setView(dialogView);
            AlertDialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.show();

            TextView emailDisplay  = dialogView.findViewById(R.id.emailDisplay);
            EditText etCodeInput   = dialogView.findViewById(R.id.etCodeInput);
            TextView btnVerify     = dialogView.findViewById(R.id.btnVerify);
            TextView tvResend      = dialogView.findViewById(R.id.tvResend);
            TextView tvCountdown   = dialogView.findViewById(R.id.tvCountdown);
            ImageButton btnClose   = dialogView.findViewById(R.id.btnCloseDialog);

            tvResend.post(() -> {
                int width = tvResend.getWidth();
                Shader shader = new LinearGradient(
                        0, 0, width, 0,
                        new int[]{Color.parseColor("#A259FF"), Color.parseColor("#CF78FF")},
                        null,
                        Shader.TileMode.CLAMP
                );
                tvResend.getPaint().setShader(shader);
                tvResend.invalidate();
            });

            emailDisplay.setText("正在發送驗證碼到：" + email);
            btnVerify.setEnabled(false);
            tvResend.setEnabled(true);
            tvCountdown.setVisibility(View.GONE);

            ApiService api = RetrofitClient.getInstance().create(ApiService.class);

            api.precheck(new PrecheckRequest(userId, email)).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> resp) {
                    if (resp.isSuccessful()) {
                        emailDisplay.setText("驗證碼已寄至：" + email);
                        btnVerify.setEnabled(true);
                    } else {
                        Toast.makeText(SignUp.this, "註冊失敗：" + getErrorMessage(resp), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(SignUp.this, "連線失敗：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });

            btnClose.setOnClickListener(x -> dialog.dismiss());

            btnVerify.setOnClickListener(x -> {
                String code = etCodeInput.getText().toString().trim();
                if (code.isEmpty()) {
                    Toast.makeText(SignUp.this, "請輸入驗證碼", Toast.LENGTH_SHORT).show();
                    return;
                }

                // ✅ 直接進行註冊，不再呼叫 verifyCode API
                api.signUp(new SignUpRequest(userId, email, password, code)).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> c, Response<Void> r) {
                        if (r.isSuccessful()) {
                            Toast.makeText(SignUp.this, "註冊完成", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            startActivity(new Intent(SignUp.this, SignIn.class));
                            finish();
                        } else {
                            Toast.makeText(SignUp.this, "註冊失敗：" + getErrorMessage(r), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> c, Throwable t) {
                        Toast.makeText(SignUp.this, "註冊連線失敗：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });

            tvResend.setOnClickListener(x -> {
                tvResend.setEnabled(false);
                tvResend.setVisibility(View.GONE);
                tvCountdown.setVisibility(View.VISIBLE);
                startCountdown(tvCountdown, tvResend);

                api.precheck(new PrecheckRequest(userId, email)).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> resp) {
                        if (resp.isSuccessful()) {
                            Toast.makeText(SignUp.this, "已重新寄出驗證碼", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SignUp.this, "重發失敗：" + getErrorMessage(resp), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(SignUp.this, "連線失敗：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }

    private String getErrorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                JSONObject obj = new JSONObject(response.errorBody().string());
                return obj.getString("message");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response.message();
    }

    private void startCountdown(TextView tvCount, TextView tvResend) {
        new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long ms) {
                tvCount.setText((ms / 1000) + " 秒後可重新發送");
            }
            @Override
            public void onFinish() {
                tvCount.setVisibility(View.GONE);
                tvResend.setText("重新發送驗證碼");
                tvResend.setEnabled(true);
                tvResend.setVisibility(View.VISIBLE);
                // 在 startCountdown 的 onFinish() 裡加上
                tvResend.post(() -> {
                    int width = tvResend.getWidth();
                    Shader shader = new LinearGradient(
                            0, 0, width, 0,
                            new int[]{Color.parseColor("#A259FF"), Color.parseColor("#CF78FF")},
                            null,
                            Shader.TileMode.CLAMP
                    );
                    tvResend.getPaint().setShader(shader);
                    tvResend.invalidate();
                });

            }
        }.start();
    }
}
