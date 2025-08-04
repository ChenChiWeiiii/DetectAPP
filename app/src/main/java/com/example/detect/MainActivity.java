package com.example.detect;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.detect.model.ReminderRequest;
import com.example.detect.model.SensitivityRequest;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.graphics.ImageFormat;

import android.graphics.YuvImage;

import org.opencv.core.Rect;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;

import android.speech.tts.TextToSpeech;

import android.app.NotificationChannel;
import android.app.NotificationManager;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private OverlayView overlayView;
    private TextView tvSpeed;
    private Bitmap currentBitmap = null;
    private static final int PERMISSION_CODE = 100;
    private DetectorMain detector;
    private int sensitivityLevel = 2;
    private boolean isVoiceEnabled = true;
    private boolean isVibrationEnabled = true;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "AppSettings";
    private String userId;
    private LocationManager locationManager;
    private static final float DISTANCE_SCALING_FACTOR = 400.0f;
    private static final long REMINDER_COOLDOWN_MS = 3000;
    private long lastVibrationTime = 0;
    private long lastSpeechTime = 0;
    private TextToSpeech textToSpeech;

    private static final Scalar LOWER_RED1 = new Scalar(0, 70, 50);
    private static final Scalar UPPER_RED1 = new Scalar(10, 255, 255);
    private static final Scalar LOWER_RED2 = new Scalar(160, 70, 50);
    private static final Scalar UPPER_RED2 = new Scalar(180, 255, 255);
    private static final Scalar LOWER_YELLOW = new Scalar(15, 100, 100);
    private static final Scalar UPPER_YELLOW = new Scalar(35, 255, 255);
    private static final Scalar LOWER_GREEN = new Scalar(40, 50, 50);
    private static final Scalar UPPER_GREEN = new Scalar(90, 255, 255);





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences loginPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = loginPrefs.getString("user_id", null);
        if (userId == null) {
            Intent intent = new Intent(MainActivity.this, SignIn.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlay);
        tvSpeed = findViewById(R.id.tv_speed);
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadSettingsFromPreferences();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
            startCamera();
            startLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.POST_NOTIFICATIONS
                    }, PERMISSION_CODE);}

        try {
            detector = new DetectorMain(getAssets(), "best_float16.tflite", "All");

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (detector == null ) {//
            Toast.makeText(this, "模型載入失敗，請確認 assets 資料夾內有 best_float16.tflite", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(java.util.Locale.TAIWAN); // 使用中文語音
            } else {
                Toast.makeText(this, "語音初始化失敗", Toast.LENGTH_SHORT).show();
            }
        });

        createNotificationChannel();
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettingsDialog());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "alert_channel",                     // Channel ID
                    "行人/紅綠燈提醒",                     // 名稱
                    NotificationManager.IMPORTANCE_HIGH  // 優先權
            );
            channel.setDescription("用於警示行人、紅綠燈等事件");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void sendAlertNotification(String title, String content) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "alert_channel")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // 替換成你自己的 icon
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // 檢查權限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } else {
            Log.w("通知權限", "尚未取得通知權限，無法顯示通知");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
                previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
                preview.setTargetRotation(previewView.getDisplay().getRotation());
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setTargetRotation(previewView.getDisplay().getRotation());

                analysis.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
                    // 1. 先把 ImageProxy 轉成 Bitmap
                    Bitmap bitmap = imageToBitmap(image);
                    currentBitmap = bitmap;

                    // 2. 模型回傳的 recognitions，假設它們的 RectF 是 bitmap 尺寸之座標
                    List<DetectorMain.Recognition> rawResults =
                            detector.detect(bitmap, bitmap.getWidth(), bitmap.getHeight());

                    // 3. 計算 PreviewView 上實際顯示影像的縮放、偏移
                    float viewW = previewView.getWidth();
                    float viewH = previewView.getHeight();
                    float imgW  = bitmap.getWidth();
                    float imgH  = bitmap.getHeight();
                    // FIT_CENTER：維持長寬比，整張圖完整顯示
                    float scale = Math.min(viewW / imgW, viewH / imgH);
                    float dx = (viewW  - imgW * scale) / 2f;
                    float dy = (viewH  - imgH * scale) / 2f;

                    // 4. 把每個 box 轉成 View 座標
                    List<DetectorMain.Recognition> viewResults = new ArrayList<>();
                    for (DetectorMain.Recognition r : rawResults) {
                        RectF b = r.getLocation();
                        RectF vb = new RectF(
                                b.left   * scale + dx,
                                b.top    * scale + dy,
                                b.right  * scale + dx,
                                b.bottom * scale + dy
                        );
                        r.setLocation(vb);
                        viewResults.add(r);
                    }

                    // 5. 丟到 OverlayView 畫
                    overlayView.setResults(viewResults);
                    processPedestrianLogic(viewResults);
                    image.close();
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private Bitmap imageToBitmap(ImageProxy image) {
        // 1. 先拿到旋轉角度
        int rotation = image.getImageInfo().getRotationDegrees();

        // 2. NV21 -> JPEG -> 原始 Bitmap（raw）
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21,
                image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(
                new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()),
                100,
                out
        );

        byte[] jpeg = out.toByteArray();
        Bitmap raw = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);

        // 3. 用 Matrix 把 raw 旋轉到正確方向
        Matrix m = new Matrix();
        m.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(
                raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);

        // 4. 回傳糾正後的 Bitmap
        return rotated;
    }



    private void startLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            float speed = location.getSpeed() * 3.6f;
            tvSpeed.setText(String.format("時速：%.1f km/h", speed));
        }
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    private void loadSettingsFromPreferences() {
        sensitivityLevel = sharedPreferences.getInt("sensitivityLevel", 2);
        isVoiceEnabled = sharedPreferences.getBoolean("isVoiceEnabled", true);
        isVibrationEnabled = sharedPreferences.getBoolean("isVibrationEnabled", true);
    }

    private void saveSettingsToPreferences() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("sensitivityLevel", sensitivityLevel);
        editor.putBoolean("isVoiceEnabled", isVoiceEnabled);
        editor.putBoolean("isVibrationEnabled", isVibrationEnabled);
        editor.apply();
    }

    private void showSettingsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        RadioGroup radioGroup = view.findViewById(R.id.radioGroupSensitivity);
        SwitchCompat switchVoice = view.findViewById(R.id.switchVoice);
        SwitchCompat switchVibration = view.findViewById(R.id.switchVibration);
        Button btnSave = view.findViewById(R.id.btnSave);
        Button btnLogout = view.findViewById(R.id.btnLogout);
        ImageButton btnClose = view.findViewById(R.id.btnCloseDialog);

        AtomicInteger tempSensitivity = new AtomicInteger(sensitivityLevel);
        AtomicBoolean tempVoiceEnabled = new AtomicBoolean(isVoiceEnabled);
        AtomicBoolean tempVibrationEnabled = new AtomicBoolean(isVibrationEnabled);

        if (tempSensitivity.get() == 1) radioGroup.check(R.id.radioLow);
        else if (tempSensitivity.get() == 2) radioGroup.check(R.id.radioMedium);
        else radioGroup.check(R.id.radioHigh);

        switchVoice.setChecked(tempVoiceEnabled.get());
        switchVibration.setChecked(tempVibrationEnabled.get());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioLow) tempSensitivity.set(1);
            else if (checkedId == R.id.radioMedium) tempSensitivity.set(2);
            else tempSensitivity.set(3);
        });

        switchVoice.setOnCheckedChangeListener((buttonView, isChecked) -> tempVoiceEnabled.set(isChecked));
        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> tempVibrationEnabled.set(isChecked));

        btnSave.setOnClickListener(v -> {
            sensitivityLevel = tempSensitivity.get();
            isVoiceEnabled = tempVoiceEnabled.get();
            isVibrationEnabled = tempVibrationEnabled.get();
            saveSettingsToPreferences();

            ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);
            SensitivityRequest sensitivityRequest = new SensitivityRequest(userId, sensitivityLevel);
            ReminderRequest reminderRequest = new ReminderRequest(userId,
                    isVoiceEnabled ? 1 : 0,
                    isVibrationEnabled ? 1 : 0);

            apiService.updateSensitivity(sensitivityRequest).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        apiService.updateReminder(reminderRequest).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> response) {
                                if (response.isSuccessful()) {
                                    Toast.makeText(MainActivity.this, "設定已同步到後端", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                } else {
                                    Toast.makeText(MainActivity.this, "提醒設定同步失敗", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                Toast.makeText(MainActivity.this, "連線失敗：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        Toast.makeText(MainActivity.this, "靈敏度同步失敗", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "連線失敗：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnLogout.setOnClickListener(v -> {
            SharedPreferences loginPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            loginPrefs.edit().remove("user_id").apply();

            Toast.makeText(MainActivity.this, "已登出", Toast.LENGTH_SHORT).show();

            dialog.dismiss();
            Intent intent = new Intent(MainActivity.this, SignIn.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
            startLocationUpdates();
        }
    }

    private void processPedestrianLogic(List<DetectorMain.Recognition> recognitions) {
        boolean hasPerson = false;
        boolean hasCrosswalk = false;
        float personDistance = -1f;
        String trafficLightColor = "unknown";


        for (DetectorMain.Recognition r : recognitions) {
            if ("person".equals(r.getTitle())) {
                hasPerson = true;
                float h = r.getLocation().height();
                if (h > 0) {
                    personDistance = DISTANCE_SCALING_FACTOR / h;
                }
            }
            if ("crosswalk".equals(r.getTitle())) {
                hasCrosswalk = true;
            }
            if ("traffic_light".equals(r.getTitle())) {
                trafficLightColor = detectTrafficLightColor(currentBitmap, r);
                r.setColor(trafficLightColor);
            }
        }

        if (!hasPerson || personDistance < 0) return;

        switch (sensitivityLevel) {
            case 3: // 高靈敏度：只要行人接近即可提醒
                if (personDistance <= 20f) {
                    triggerVibrationOnce("高靈敏度：行人接近");
                    speakOnce("前方有行人，請注意");
                    sendAlertNotification("行人靠近", "前方有行人，請小心慢行");
                }
                break;
            case 2: // 中靈敏度：行人 + 斑馬線
                if (hasCrosswalk && personDistance <= 15f) {
                    triggerVibrationOnce("中靈敏度：行人+斑馬線");
                    speakOnce("行人準備過馬路，請減速");
                    sendAlertNotification("行人靠近", "前方有行人，請小心慢行");
                }
                break;
            case 1: // 低靈敏度：行人 + 斑馬線 + 綠燈
                if (hasCrosswalk && personDistance <= 10f && "green".equals(trafficLightColor)) {
                    triggerVibrationOnce("低靈敏度：綠燈+行人+斑馬線");
                    speakOnce("綠燈期間有行人過馬路，請讓行");
                    sendAlertNotification("行人靠近", "前方有行人，請小心慢行");
                }
                break;
        }
    }

    private float estimateTrafficLightDistance(List<DetectorMain.Recognition> recognitions) {
        for (DetectorMain.Recognition r : recognitions) {
            if (!"traffic_light".equals(r.getTitle())) continue;
            float height = r.getLocation().height();
            if (height <= 0) continue;
            return DISTANCE_SCALING_FACTOR / height;
        }
        return -1f;
    }

    private void triggerVibrationOnce(String tag) {
        long now = System.currentTimeMillis();
        if (now - lastVibrationTime < REMINDER_COOLDOWN_MS) return;

        if (isVibrationEnabled) {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
            lastVibrationTime = now;
            Log.d("提醒", "觸發提醒: " + tag);
        }
    }

    private void speakOnce(String message) {
        if (!isVoiceEnabled) return;

        long now = System.currentTimeMillis();
        if (now - lastSpeechTime < REMINDER_COOLDOWN_MS) return;

        if (textToSpeech != null && !textToSpeech.isSpeaking()) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tts1");
            lastSpeechTime = now; // 更新語音冷卻時間
            Log.d("提醒", "播放語音：" + message);
        }
    }

    private String detectTrafficLightColor(Bitmap fullBmp, DetectorMain.Recognition rec) {
        // 1. 裁切 ROI
        RectF boxF = rec.getLocation();
        int x = Math.max(0, (int)boxF.left);
        int y = Math.max(0, (int)boxF.top);
        int w = Math.min(fullBmp.getWidth() - x, (int)(boxF.right - boxF.left));
        int h = Math.min(fullBmp.getHeight() - y, (int)(boxF.bottom - boxF.top));
        if (w < 20 || h < 20) return "unknown";
        Bitmap crop = Bitmap.createBitmap(fullBmp, x, y, w, h);

        // 2. 转成 OpenCV Mat 并到 HSV
        Mat hsv = new Mat();
        Utils.bitmapToMat(crop, hsv);
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV);

        // 3. 均衡 V 通道（提亮）
        List<Mat> chs = new ArrayList<>();
        Core.split(hsv, chs);
        Imgproc.equalizeHist(chs.get(2), chs.get(2));
        Core.merge(chs, hsv);

        // 4. 用 V 通道直接作为灰度图做 Otsu 分割
        Mat brightMask = chs.get(2).clone();  // V 通道
        Imgproc.threshold(brightMask, brightMask, 0, 255,
                Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);

        // 5. 找轮廓
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(brightMask, contours, new Mat(),
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // 6. 面积&位置过滤 & 合并到 finalMask
        Mat finalMask = Mat.zeros(brightMask.size(), CvType.CV_8UC1);
        for (MatOfPoint ctr : contours) {
            double area = Imgproc.contourArea(ctr);
            if (area < 0.001 * w * h) continue;        // 面积太小
            //Rect r = Imgproc.boundingRect(ctr);        // 这里用的是 org.opencv.core.Rect
            //if (r.y > h * 0.6) continue;               // 发光区域大多在上方 60%
            Imgproc.drawContours(finalMask, Arrays.asList(ctr), -1,
                    new Scalar(255), Core.FILLED);
        }

        // 7. 在 finalMask 区域里计算 HSV 平均色
        Scalar meanHSV = Core.mean(hsv, finalMask);
        double H = meanHSV.val[0], S = meanHSV.val[1], Vv = meanHSV.val[2];

        // 8. 简单阈值判定
        if ((H < 10 || H > 160) && S > 80 && Vv > 60)      return "red";
        if (H > 15  && H < 35  && S > 80 && Vv > 60)      return "yellow";
        if (H > 35  && H < 85  && S > 80 && Vv > 60)      return "green";
        return "unknown";
    }

    private int getMaxVerticalProjection(Mat binaryMask) {
        int rows = binaryMask.rows();
        int cols = binaryMask.cols();
        int[] projection = new int[rows];

        for (int y = 0; y < rows; y++) {
            int count = 0;
            for (int x = 0; x < cols; x++) {
                double[] pixel = binaryMask.get(y, x);
                if (pixel != null && pixel[0] > 0) count++;
            }
            projection[y] = count;
        }

        int max = 0;
        for (int v : projection) max = Math.max(max, v);
        return max;
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

}
