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
import android.util.Rational;
import androidx.camera.core.ViewPort;
import androidx.camera.core.UseCaseGroup;
import com.example.detect.model.ReminderRequest;
import com.example.detect.model.SensitivityRequest;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Map;
import java.util.HashMap;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.util.SizeF;
import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import android.media.AudioAttributes;
import android.net.Uri;
import android.provider.Settings;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.FocusMeteringAction;

@OptIn(markerClass = ExperimentalCamera2Interop.class)
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
    private androidx.camera.core.Camera camera;

    // ==== 超速提醒 ====
    private SpeedAlertManager speedMgr;
    // ---- Long-distance TL tuning ----
    private static final float INITIAL_ZOOM = 1.8f;   // 綁定相機後套用的預設變焦
    private static final int   TARGET_W     = 1920;   // 影像分析輸入寬
    private static final int   TARGET_H     = 1080;   // 影像分析輸入高

    private static final float TL_CONF   = 0.28f;     // 交通號誌專屬信心門檻（遠距離小物件通常較低）
    private static final float IOU_NMS   = 0.80f;     // NMS IoU
    private static final int   MIN_BOX_PX = 8;        // 最小框像素，避免噪聲
    // 顏色判斷穩定化
    private static final float  TL_ROI_INSET    = 0.08f;   // 裁一圈，避開邊框/反光（8%）
    private static final double TL_MIN_RATIO    = 0.03;    // 原本 0.06 → 0.03
    private static final double TL_MIN_GAP      = 0.015;   // 原本 0.025 → 0.015
    private static final int    TL_MIN_TOTALPX  = 6;       // 原本 12 → 6

    // 追蹤/投票去抖
    private static final float  TRACK_IOU_MATCH = 0.30f;   // 降低到 0.3，比較容易續上同一盞燈
    private static final int    COLOR_CONFIRM_FRAMES = 3;  // 變色需連續 N 幀
    private static final int    COLOR_HOLD_FRAMES    = 8;  // 確認後至少維持 M 幀

    // 平鋪推論（不換模型也能增距）
    private static final int TILE_COLS = 2;           // 先用 2×2；效能足夠再升 3×3
    private static final int TILE_ROWS = 2;
    private static final int TILE_OVERLAP = 40;       // 邊緣重疊，避免切到一半

    // 多幀投票（讓顏色更穩）
    private int frameIndex = 0;                       // 逐幀累加
    private static final int TRACK_TTL = 10;          // 追蹤幀數存活

    private static class TLTrack {
        RectF box;
        int seenFrame;
        int[] votes = new int[4]; // 保留
        int stable = 0;           // 目前穩定色 (0/1/2/3)
        int cand   = 0;           // 當前候選色
        int streak = 0;           // 候選色連續幀數
        int hold   = 0;           // 已確認後的保留幀數
    }
    private final List<TLTrack> tlTracks = new ArrayList<>();
    private ExecutorService cameraExecutor;
    private Handler ui;
    private static final boolean TL_DEBUG_LOG   = true;   // 想看細節就 true
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
    private static final String ALERT_CHANNEL_ID = "alert_channel_sound_novib";
    private static final String ALERT_CHANNEL_NAME = "行人/紅綠燈提醒";
    private BluetoothGattCharacteristic vibChar = null;         // 震動用特徵值快取
    private final java.util.concurrent.atomic.AtomicBoolean analyzing = new java.util.concurrent.atomic.AtomicBoolean(false);
    private long lastAnalyzeMs = 0;
    private static final long MIN_INTERVAL_MS = 66; // ~15 FPS，可依機型調整
    // ===== OSM Overpass (速限查詢 API) =====
    private static final String OVERPASS_BASE_URL = "https://overpass-api.de/";
    private OverpassApi overpassApi;

    // 節流（避免 Overpass 被你打爆）
    private static final float OSM_QUERY_MIN_MOVE_M = 120f;        // 位移 > 120m 才查
    private static final long  OSM_QUERY_MIN_INTERVAL_MS = 20_000; // 或每 20 秒一次
    private static final int DEFAULT_OSM_SPEED_KMH = 50;

    private float lastQueryLat = Float.NaN, lastQueryLng = Float.NaN;
    private long lastQueryMs = 0L;

    // 目前取得到的速限（km/h），null = 未知
    private Integer currentSpeedLimitKmh = DEFAULT_OSM_SPEED_KMH;

    // === 超速提醒控制參數 ===
    private static final float OVERSPEED_TOLERANCE = 1.01f; // 容忍比例，避免 GPS 抖動
    private static final long NOTIF_COOLDOWN_MS = 10_000;   // 通知冷卻 10 秒
    private static final long OVERSPEED_HOLD_MS = 0_000;    // 持續時間判定 (0 表示立即)
    private long lastNotifMs = 0L;
    private long overspeedSinceMs = 0L;

    // 最近一次定位
    private Location lastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();
        ui = new Handler(Looper.getMainLooper());

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
        Log.e("SpeedLimit", "BOOT onCreate()");
        initOverpassApi();
        createNotificationChannel();
        requestBtPermsIfNeeded();
        ensureMiBandConnected();
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
//            ActivityCompat.requestPermissions(this,
//                    new String[]{
//                            Manifest.permission.CAMERA,
//                            Manifest.permission.ACCESS_FINE_LOCATION,
//                            Manifest.permission.POST_NOTIFICATIONS
//                    }, PERMISSION_CODE);}
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION },
                    PERMISSION_CODE);}

        try {
            detector = new DetectorMain(getAssets(), "best_float16.tflite", "All");

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (detector == null ) {
            finish();
            return;
        }

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(java.util.Locale.TAIWAN); // 使用中文語音
            }
        });
        //初始化藍牙並請求權限
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        //requestBluetoothPermissions(); // 呼叫藍牙權限請
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettingsDialog());

    }

    private void initOverpassApi() {
        Retrofit rt = new Retrofit.Builder()
                .baseUrl(OVERPASS_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        overpassApi = rt.create(OverpassApi.class);
    }

    private boolean hasBtConnect() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBtScan() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBtPermsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ArrayList<String> req = new ArrayList<>();
            if (!hasBtConnect()) req.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (!hasBtScan())    req.add(Manifest.permission.BLUETOOTH_SCAN);
            if (!req.isEmpty()) {
                ActivityCompat.requestPermissions(this, req.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
            }
        }
    }

    //從已配對裝置連線
    @SuppressLint("MissingPermission")
    private void tryConnectFromBonded() {
        if (!hasBtConnect()) { requestBtPermsIfNeeded(); return; }
        if (bluetoothAdapter == null) return;

        for (BluetoothDevice d : bluetoothAdapter.getBondedDevices()) { // 需要 CONNECT
            String name = d.getName();                                  // 需要 CONNECT
            if (name != null && (name.contains("Mi") || name.contains("Band") || name.contains("Xiaomi")
                    || (targetDeviceName != null && name.contains(targetDeviceName)))) {
                Log.d("MiBand", "從已配對裝置直接連: " + name + " / " + d.getAddress());

                return;
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void triggerMiBandVibration() {
        if (!hasBtConnect()) { requestBtPermsIfNeeded(); return; }
        if (bluetoothGatt == null || vibChar == null) { Log.w("MiBand","gatt/char 為 null"); return; }
        vibChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        vibChar.setValue(new byte[]{0x02});
        try {
            boolean ok = bluetoothGatt.writeCharacteristic(vibChar);
            Log.d("MiBand","write vibrate -> " + ok);
        } catch (SecurityException se) {
            Log.e("MiBand","writeCharacteristic 被拒", se);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            // 先刪同名（理論上新 ID 不會撞，但保險）
            NotificationChannel existing = nm.getNotificationChannel(ALERT_CHANNEL_ID);
            if (existing != null) nm.deleteNotificationChannel(ALERT_CHANNEL_ID);

            NotificationChannel ch = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    ALERT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT   // 有聲、不彈頭貼
            );
            ch.setDescription("有聲、不震（手機），手環由 Mi Fitness 鏡像震動");
            ch.enableVibration(false);                 // 關震動
            ch.setVibrationPattern(new long[]{0});     // 明確不震
            Uri sound = Settings.System.DEFAULT_NOTIFICATION_URI;
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ch.setSound(sound, attrs);                 // 開聲音
            nm.createNotificationChannel(ch);

            NotificationChannel v = nm.getNotificationChannel(ALERT_CHANNEL_ID);
            Log.d("CHAN", "created: importance=" + (v!=null?v.getImportance():-1)
                    + " vib=" + (v!=null && v.shouldVibrate())
                    + " sound=" + (v!=null && v.getSound()!=null));
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
                    previewView.setImplementationMode(PreviewView.ImplementationMode.PERFORMANCE);
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
                            .setTargetResolution(new android.util.Size(1920, 1080)) // 需要再快可改 1280x720
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) // ✅ 改 RGBA
                            .setImageQueueDepth(1)
                            .build();
                    analysis.setTargetRotation(rotation);

                    // ★ 讓 Preview & Analysis 共用同一個 ViewPort（裁切/比例一致）
                    androidx.camera.core.ViewPort vp =
                            new androidx.camera.core.ViewPort.Builder(
                                    new android.util.Rational(previewView.getWidth(), previewView.getHeight()),
                                    rotation // 用你前面已算好的 rotation
                            )
                                    .setScaleType(androidx.camera.core.ViewPort.FIT) // 對齊 PreviewView 的 FIT_CENTER 顯示
                                    .build();

                    androidx.camera.core.UseCaseGroup ucg =
                            new androidx.camera.core.UseCaseGroup.Builder()
                                    .addUseCase(preview)   // 用你前面已建立的 preview
                                    .addUseCase(analysis)  // 用你前面已建立的 analysis
                                    .setViewPort(vp)
                                    .build();

                    cameraProvider.unbindAll();
                    camera = cameraProvider.bindToLifecycle(this, cameraSelector, ucg);

                    // 4. 設定 Analyzer（建議用主執行緒，如要用 cameraExecutor 也可）
                    analysis.setAnalyzer(cameraExecutor, image -> {
                        if (!analyzing.compareAndSet(false, true)) { image.close(); return; }

                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - lastAnalyzeMs < MIN_INTERVAL_MS) { // ~15fps，可自行調整
                            image.close();
                            analyzing.set(false);
                            return;
                        }
                        lastAnalyzeMs = now;

                        try {
                            frameIndex++;

                            // ✅ 走快速路徑（因為上面把輸出改成 RGBA）
                            Bitmap bitmap = imageToBitmapFast(image);
                            currentBitmap = bitmap;
                            lastImageHeightPx = bitmap.getHeight();

                            if (focalMm > 0 && sensorHeightMm > 0 &&
                                    (fPxY <= 0f || lastImageHeightPx != bitmap.getHeight())) {
                                fPxY = (focalMm / sensorHeightMm) * bitmap.getHeight();
                            }

                            // ✓ 自適應平鋪：每隔一幀才做 2×2，另一幀跑全圖，降低 GC
                            boolean useTiles = false; // 偶數幀做平鋪
                            if ((frameIndex & 1) == 0 && lastTLHeightPx > 0 && lastTLHeightPx < 20) { // 門檻可調
                                useTiles = true;
                            }
                            List<DetectorMain.Recognition> detAll = useTiles
                                    ? detectTiled(bitmap, TILE_COLS, TILE_ROWS, TILE_OVERLAP)
                                    : detector.detect(bitmap, bitmap.getWidth(), bitmap.getHeight());

                            // 交通號誌的門檻先過濾，降低後面 OpenCV 的負擔
                            List<DetectorMain.Recognition> filtered = new ArrayList<>();
                            for (DetectorMain.Recognition r : detAll) {
                                if ("traffic_light".equals(r.getTitle())) {
                                    if (r.getConfidence() >= TL_CONF &&
                                            Math.min(r.getLocation().width(), r.getLocation().height()) >= MIN_BOX_PX) { // 稍微放大最小框
                                        filtered.add(r);
                                    }
                                } else {
                                    filtered.add(r);
                                }
                            }

                            List<DetectorMain.Recognition> kept = nmsByClass(filtered, IOU_NMS);

                            // ===== 判斷燈號顏色（降頻 + 只對最大幾個做） =====
                            float maxTlH = -1f;

                            // 先收集所有紅綠燈
                            List<DetectorMain.Recognition> tls = new ArrayList<>();
                            for (DetectorMain.Recognition r : kept) {
                                if ("traffic_light".equals(r.getTitle())) {
                                    tls.add(r);
                                    // 順便記錄最大高度，供後面自適應用
                                    if (r.getLocation().height() > maxTlH) maxTlH = r.getLocation().height();
                                }
                            }
                            if (maxTlH > 0) lastTLHeightPx = maxTlH;

                            // 依 bbox 高度由大到小排序（大的通常比較近、比較清楚）
                            tls.sort((a, b) -> Float.compare(b.getLocation().height(), a.getLocation().height()));

                            // 只挑最大的 1～2 個做判色（避免每幀大量 OpenCV 計算）
                            int maxColorCheck = Math.min(2, tls.size());

                            // 每 3 幀才做一次判色（降頻，降低延遲）
                            boolean doColorThisFrame = (frameIndex % 3 == 0);

                            if (doColorThisFrame) {
                                for (int i = 0; i < maxColorCheck; i++) {
                                    DetectorMain.Recognition r = tls.get(i);
                                    String c = detectTrafficLightColor(bitmap, r.getLocation());
                                    r.setColor(c);
                                    // 可選：Log 診斷
                                    // Log.d("DEBUG_TL", "TrafficLight color = " + c + " bbox=" + r.getLocation());
                                }
                            }
                            // 其餘較小的燈，本幀先不判；顏色會在之後幀慢慢補上

                            // 映射到 Overlay 座標（保留你原本距離/顯示邏輯）
                            int imgW = bitmap.getWidth(), imgH = bitmap.getHeight();
                            List<DetectorMain.Recognition> viewResults = toOverlayResults(kept, imgW, imgH);

                            // UI 更新丟回主執行緒
                            runOnUiThread(() -> {
                                overlayView.setResults(viewResults);
                                processPedestrianLogic(viewResults);
                            });
                        } catch (Throwable t) {
                            Log.e("Analyzer", "analyze error", t);
                        } finally {
                            image.close();
                            analyzing.set(false);
                        }
                    });

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
    // 若你沒有這個欄位，順便加上
    private final java.util.concurrent.atomic.AtomicReference<Bitmap> reusableBmp =
            new java.util.concurrent.atomic.AtomicReference<>();

    private Bitmap imageToBitmapFast(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        int w = image.getWidth(), h = image.getHeight();

        Bitmap bmp = reusableBmp.get();
        if (bmp == null || bmp.getWidth() != w || bmp.getHeight() != h) {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            reusableBmp.set(bmp);
        }
        ByteBuffer buf = plane.getBuffer();
        buf.rewind();
        bmp.copyPixelsFromBuffer(buf);

        // ✅ 要把像素旋轉到正向（對齊 PreviewView）
        int rotation = image.getImageInfo().getRotationDegrees();
        if (rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            bmp = Bitmap.createBitmap(bmp, 0, 0, w, h, m, true);
        }
        return bmp;
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

//        android.graphics.Rect crop = image.getCropRect();
//
//        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21,
//                image.getWidth(), image.getHeight(), null);
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        yuv.compressToJpeg(
//                new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()),
//                100,
//                out
//        );

        android.graphics.Rect crop = image.getCropRect();

        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        yuv.compressToJpeg(crop, 100, out);

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

    private final int[] pvLoc = new int[2];
    private final int[] ovLoc = new int[2];

    private RectF mapRectToOverlay(RectF imgRect, int imgW, int imgH) {
        float viewW = overlayView.getWidth();
        float viewH = overlayView.getHeight();

        // 與 PreviewView.FIT_CENTER 對應的 letterbox 縮放
        float scale = Math.min(viewW / imgW, viewH / imgH);
        float dx = (viewW - imgW * scale) / 2f;
        float dy = (viewH - imgH * scale) / 2f;

        // 若 previewView 與 overlayView 在父容器位置不同，補上相對位移
        previewView.getLocationInWindow(pvLoc);
        overlayView.getLocationInWindow(ovLoc);
        dx += (pvLoc[0] - ovLoc[0]);
        dy += (pvLoc[1] - ovLoc[1]);

        // 保留你既有的距離/顯示邏輯會用到的比例與偏移
        currentScale = scale;
        currentDx = dx;
        currentDy = dy;

        return new RectF(
                imgRect.left   * scale + dx,
                imgRect.top    * scale + dy,
                imgRect.right  * scale + dx,
                imgRect.bottom * scale + dy
        );
    }
    private List<DetectorMain.Recognition> toOverlayResults(
            List<DetectorMain.Recognition> src, int imgW, int imgH) {
        List<DetectorMain.Recognition> out = new ArrayList<>(src.size());
        for (DetectorMain.Recognition r : src) {
            RectF v = mapRectToOverlay(r.getLocation(), imgW, imgH);
            r.setLocation(v);
            out.add(r);
        }
        return out;
    }

    private void startLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            // 用主執行緒 Looper
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000, 0, locationListener, Looper.getMainLooper());
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 2000, 0, locationListener, Looper.getMainLooper());

            // 立刻用上一次位置觸發一次（就算沒移動也能打 API）
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) {
                Log.e("SpeedLimit", "KICK with lastKnownLocation");
                locationListener.onLocationChanged(last);
            }
        } catch (SecurityException e) {
            Log.e("SpeedLimit", "requestLocationUpdates SecurityException", e);
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            Log.e("SpeedLimit", "LOC tick");
            lastLocation = location;
            float speedKmh = location.getSpeed() * 3.6f;

            updateSpeedUi(speedKmh);

            maybeAlertOverspeed(speedKmh, currentSpeedLimitKmh);
            Log.e("OSMSpeed", "call maybeFetch from onLocationChanged");
            maybeFetchAndApplySpeedLimit(location);
            Log.d("SpeedLimit", String.format(java.util.Locale.US,
                    "onLoc: v=%.1f km/h lat=%.6f lon=%.6f",
                    speedKmh, location.getLatitude(), location.getLongitude()));
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
        calibScale = sharedPreferences.getFloat("calibScale", 1.0f);
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
            ReminderRequest reminderRequest = new ReminderRequest(
                    userId,
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
                                    dialog.dismiss();
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                Toast.makeText(MainActivity.this, "連線失敗!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "連線失敗!", Toast.LENGTH_SHORT).show();
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
            boolean locOk = false;
            startCamera();
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])) {
                    locOk = (grantResults[i] == PackageManager.PERMISSION_GRANTED);
                }
            }
            if (locOk) {
                startLocationUpdates();
            }
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
                ensureMiBandConnected();
                //scanAndConnectMiBand();
            } else {
                Toast.makeText(this, "未授權藍牙權限，無法連線手環", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try { return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); }
        catch (Exception e) { return false; }
    }

    private void ensureMiBandConnected() {
        if (bluetoothGatt != null) return; // 已有連線物件就不重來

        // 藍牙要開
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w("MiBand", "藍牙未開啟，請先開啟藍牙");
            try { startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); } catch (Exception ignore) {}
            return;
        }
        // 某些機型掃描需要開定位
        if (!isLocationEnabled()) {
            Log.w("MiBand", "系統定位未開啟，可能導致掃描為空");
            try { startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } catch (Exception ignore) {}
        }
        // 權限（Android 12+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }
        tryConnectFromBonded(); // 先從已配對直連，沒有再掃描
    }

    private void sendAlertNotification(String title, String content) {
        //if (!isVibrationEnabled) return;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
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

    private void processPedestrianLogic(List<DetectorMain.Recognition> recognitions) {
        boolean hasPerson = false;
        boolean hasCrosswalk = false;
        float personDistance = -1f;
        String trafficLightColor = "unknown";

        for (DetectorMain.Recognition r : recognitions) {
            Log.d("DEBUG_DET", "Detected title=" + r.getTitle()
                    + "  bbox=" + r.getLocation()
                    + "  conf=" + r.getConfidence());
        }

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
                    speakOnce("前方有行人，請注意");
                    sendAlertNotification("行人靠近", "前方有行人，請小心慢行");
                }
                break;
            case 2:
                if (hasCrosswalk && personDistance <= 15f) {
                    speakOnce("行人準備過馬路，請減速");
                    sendAlertNotification("行人靠近", "前方有行人，請小心慢行");
                }
                break;
            case 1:
                if (hasCrosswalk && personDistance <= 10f && "green".equals(trafficLightColor)) {
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
        Mat mat = new Mat(), mask = new Mat(), maskLight = new Mat(), kernel = null;
        List<Mat> hsv = new ArrayList<>();
        Mat redMask = new Mat(), tmpRed = new Mat(), yellowMask = new Mat(), greenMask = new Mat();

        try {
            // 1) ROI 稍微內縮，避免邊緣
            RectF box = new RectF(rawBox);
            float insetX = box.width()  * TL_ROI_INSET;
            float insetY = box.height() * TL_ROI_INSET;
            box.inset(insetX, insetY);

            int x = Math.max(0, (int) box.left);
            int y = Math.max(0, (int) box.top);
            int w = Math.min(fullBmp.getWidth()  - x, (int) box.width());
            int h = Math.min(fullBmp.getHeight() - y, (int) box.height());
            if (w < 15 || h < 15) return "unknown";

            Bitmap crop = Bitmap.createBitmap(fullBmp, x, y, w, h);
            Bitmap cropSmall = Bitmap.createScaledBitmap(crop, 96, 96, true);
            crop.recycle();

            // 2) 轉 HSV
            Utils.bitmapToMat(cropSmall, mat);
            cropSmall.recycle();
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR);
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2HSV);

            // 3) 拆 H/S/V
            Core.split(mat, hsv);
            Mat hueChan   = hsv.get(0);
            Mat satChan   = hsv.get(1);
            Mat valueChan = hsv.get(2);

            // 4) 用 Otsu 找亮區 + 降低飽和度門檻
            Imgproc.threshold(valueChan, mask, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
            Mat satMask = new Mat();
            Imgproc.threshold(satChan, satMask, 60, 255, Imgproc.THRESH_BINARY); // 60 比 100 寬鬆
            Core.bitwise_and(mask, satMask, mask);
            satMask.release();

            // 5) 輕度開閉去噪
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN,  kernel);
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);

            // 6) 取最大亮區
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(mask.clone(), contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
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

            // 7) 三色掩膜（在 HSV 空間）
            Core.inRange(mat, LOWER_RED1, UPPER_RED1, redMask);
            Core.inRange(mat, LOWER_RED2, UPPER_RED2, tmpRed);
            Core.add(redMask, tmpRed, redMask);
            Core.inRange(mat, LOWER_YELLOW, UPPER_YELLOW, yellowMask);
            Core.inRange(mat, LOWER_GREEN, UPPER_GREEN,   greenMask);

            // 與最亮區相交
            Core.bitwise_and(redMask,    maskLight, redMask);
            Core.bitwise_and(yellowMask, maskLight, yellowMask);
            Core.bitwise_and(greenMask,  maskLight, greenMask);

            // 8) 嚴格判色（主要路徑）
            int cntR  = Core.countNonZero(redMask);
            int cntY  = Core.countNonZero(yellowMask);
            int cntG  = Core.countNonZero(greenMask);
            int total = Core.countNonZero(maskLight);

            double ratioR = total > 0 ? cntR / (double) total : 0.0;
            double ratioY = total > 0 ? cntY / (double) total : 0.0;
            double ratioG = total > 0 ? cntG / (double) total : 0.0;

            double max = Math.max(ratioR, Math.max(ratioY, ratioG));
            double second = (max == ratioR) ? Math.max(ratioY, ratioG)
                    : (max == ratioY ? Math.max(ratioR, ratioG) : Math.max(ratioR, ratioY));

            if (TL_DEBUG_LOG) {
                Scalar mHue = Core.mean(hueChan, maskLight);
                Scalar mSat = Core.mean(satChan, maskLight);
                Scalar mVal = Core.mean(valueChan, maskLight);
                Log.d("TLDBG", String.format(
                        "roi=%dx%d total=%d R=%d Y=%d G=%d rR=%.3f rY=%.3f rG=%.3f hue=%.1f sat=%.1f val=%.1f",
                        w, h, total, cntR, cntY, cntG, ratioR, ratioY, ratioG, mHue.val[0], mSat.val[0], mVal.val[0]));
            }

            if (total >= TL_MIN_TOTALPX && max >= TL_MIN_RATIO && (max - second) >= TL_MIN_GAP) {
                if (max == ratioR) return "red";
                if (max == ratioY) return "yellow";
                return "green";
            }

            // 9) 鬆綁 fallback（亮區太少或比例不明顯時）
            Mat looseMask = new Mat();
            Mat valMask2 = new Mat(), satMask2 = new Mat();
            Imgproc.threshold(valueChan, valMask2, 60, 255, Imgproc.THRESH_BINARY); // V>=60
            Imgproc.threshold(satChan,   satMask2, 40, 255, Imgproc.THRESH_BINARY); // S>=40
            Core.bitwise_and(valMask2, satMask2, looseMask);
            valMask2.release(); satMask2.release();

            Mat red2 = new Mat(), tmp2 = new Mat(), y2 = new Mat(), g2 = new Mat();
            Core.inRange(mat, new Scalar(0, 40, 40),   new Scalar(10, 255, 255), red2);
            Core.inRange(mat, new Scalar(160, 40, 40), new Scalar(180, 255, 255), tmp2);
            Core.add(red2, tmp2, red2);
            Core.inRange(mat, new Scalar(10, 60, 60),  new Scalar(45, 255, 255), y2);
            Core.inRange(mat, new Scalar(35, 40, 40),  new Scalar(100,255, 255), g2);

            Core.bitwise_and(red2, looseMask, red2);
            Core.bitwise_and(y2,   looseMask, y2);
            Core.bitwise_and(g2,   looseMask, g2);

            int r2 = Core.countNonZero(red2);
            int y2c = Core.countNonZero(y2);
            int g2c = Core.countNonZero(g2);
            int tot2 = Core.countNonZero(looseMask);

            if (TL_DEBUG_LOG) {
                Log.d("TLDBG", String.format("fallback tot=%d r2=%d y2=%d g2=%d", tot2, r2, y2c, g2c));
            }

            red2.release(); tmp2.release(); y2.release(); g2.release();
            looseMask.release();

            if (tot2 > 4) {
                double rR2 = r2  / (double) tot2;
                double rY2 = y2c / (double) tot2;
                double rG2 = g2c / (double) tot2;
                double m2  = Math.max(rR2, Math.max(rY2, rG2));
                if (m2 >= 0.02) {
                    if (m2 == rR2) return "red";
                    if (m2 == rY2) return "yellow";
                    return "green";
                }
            }

            // 10) 最終兜底：Hue 平均（避免全是 unknown）
            Scalar mHue = Core.mean(hueChan, maskLight);
            Scalar mSat = Core.mean(satChan, maskLight);
            double hue = mHue.val[0], sat = mSat.val[0];
            if (sat >= 40) {
                if (hue <= 10 || hue >= 160) return "red";
                if (hue <= 45) return "yellow";
                if (hue <= 100) return "green";
            }
            return "unknown";

        } finally {
            mat.release(); mask.release(); maskLight.release();
            if (kernel != null) kernel.release();
            for (Mat m : hsv) m.release();
            redMask.release(); tmpRed.release(); yellowMask.release(); greenMask.release();
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
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
        if (detector != null) {
            try { detector.close(); } catch (Throwable ignore) {}
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
    private Matrix computeImageToViewMatrix(int imgW, int imgH, float viewW, float viewH,
                                            PreviewView.ScaleType scaleType) {
        Matrix m = new Matrix();

        // FIT_CENTER = letterbox：以較小比例縮放（不裁切）
        // FILL_CENTER = centerCrop：以較大比例縮放（會裁切）
        boolean isFit = (scaleType == PreviewView.ScaleType.FIT_CENTER
                || scaleType == PreviewView.ScaleType.FIT_START
                || scaleType == PreviewView.ScaleType.FIT_END);

        float sx = viewW / imgW;
        float sy = viewH / imgH;
        float scale = isFit ? Math.min(sx, sy) : Math.max(sx, sy);

        float dx = (viewW - imgW * scale) * 0.5f;
        float dy = (viewH - imgH * scale) * 0.5f;

        m.setScale(scale, scale);
        m.postTranslate(dx, dy);

        // 若你「沒有」手動把 Bitmap 旋轉正向，而是靠 setTargetRotation，
        // 且你的偵測結果仍在感測器座標，這裡需加上旋轉矩陣。
        // 不過你目前是把 Bitmap 旋轉為正向再送入模型，就不需要再旋轉。
        return m;
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

    private List<DetectorMain.Recognition> detectTiled(Bitmap src, int cols, int rows, int overlap) {
        int W = src.getWidth(), H = src.getHeight();
        int tileW = W / cols, tileH = H / rows;
        List<DetectorMain.Recognition> all = new ArrayList<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x0 = Math.max(0, c * tileW - overlap);
                int y0 = Math.max(0, r * tileH - overlap);
                int x1 = Math.min(W, (c + 1) * tileW + overlap);
                int y1 = Math.min(H, (r + 1) * tileH + overlap);
                int w = x1 - x0, h = y1 - y0;
                if (w <= 0 || h <= 0) continue;

                Bitmap tile = Bitmap.createBitmap(src, x0, y0, w, h);
                try {
                    List<DetectorMain.Recognition> part = detector.detect(tile, w, h);
                    for (DetectorMain.Recognition rec : part) {
                        RectF b = new RectF(rec.getLocation());
                        b.offset(x0, y0);  // 映回原圖
                        rec.setLocation(b);
                        all.add(rec);
                    }
                } finally {
                    tile.recycle(); // ✅ 這行很重要，避免記憶體壓力
                }
            }
        }
        return nmsByClass(all, IOU_NMS);
    }

    private List<DetectorMain.Recognition> nmsByClass(
            List<DetectorMain.Recognition> list, float iouTh) {

        Map<String, List<DetectorMain.Recognition>> by = new HashMap<>();

        for (DetectorMain.Recognition r : list) {
            List<DetectorMain.Recognition> grp = by.get(r.getTitle());
            if (grp == null) {
                grp = new ArrayList<>();
                by.put(r.getTitle(), grp);
            }
            grp.add(r);
        }

        List<DetectorMain.Recognition> out = new ArrayList<>();
        for (List<DetectorMain.Recognition> grp : by.values()) {
            out.addAll(nonMaxSuppression(grp, iouTh)); // 你原本的 NMS
        }
        return out;
    }

    private List<DetectorMain.Recognition> mapToViewCoordinates(
            List<DetectorMain.Recognition> imageResults,
            float viewW, float viewH, float imgW, float imgH) {
        float scale = Math.min(viewW / imgW, viewH / imgH);
        float dx = (viewW - imgW * scale) / 2f;
        float dy = (viewH - imgH * scale) / 2f;

        List<DetectorMain.Recognition> out = new ArrayList<>();
        for (DetectorMain.Recognition r : imageResults) {
            RectF b = r.getLocation();
            RectF vb = new RectF(
                    b.left * scale + dx, b.top * scale + dy,
                    b.right * scale + dx, b.bottom * scale + dy
            );
            r.setLocation(vb);
            out.add(r);
        }
        return out;
    }

    private int colorIdx(String c) {
        if ("red".equals(c)) return 1;
        if ("yellow".equals(c)) return 2;
        if ("green".equals(c)) return 3;
        return 0;
    }

    private String colorStr(int idx) {
        switch (idx) {
            case 1: return "red";
            case 2: return "yellow";
            case 3: return "green";
            default: return "unknown";
        }
    }

    private float iou(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float interW = Math.max(0f, right - left);
        float interH = Math.max(0f, bottom - top);
        float inter = interW * interH;
        float areaA = Math.max(0f, a.width()) * Math.max(0f, a.height());
        float areaB = Math.max(0f, b.width()) * Math.max(0f, b.height());
        float denom = areaA + areaB - inter;
        return denom > 0 ? inter / denom : 0f;
    }

    private String voteColorOverFrames(RectF box, String current) {
        // 清除過期
        for (int i = tlTracks.size() - 1; i >= 0; i--) {
            if (frameIndex - tlTracks.get(i).seenFrame > TRACK_TTL) tlTracks.remove(i);
        }

        // 尋找最像的 track（IoU 放寬到 0.30）
        TLTrack best = null; float bestIou = 0f;
        for (TLTrack t : tlTracks) {
            float iouVal = iou(t.box, box);
            if (iouVal > bestIou && iouVal >= TRACK_IOU_MATCH) { best = t; bestIou = iouVal; }
        }
        if (best == null) {
            best = new TLTrack();
            best.box = new RectF(box);
            best.seenFrame = frameIndex;
            best.votes = new int[4];
            tlTracks.add(best);
        }

        best.seenFrame = frameIndex;
        best.box = new RectF(box);

        int idx = colorIdx(current);

        // 若這幀不確定（unknown），維持穩定色並延長 hold 一點
        if (idx == 0) {
            if (best.stable != 0 && best.hold > 0) best.hold--;
            return colorStr(best.stable != 0 ? best.stable : 0);
        }

        // 候選色連續統計
        if (idx == best.cand) best.streak++;
        else { best.cand = idx; best.streak = 1; }

        // 若已確認且還在保留期，除非連續很久才允許切換
        if (best.stable != 0 && best.cand != best.stable) {
            if (best.streak >= COLOR_CONFIRM_FRAMES && best.hold <= 0) {
                best.stable = best.cand;
                best.hold   = COLOR_HOLD_FRAMES;
            }
        } else {
            // 初次確認，或候選==穩定：重設保留
            if (best.streak >= COLOR_CONFIRM_FRAMES) {
                best.stable = best.cand;
                best.hold   = COLOR_HOLD_FRAMES;
            }
        }

        return colorStr(best.stable);
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

    private void maybeFetchAndApplySpeedLimit(Location loc) {
        Log.e("OSMSpeed", "enter maybeFetch: lat=" + loc.getLatitude() + " lon=" + loc.getLongitude());

        long now = android.os.SystemClock.uptimeMillis();
        long delta = now - lastQueryMs;
        if (delta < OSM_QUERY_MIN_INTERVAL_MS) {
            Log.e("OSMSpeed", "skip by interval: " + delta + "ms");
            return;
        }

        float lat = (float) loc.getLatitude();
        float lng = (float) loc.getLongitude();

        if (!Float.isNaN(lastQueryLat) && !Float.isNaN(lastQueryLng)) {
            float moved = distanceMeters(lastQueryLat, lastQueryLng, lat, lng);
            if (moved < OSM_QUERY_MIN_MOVE_M) {
                Log.e("OSMSpeed", "skip by moved=" + moved + "m");
                return;
            }
        }

        lastQueryMs  = now;
        lastQueryLat = lat;
        lastQueryLng = lng;

        // ✅ 重點：同時抓 highway 與 maxspeed（最近的前幾筆）
        String q = "[out:json][timeout:8];"
                + "way(around:70," + lat + "," + lng + ")[\"highway\"];"
                + "out tags center 10;"; // out center 會帶回中心點，通常依距離排序

        Log.e("OSMSpeed", "QUERY=" + q);

        if (overpassApi == null) initOverpassApi();

        overpassApi.query(q).enqueue(new retrofit2.Callback<OverpassResp>() {
            @Override public void onResponse(Call<OverpassResp> call, Response<OverpassResp> resp) {
                OverpassResp body = resp.body();
                if (!resp.isSuccessful() || body == null) {
                    Log.e("OSMSpeed", "HTTP=" + resp.code() + " body=" + body);
                    return;
                }

                Integer decided = null;

                if (body.elements != null) {
                    // 依回傳順序（通常最近）挑第一個能決定速限的
                    for (OverpassResp.Element el : body.elements) {
                        if (el == null || el.tags == null) continue;

                        // 1) 有 maxspeed → 直接用
                        Integer v = pickMaxspeedFromTags(el.tags);
                        if (v != null) { decided = v; break; }

                        // 2) 沒有 maxspeed → 用 highway 類型推估
                        String hw = el.tags.get("highway");
                        Integer guess = HighwaySpeedTable.fromHighway(hw);
                        if (guess != null) { decided = guess; break; }
                    }
                }

                // 最後兜底
                if (decided == null) decided = DEFAULT_OSM_SPEED_KMH; // 仍給保守 50

                // ⚠️ 不再硬性 cap 在 50；若你想保險，可 cap 在 90
                // decided = Math.min(decided, 90);

                currentSpeedLimitKmh = decided;
                Log.e("OSMSpeed", "SET limit=" + currentSpeedLimitKmh);

                if (lastLocation != null) {
                    float sp = lastLocation.getSpeed() * 3.6f;
                    updateSpeedUi(sp);
                    maybeAlertOverspeed(sp, currentSpeedLimitKmh);
                }
            }

            @Override public void onFailure(Call<OverpassResp> call, Throwable t) {
                Log.e("OSMSpeed", "FAIL", t);
                // 保留原速限；或你也可設定成 DEFAULT_OSM_SPEED_KMH
            }
        });
    }

    private void fetchOsmMaxspeed(final double lat, final double lon, final int radiusM,
                                  final java.util.concurrent.atomic.AtomicBoolean triedWider) {
        if (overpassApi == null) return;

        // 只抓有 highway 的 way，且帶有 maxspeed；只輸出 tags 省流量
        String q = "[out:json][timeout:8];"
                + "way(around:" + radiusM + "," + lat + "," + lon + ")[\"highway\"][\"maxspeed\"];"
                + "out tags;";

        overpassApi.query(q).enqueue(new retrofit2.Callback<OverpassResp>() {
            @Override public void onResponse(retrofit2.Call<OverpassResp> call,
                                             retrofit2.Response<OverpassResp> resp) {
                OverpassResp body = resp.body();
                Integer kmh = null;

                if (body != null && body.elements != null) {
                    for (OverpassResp.Element e : body.elements) {
                        Integer v = pickMaxspeedFromTags(e.tags);
                        if (v != null) { kmh = v; break; }
                    }
                }

                if (kmh == null && !triedWider.get()) {
                    triedWider.set(true);
                    fetchOsmMaxspeed(lat, lon, 100, triedWider);
                    return;
                }

                currentSpeedLimitKmh = kmh; // 可能為 null（未找到）

                if (lastLocation != null) {
                    float sp = lastLocation.getSpeed() * 3.6f;
                    updateSpeedUi(sp);
                    maybeAlertOverspeed(sp, currentSpeedLimitKmh);
                }
            }

            @Override public void onFailure(retrofit2.Call<OverpassResp> call, Throwable t) {
                // 失敗就先不更新，保留原值
            }
        });
    }

    // =============== 超速提醒 Manager（最小可行版） ===============
    private static class SpeedAlertManager {

        interface SpeedLimitProvider {
            /** 回傳該點（可帶 heading）之速限，km/h；-1 表示未知 */
            void getSpeedLimitAsync(double lat, double lon, float heading, Callback cb);
            interface Callback { void onResult(int speedLimitKmh); }
        }

        interface Notifier {
            void onOverspeed(int speedKmh, int limitKmh);
            void onUpdate(int speedKmh, @androidx.annotation.Nullable Integer limitKmh);
        }

        private final Context ctx;
        private final SpeedLimitProvider provider;
        private final Notifier notifier;

        // 參數（之後可做成設定）
        private int   toleranceKmh   = 5;      // 固定容差
        private float tolerancePct   = 0.10f;  // 百分比容差（10%）
        private int   minHoldMs      = 3000;   // 需連續超速時間
        private int   cooldownMs     = 20000;  // 提醒冷卻
        private int   requeryMs      = 30000;  // 速限查詢間隔

        // 狀態
        private Integer lastLimit = null;
        private long lastLimitAt  = 0L;
        private long overspeedSince = 0L;
        private long lastAlertAt    = 0L;

        // 簡單平滑
        private final java.util.ArrayDeque<Float> buf = new java.util.ArrayDeque<>(4);

        SpeedAlertManager(Context c, SpeedLimitProvider p, Notifier n) {
            this.ctx = c.getApplicationContext();
            this.provider = p;
            this.notifier = n;
        }

        void onLocation(android.location.Location loc) {
            if (loc == null) return;
            if (loc.getAccuracy() > 25f) return; // 精度太差先跳過

            // 速度（km/h）＋簡單移動平均
            float v = loc.getSpeed() * 3.6f;
            if (buf.size() == 4) buf.removeFirst();
            buf.addLast(v);
            float speed = 0f; for (Float x: buf) speed += x; speed /= buf.size();
            int spd = Math.round(speed);

            // 方向（部分 map matching 會用到）
            float heading = loc.hasBearing() ? loc.getBearing() : Float.NaN;

            // 速限過舊或未知就查一次（之後你可改成：換道路/位移超過一定距離再查）
            long now = android.os.SystemClock.uptimeMillis();
            if (lastLimit == null || now - lastLimitAt > requeryMs) {
                final double lat = loc.getLatitude(), lon = loc.getLongitude();
                provider.getSpeedLimitAsync(lat, lon, heading, limit -> {
                    if (limit >= 0) {
                        lastLimit = limit;
                        lastLimitAt = android.os.SystemClock.uptimeMillis();
                    }
                });
            }

            notifier.onUpdate(spd, lastLimit);

            if (lastLimit != null && lastLimit > 0) {
                int tol = Math.max(toleranceKmh, Math.round(lastLimit * tolerancePct));
                boolean over = spd > (lastLimit + tol);
                if (over) {
                    if (overspeedSince == 0L) overspeedSince = now;
                    boolean held   = (now - overspeedSince) >= minHoldMs;
                    boolean cooled = (now - lastAlertAt)    >= cooldownMs;
                    if (held && cooled) {
                        lastAlertAt = now;
                        notifier.onOverspeed(spd, lastLimit);
                    }
                } else {
                    overspeedSince = 0L;
                }
            }
        }
    }

    interface OverpassApi {
        @retrofit2.http.GET("api/interpreter")
        retrofit2.Call<OverpassResp> query(@retrofit2.http.Query("data") String q);
    }
    static class OverpassResp {
        static class Element {
            java.util.Map<String,String> tags;
        }
        java.util.List<Element> elements;
    }

    // === UI 顯示「時速」 ===
    private void updateSpeedUi(float speedKmh) {
        tvSpeed.setText(String.format(java.util.Locale.TAIWAN, "時速：%.1f km/h ", speedKmh));
    }

    public static final class HighwaySpeedTable {
        private HighwaySpeedTable() {}
        public static Integer fromHighway(String highway) {

            if (highway == null) return 50; // 最後兜底
            switch (highway) {
                case "motorway":
                    return null;
                case "trunk":
                case "primary":
                case "secondary":
                case "tertiary":
                    return 50;
                case "residential":
                case "unclassified":
                    return 40;
                case "service":
                case "track":
                case "living_street":
                    return 30;
                default:
                    return 50;
            }
        }
    }
    // === 超速提醒（沿用你原本的語音/通知） ===
    private void maybeAlertOverspeed(float speedKmh, Integer limitKmh) {
        if (limitKmh == null || limitKmh <= 0) return;

        long now = android.os.SystemClock.uptimeMillis();
        boolean over = speedKmh > limitKmh * OVERSPEED_TOLERANCE;

        if (over) {
            if (overspeedSinceMs == 0L) overspeedSinceMs = now;
            boolean held   = (now - overspeedSinceMs) >= OVERSPEED_HOLD_MS;
            boolean cooled = (now - lastNotifMs)      >= NOTIF_COOLDOWN_MS;
            if (held && cooled) {
                lastNotifMs = now;
                speakOnce(String.format(java.util.Locale.TAIWAN,
                        "超速提醒，目前 %.0f，限速 %d，請減速", speedKmh, limitKmh));
                if (isVibrationEnabled) triggerMiBandVibration();
                sendAlertNotification("超速提醒",
                        String.format(java.util.Locale.TAIWAN, "目前 %.0f km/h，限速 %d km/h，請減速", speedKmh, limitKmh));
            }
        } else {
            overspeedSinceMs = 0L; // 一降速就重算
        }
    }

    // === Haversine：兩點距離（公尺） ===
    private static float distanceMeters(float lat1, float lon1, float lat2, float lon2) {
        double R = 6371000.0; // 地球半徑
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return (float)(R * c);
    }

    @androidx.annotation.Nullable
    private Integer parseMaxspeed(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();

        // 常見非數值
        if (s.equals("none") || s.equals("signals") || s.equals("variable") || s.equals("walk")) return null;

        // mph
        if (s.endsWith("mph")) {
            try {
                int v = Integer.parseInt(s.replace("mph","").trim());
                return Math.round(v * 1.60934f);
            } catch (Exception ignore) {}
        }

        // km/h 或 kph
        if (s.endsWith("km/h") || s.endsWith("kph")) {
            try {
                return Integer.parseInt(s.replace("km/h","").replace("kph","").trim());
            } catch (Exception ignore) {}
        }

        // 純數字（或夾雜字元取數字）
        try {
            String digits = s.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) return Integer.parseInt(digits);
        } catch (Exception ignore) {}

        return null;
    }

    @androidx.annotation.Nullable
    private Integer pickMaxspeedFromTags(java.util.Map<String,String> tags) {
        if (tags == null) return null;

        Integer v;
        v = parseMaxspeed(tags.get("maxspeed:signed"));
        if (v != null) return v;
        v = parseMaxspeed(tags.get("maxspeed:forward"));
        if (v != null) return v;
        v = parseMaxspeed(tags.get("maxspeed:backward"));
        if (v != null) return v;
        v = parseMaxspeed(tags.get("maxspeed"));
        if (v != null) return v;

        // 其他像 GB:nsl_single、TW:urban 之類國別代碼先略過（需要對照表就再加）
        return null;
    }
}