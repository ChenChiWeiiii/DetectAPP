package com.example.detect;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
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

import org.opencv.core.Size;
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
import org.opencv.android.OpenCVLoader;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import java.util.UUID;

import androidx.camera.camera2.interop.Camera2CameraInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.util.SizeF;

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

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private final String targetDeviceName = "Mi Smart Band"; // 可改成你實際的裝置名稱
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;

    private static final Scalar LOWER_RED1 = new Scalar(0, 70, 50);
    private static final Scalar UPPER_RED1 = new Scalar(10, 255, 255);
    private static final Scalar LOWER_RED2 = new Scalar(160, 70, 50);
    private static final Scalar UPPER_RED2 = new Scalar(180, 255, 255);
    private static final Scalar LOWER_YELLOW = new Scalar(15, 100, 100);
    private static final Scalar UPPER_YELLOW = new Scalar(35, 255, 255);
    private static final Scalar LOWER_GREEN = new Scalar(40, 50, 50);
    private static final Scalar UPPER_GREEN = new Scalar(90, 255, 255);

    private float fPxY = -1f;
    private int sensorArrayHeightPx = -1;
    private float sensorHeightMm = -1f;
    private float focalMm = -1f;
    public static final float H_PERSON = 1.65f;
    public static final float H_TL_LAMP = 0.30f;
    private float calibScale = 1.0f;
    private float lastTLHeightPx = -1f;

    private float currentScale = 1f;
    public float getCurrentScale() { return currentScale; }
    // 影像座標還原需要用到
    private float currentDx = 0f, currentDy = 0f;
    private int lastImageHeightPx = 0;

    // 手機鏡頭離地高度（公尺）— 可微調或做成設定
    public static final float H_CAMERA = 1.40f;

    public float getCurrentDx() { return currentDx; }
    public float getCurrentDy() { return currentDy; }
    public int getLastImageHeightPx() { return lastImageHeightPx; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV");
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully");
        }

        SharedPreferences loginPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = loginPrefs.getString("user_id", null);
        if (userId == null) {
            Intent intent = new Intent(MainActivity.this, SignIn.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        createNotificationChannel();
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

        if (detector == null ) {
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
        //初始化藍牙並請求權限
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        requestBluetoothPermissions(); // 呼叫藍牙權限請


        createNotificationChannel();
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettingsDialog());
    }

    //加入藍芽權限請求
    private void requestBluetoothPermissions() {
        Log.d("MiBand", "準備請求藍牙權限");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            Log.d("MiBand", "Android 版本低於 12，跳過藍牙權限請求");
            scanAndConnectMiBand();  // 如果低版本可以直接掃描
        }
    }

    //掃描並連線手環
    private void scanAndConnectMiBand() {
        Log.d("MiBand", "執行 scanAndConnectMiBand()");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.w("MiBand", "缺少 BLUETOOTH_SCAN 權限");
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        bluetoothLeScanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.d("MiBand", "掃描到裝置");
                BluetoothDevice device = result.getDevice();
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.w("MiBand", "缺少 BLUETOOTH_CONNECT 權限，略過裝置");
                    return;
                }

                if (device != null) {
                    Log.d("MiBand", "找到手環：" + device.getName());
                    bluetoothLeScanner.stopScan(this);
                    connectToMiBand(device);
                }
            }
        });
    }

    //連線與震動功能
    private void connectToMiBand(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w("MiBand", "缺少 BLUETOOTH_CONNECT 權限");
            return;
        }

        bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            private final Context context = MainActivity.this;
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MiBand", "成功連線到手環");
                    bluetoothGatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("MiBand", "手環已斷線");
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                Log.d("MiBand", "開始嘗試使用私有UUID震動");

                // 原本的 UUID 嘗試
                UUID vibrationServiceUUID = UUID.fromString("7365a0ae-e596-129d-d84a-88db1ffbcc04");
                UUID vibrationCharUUID = UUID.fromString("1c7cfacb-7818-c09c-9345-04602070e0cc");

                BluetoothGattService service = gatt.getService(vibrationServiceUUID);
                if (service != null) {
                    BluetoothGattCharacteristic vibrationChar = service.getCharacteristic(vibrationCharUUID);
                    if (vibrationChar != null &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        vibrationChar.setValue(new byte[]{0x01});
                        boolean success = gatt.writeCharacteristic(vibrationChar);
                        Log.d("MiBand", "試圖震動小米手環：" + success);
                    } else {
                        Log.d("MiBand", "找不到震動特徵值");
                    }
                } else {
                    Log.w("MiBand", "找不到震動服務");
                }

                // 🔽 額外呼叫你自訂的震動方法（可選）
                triggerMiBandVibration();
            }
        });


    }

    private void triggerMiBandVibration() {
        if (bluetoothGatt == null) {
            Log.w("MiBand", "bluetoothGatt 為 null，無法震動");
            return;
        }

        UUID vibrationServiceUUID = UUID.fromString("7365a0ae-e596-129d-d84a-88db1ffbcc04");
        UUID vibrationCharUUID = UUID.fromString("1c7cfacb-7818-c09c-9345-04602070e0cc");

        BluetoothGattService service = bluetoothGatt.getService(vibrationServiceUUID);
        if (service != null) {
            BluetoothGattCharacteristic vibrationChar = service.getCharacteristic(vibrationCharUUID);
            if (vibrationChar != null&&ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                vibrationChar.setValue(new byte[]{0x01});
                boolean success = bluetoothGatt.writeCharacteristic(vibrationChar);
                Log.d("MiBand", "試圖震動小米手環：" + success);
            } else {
                Log.w("MiBand", "找不到震動特徵值");
            }
        } else {
            Log.w("MiBand", "找不到震動服務");
        }
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
        previewView.post(() -> {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                    ProcessCameraProvider.getInstance(this);

            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                    // 1. Preview 設定
                    Preview preview = new Preview.Builder().build();
                    previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
                    previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);

                    // 安全取得旋轉
                    Display display = previewView.getDisplay();
                    int rotation = (display != null) ? display.getRotation() : Surface.ROTATION_0;
                    preview.setTargetRotation(rotation);

                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    // 2. 選擇後鏡頭
                    CameraSelector cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build();

                    // 3. ImageAnalysis 設定
                    ImageAnalysis analysis = new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();
                    analysis.setTargetRotation(rotation);

                    // 4. 設定 Analyzer
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
                        Bitmap bitmap = imageToBitmap(image);
                        currentBitmap = bitmap;
                        lastImageHeightPx = bitmap.getHeight();

                        // 用「實際使用的 bitmap 高度」算 fPxY（確保軸一致）
                        if (focalMm > 0 && sensorHeightMm > 0 && fPxY <= 0) {
                            fPxY = (focalMm / sensorHeightMm) * bitmap.getHeight();
                        }

                        List<DetectorMain.Recognition> rawResults =
                                detector.detect(bitmap, bitmap.getWidth(), bitmap.getHeight());

                        List<DetectorMain.Recognition> tlResults = new ArrayList<>();
                        for (DetectorMain.Recognition r : rawResults) {
                            if ("traffic_light".equals(r.getTitle())) {
                                tlResults.add(r);
                            }
                        }

                        List<DetectorMain.Recognition> tlFiltered = nonMaxSuppression(tlResults, 0.5f);
                        for (DetectorMain.Recognition tl : tlFiltered) {
                            String color = detectTrafficLightColor(bitmap, tl.getLocation());
                            tl.setColor(color);
                            lastTLHeightPx = tl.getLocation().height();
                        }

                        float viewW = previewView.getWidth();
                        float viewH = previewView.getHeight();
                        float imgW  = bitmap.getWidth();
                        float imgH  = bitmap.getHeight();
                        float scale = Math.min(viewW / imgW, viewH / imgH);
                        float dx = (viewW  - imgW * scale) / 2f;
                        float dy = (viewH  - imgH * scale) / 2f;

                        currentScale = scale;
                        currentDx = dx;
                        currentDy = dy;

                        List<DetectorMain.Recognition> viewResults = new ArrayList<>();
                        for (DetectorMain.Recognition r : rawResults) {
                            RectF rawBox = r.getLocation();
                            RectF viewBox = new RectF(
                                    rawBox.left   * scale + dx,
                                    rawBox.top    * scale + dy,
                                    rawBox.right  * scale + dx,
                                    rawBox.bottom * scale + dy
                            );
                            r.setLocation(viewBox);
                            viewResults.add(r);
                        }

                        overlayView.setResults(viewResults);
                        processPedestrianLogic(viewResults);
                        image.close();
                    });

                    cameraProvider.unbindAll();
                    androidx.camera.core.Camera camera =
                            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis);

                    Camera2CameraInfo cam2Info = Camera2CameraInfo.from(camera.getCameraInfo());

                    float[] focals = cam2Info.getCameraCharacteristic(
                            CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                    );
                    SizeF sensorSizeMm = cam2Info.getCameraCharacteristic(
                            CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
                    );

                    if (focals != null && focals.length > 0 && sensorSizeMm != null) {
                        focalMm = focals[0];
                        sensorHeightMm = sensorSizeMm.getHeight();

                        if (fPxY <= 0 && currentBitmap != null) {
                            fPxY = (focalMm / sensorHeightMm) * currentBitmap.getHeight();
                        }
                    }

                } catch (ExecutionException | InterruptedException e) {
                    Log.e("CameraX", "Camera initialization failed", e);
                }
            }, ContextCompat.getMainExecutor(this));
        });
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
        calibScale = sharedPreferences.getFloat("calibScale", 1.0f);
    }

    private void saveSettingsToPreferences() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("sensitivityLevel", sensitivityLevel);
        editor.putBoolean("isVoiceEnabled", isVoiceEnabled);
        editor.putBoolean("isVibrationEnabled", isVibrationEnabled);
        editor.putFloat("calibScale", calibScale);
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
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            Log.d("MiBand", "收到藍牙權限結果");
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                Log.d("MiBand", "藍牙權限已授權，開始掃描");
                scanAndConnectMiBand();
            } else {
                Toast.makeText(this, "未授權藍牙權限，無法連線手環", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void processPedestrianLogic(List<DetectorMain.Recognition> recognitions) {
        boolean hasPerson = false;
        boolean hasCrosswalk = false;
        float personDistance = -1f;
        String trafficLightColor = "unknown";

        // 1) 打印所有标签
        for (DetectorMain.Recognition r : recognitions) {
            Log.d("DEBUG_DET", "Detected title=" + r.getTitle()
                    + "  bbox=" + r.getLocation()
                    + "  conf=" + r.getConfidence());
        }

        // 2) 处理逻辑
        for (DetectorMain.Recognition r : recognitions) {
            String title = r.getTitle().toLowerCase();
            RectF loc = r.getLocation();

            if (title.contains("person")) {
                hasPerson = true;
                float hView = loc.height();
                float scale = Math.max(getCurrentScale(), 1e-6f);
                float hImg  = hView / scale;
                personDistance = finalizeDistance(estimateDistanceByHeightPx(hImg, H_PERSON));
            }

            if (title.contains("crosswalk")) {
                hasCrosswalk = true;
            }

            if (title.contains("traffic")) {
                // 直接讀取剛剛在 analyzer 存好的顏色
                String color = r.getColor();
                Log.d("DEBUG_TL", "TrafficLight color (預先偵測) = " + color);
                trafficLightColor = color;
            }

        }

        if (!hasPerson || personDistance < 0) return;

        switch (sensitivityLevel) {
            case 3:
                if (personDistance <= 20f) {
                    triggerVibrationOnce("高靈敏度：行人接近");
                    speakOnce("前方有行人，請注意");
                    sendAlertNotification("行人靠近", "前方有行人，請小心慢行");
                }
                break;
            case 2:
                if (hasCrosswalk && personDistance <= 15f) {
                    triggerVibrationOnce("中靈敏度：行人+斑馬線");
                    speakOnce("行人準備過馬路，請減速");
                    sendAlertNotification("行人靠近", "前方有行人，請小心慢行");
                }
                break;
            case 1:
                // 这里 trafficLightColor 已经被正确赋值了
                if (hasCrosswalk && personDistance <= 10f && "green".equals(trafficLightColor)) {
                    triggerVibrationOnce("低靈敏度：綠燈+行人+斑馬線");
                    speakOnce("綠燈期間有行人過馬路，請讓行");
                    sendAlertNotification("行人靠近", "前方有行人，請小心慢行");
                }
                break;
        }
    }

    private float estimateTrafficLightDistance(List<DetectorMain.Recognition> recognitions) {
        float scale = Math.max(getCurrentScale(), 1e-6f);
        for (DetectorMain.Recognition r : recognitions) {
            if (!"traffic_light".equals(r.getTitle())) continue;
            float hView = r.getLocation().height();
            float hImg  = hView / scale;
            if (hImg <= 0) continue;
            float d = estimateDistanceByHeightPx(hImg, H_TL_LAMP);
            return finalizeDistance(d);
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
            triggerMiBandVibration();
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

    private String detectTrafficLightColor(Bitmap fullBmp, RectF rawBox) {
        Mat mat       = new Mat();
        Mat mask      = new Mat();
        Mat maskLight = new Mat();
        Mat kernel    = null;
        List<Mat> hsv = new ArrayList<>();

        // 三色掩膜也要释放
        Mat redMask    = new Mat(), tmpRed = new Mat();
        Mat yellowMask = new Mat(), greenMask = new Mat();

        try {
            // 1. 裁剪 ROI
            int x    = Math.max(0, (int) rawBox.left);
            int y    = Math.max(0, (int) rawBox.top);
            int w    = Math.min(fullBmp.getWidth() - x, (int) rawBox.width());
            int hROI = Math.min(fullBmp.getHeight() - y, (int) rawBox.height());
            if (w < 15 || hROI < 15) return "unknown";
            Bitmap crop = Bitmap.createBitmap(fullBmp, x, y, w, hROI);

            // 2. RGBA -> BGR -> HSV
            Utils.bitmapToMat(crop, mat);
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR);
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2HSV);

            // 3. 拆出 H/S/V
            Core.split(mat, hsv);
            Mat hueChan   = hsv.get(0);
            Mat satChan   = hsv.get(1);
            Mat valueChan = hsv.get(2);

            // 4. Otsu on V 得最亮区
            Imgproc.threshold(valueChan, mask,
                    0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
            // 可加饱和度过滤去掉壳反光
            Mat satMask = new Mat();
            Imgproc.threshold(satChan, satMask, 100, 255, Imgproc.THRESH_BINARY);
            Core.bitwise_and(mask, satMask, mask);
            satMask.release();

            // 5. 开闭去噪
            kernel = Imgproc.getStructuringElement(
                    Imgproc.MORPH_ELLIPSE, new Size(3, 3));
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN,  kernel);
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);

            // 6. 找最大轮廓当灯泡
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(mask.clone(), contours,
                    new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            maskLight = Mat.zeros(mask.size(), mask.type());
            if (!contours.isEmpty()) {
                double maxA = 0; int idx = 0;
                for (int i = 0; i < contours.size(); i++) {
                    double a = Imgproc.contourArea(contours.get(i));
                    if (a > maxA) { maxA = a; idx = i; }
                }
                Imgproc.drawContours(maskLight, contours, idx, new Scalar(255), -1);
            } else {
                maskLight = mask.clone();
            }

            // ===== STEP 8：分别生成三色掩膜并统计 =====
            // 红：低 H 和 高 H 两段
            Core.inRange(mat, LOWER_RED1, UPPER_RED1, redMask);
            Core.inRange(mat, LOWER_RED2, UPPER_RED2, tmpRed);
            Core.add(redMask, tmpRed, redMask);

            // 黄
            Core.inRange(mat, LOWER_YELLOW, UPPER_YELLOW, yellowMask);

            // 绿
            Core.inRange(mat, LOWER_GREEN, UPPER_GREEN, greenMask);

            // 与最亮区相交，去掉灯壳与背景
            Core.bitwise_and(redMask,   maskLight, redMask);
            Core.bitwise_and(yellowMask,maskLight, yellowMask);
            Core.bitwise_and(greenMask, maskLight, greenMask);

            // 统计
            int cntR  = Core.countNonZero(redMask);
            int cntY  = Core.countNonZero(yellowMask);
            int cntG  = Core.countNonZero(greenMask);
            int total = Core.countNonZero(maskLight);

            double ratioR = cntR / (double) total;
            double ratioY = cntY / (double) total;
            double ratioG = cntG / (double) total;

            Log.d("DEBUG_TL", String.format(
                    "ColorRatios R=%.3f Y=%.3f G=%.3f", ratioR, ratioY, ratioG));

            if (total < 5) return "unknown";

            // 最终判色（阈值可调）
            if (ratioR > ratioY && ratioR > ratioG && ratioR > 0.05) {
                return "red";
            } else if (ratioY > ratioR && ratioY > ratioG && ratioY > 0.05) {
                return "yellow";
            } else if (ratioG > ratioR && ratioG > ratioY && ratioG > 0.05) {
                return "green";
            } else {
                return "unknown";
            }

        } finally {
            // 释放所有 Mat
            mat.release();
            mask.release();
            maskLight.release();
            if (kernel   != null) kernel.release();
            for (Mat m : hsv)        m.release();
            redMask.release();
            tmpRed.release();
            yellowMask.release();
            greenMask.release();
        }
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

    private List<DetectorMain.Recognition> nonMaxSuppression(
            List<DetectorMain.Recognition> tlList, float iouThreshold) {
        // 按 confidence 由大到小排序
        tlList.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        List<DetectorMain.Recognition> kept = new ArrayList<>();
        boolean[] removed = new boolean[tlList.size()];

        for (int i = 0; i < tlList.size(); i++) {
            if (removed[i]) continue;
            DetectorMain.Recognition a = tlList.get(i);
            kept.add(a);
            RectF boxA = a.getLocation();

            for (int j = i + 1; j < tlList.size(); j++) {
                if (removed[j]) continue;
                DetectorMain.Recognition b = tlList.get(j);
                if (boxIoU(boxA, b.getLocation()) > iouThreshold) {
                    removed[j] = true;
                }
            }
        }
        return kept;
    }
    private float boxIoU(RectF a, RectF b) {
        float left   = Math.max(a.left,   b.left);
        float right  = Math.min(a.right,  b.right);
        float top    = Math.max(a.top,    b.top);
        float bottom = Math.min(a.bottom, b.bottom);
        float interW = Math.max(0f, right - left);
        float interH = Math.max(0f, bottom - top);
        float inter  = interW * interH;
        float areaA  = (a.right - a.left) * (a.bottom - a.top);
        float areaB  = (b.right - b.left) * (b.bottom - b.top);
        return inter / (areaA + areaB - inter);
    }

    private float estimateDistanceByHeightPx(float boxHeightPx, float realHeightM) {
        if (fPxY <= 0 || boxHeightPx <= 0) return -1f;
        return (realHeightM * fPxY) / boxHeightPx;
    }

    private float finalizeDistance(float dEst) {
        if (dEst <= 0) return dEst;
        return dEst * calibScale;
    }

    public static float estimateDistanceForHeightPx(float fPxYStatic, float calibScaleStatic, float boxHeightPx, float realHeightM) {
        if (fPxYStatic <= 0 || boxHeightPx <= 0) return -1f;
        float d = (realHeightM * fPxYStatic) / boxHeightPx;
        return d * (calibScaleStatic > 0 ? calibScaleStatic : 1.0f);
    }

    public float getFPxY() { return fPxY; }

    public float getCalibScale() { return calibScale; }

}

