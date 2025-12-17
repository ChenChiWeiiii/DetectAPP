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
    private final String targetDeviceName = "Mi Smart Band"; //  虾 㺿  𣂷 惩祕  𤤿 鋆萘蔭  滨迂
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;
    private androidx.camera.core.Camera camera;

    // ==== 頞    鞾   ====
    private SpeedAlertManager speedMgr;
    // ---- Long-distance TL tuning ----
    private static final float INITIAL_ZOOM = 1.8f;   // 蝬 摰𡁶㮾璈笔   㛖鍂    鞱身霈羓
    private static final int   TARGET_W     = 1920;   // 敶勗 誩   鞱撓 亙祝
    private static final int   TARGET_H     = 1080;   // 敶勗 誩   鞱撓 仿

    private static final float TL_CONF   = 0.28f;     // 鈭日 朞 蠘    惇靽∪   瑼鳴   㰘 嗪𣪧撠讐 隞園 𡁜虜頛 雿𠬍
    private static final float IOU_NMS   = 0.80f;     // NMS IoU
    private static final int   MIN_BOX_PX = 8;        //   撠𤩺  讐 𩤃 屸 踹 滚臁
    // 憿讛𠧧 ế 𪃾蝛拙 𡁜
    private static final float TL_ROI_INSET = 0.04f;      // 減少裁切
    private static final double TL_MIN_RATIO = 0.018;     // 放寬比例
    private static final double TL_MIN_GAP = 0.008;       // 放寬差距
    private static final int TL_MIN_TOTALPX = 4;          // 降低最小像素
    private static final float TRACK_IOU_MATCH = 0.35f;  // 放寬匹配，減少 track 丟失
    private static final int COLOR_CONFIRM_FRAMES = 1;    // 加快確認
    private static final int COLOR_HOLD_FRAMES = 15;      // 增加穩定時間

    // 撟喲𪊽 綫隢吔   齿 𥟇芋  衤 蠘 賢 噼 嘅
    private static final int TILE_COLS = 2;           //   鍂 2  2嚗𥟇   質雲憭惩 滚   3  3
    private static final int TILE_ROWS = 2;
    private static final int TILE_OVERLAP = 40;       //  羓楠  滨 𠺪 屸 踹 滚   銝

    // 憭𡁜   閧巨嚗  㯄 讛𠧧 凒蝛抬
    private int frameIndex = 0;                       //  𣂼 蝝臬
    private static final int TRACK_TTL = 20;

    private static class TLTrack {
        RectF box;
        int seenFrame;
        int[] votes = new int[4]; // 靽萘
        int stable = 0;           //  𤌍  滨帘摰朞𠧧 (0/1/2/3)
        int cand   = 0;           //  訜  滚 䠷 貉𠧧
        int streak = 0;           //  䠷 貉𠧧      彍
        int hold   = 0;           // 撌脩Ⅱ隤滚 𣬚 靽萘 坔  彍
    }
    private final List<TLTrack> tlTracks = new ArrayList<>();
    private ExecutorService cameraExecutor;
    private Handler ui;
    private static final boolean TL_DEBUG_LOG   = true;   //  喟 讠敦蝭 撠  true
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
    // 敶勗 誩漣璅䠷    罸 閬  鍂
    private float currentDx = 0f, currentDy = 0f;
    private int lastImageHeightPx = 0;

    //   𧢲 罸𨘥   𣪧 𧑐擃睃漲嚗  砍偕嚗争    虾敺株矽  硋 𡁏 鞱身摰
    public static final float H_CAMERA = 1.40f;
    public float getCurrentDx() { return currentDx; }
    public float getCurrentDy() { return currentDy; }
    public int getLastImageHeightPx() { return lastImageHeightPx; }
    private static final String ALERT_CHANNEL_ID = "alert_channel_sound_novib";
    private static final String ALERT_CHANNEL_NAME = "銵䔶犖/蝝 蝬删   鞾  ";
    private BluetoothGattCharacteristic vibChar = null;         //     閧鍂 鸌敺萄 澆翰
    private final java.util.concurrent.atomic.AtomicBoolean analyzing = new java.util.concurrent.atomic.AtomicBoolean(false);
    private long lastAnalyzeMs = 0;
    private static final long MIN_INTERVAL_MS = 66; // ~15 FPS嚗 虾靘脲 笔 贝矽 㟲
    // ===== OSM Overpass ( 罸 鞉䰻閰  API) =====
    private static final String OVERPASS_BASE_URL = "https://overpass-api.de/";
    private OverpassApi overpassApi;

    // 蝭 瘚 嚗  踹   Overpass 鋡思 䭾 梶 嚗
    private static final float OSM_QUERY_MIN_MOVE_M = 120f;        // 雿滨宏 > 120m   齿䰻
    private static final long  OSM_QUERY_MIN_INTERVAL_MS = 20_000; //   𡝗   20 蝘雴 甈
    private static final int DEFAULT_OSM_SPEED_KMH = 50;

    private float lastQueryLat = Float.NaN, lastQueryLng = Float.NaN;
    private long lastQueryMs = 0L;

    //  𤌍  滚 硋 堒    罸 琜 ɑm/h嚗㚁 矝ull =  𧊋 䰻
    private Integer currentSpeedLimitKmh = DEFAULT_OSM_SPEED_KMH;

    // === 頞    鞾 埝綉     彍 ===
    private static final float OVERSPEED_TOLERANCE = 1.01f; // 摰孵 齿 𥪯 页 屸 踹   GPS   硋
    private static final long NOTIF_COOLDOWN_MS = 10_000;   //  𡁶䰻 瑕㭱 10 蝘
    private static final long OVERSPEED_HOLD_MS = 0_000;    //   蝥峕   枏ế摰  (0 銵函內蝡见朖)
    private long lastNotifMs = 0L;
    private long overspeedSinceMs = 0L;

    //   餈睲 甈∪ 帋
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
                textToSpeech.setLanguage(java.util.Locale.TAIWAN); // 雿輻鍂銝剜   鮋𨺗
            }
        });
        //  嘥 见 𤥁 滨 嗘蒂隢𧢲 甈𢠃
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        //requestBluetoothPermissions(); //  鐤 㙈  滨 蹱 𢠃 鞱
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

    //敺𧼮歇 滚 滩 萘蔭
    @SuppressLint("MissingPermission")
    private void tryConnectFromBonded() {
        if (!hasBtConnect()) { requestBtPermsIfNeeded(); return; }
        if (bluetoothAdapter == null) return;

        for (BluetoothDevice d : bluetoothAdapter.getBondedDevices()) { //   閬  CONNECT
            String name = d.getName();                                  //   閬  CONNECT
            if (name != null && (name.contains("Mi") || name.contains("Band") || name.contains("Xiaomi")
                    || (targetDeviceName != null && name.contains(targetDeviceName)))) {
                Log.d("MiBand", "敺𧼮歇 滚 滩 萘蔭 凒 𦻖  : " + name + " / " + d.getAddress());

                return;
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void triggerMiBandVibration() {
        if (!hasBtConnect()) { requestBtPermsIfNeeded(); return; }
        if (bluetoothGatt == null || vibChar == null) { Log.w("MiBand","gatt/char    null"); return; }
        vibChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        vibChar.setValue(new byte[]{0x02});
        try {
            boolean ok = bluetoothGatt.writeCharacteristic(vibChar);
            Log.d("MiBand","write vibrate -> " + ok);
        } catch (SecurityException se) {
            Log.e("MiBand","writeCharacteristic 鋡急  ", se);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            //   ⏛    㵪   隢碶 𦠜鰵 ID 銝齿   痹 䔶 靽嗪麬嚗
            NotificationChannel existing = nm.getNotificationChannel(ALERT_CHANNEL_ID);
            if (existing != null) nm.deleteNotificationChannel(ALERT_CHANNEL_ID);

            NotificationChannel ch = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    ALERT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT   //   㕑 脯  銝滚   鞎
            );
            ch.setDescription("  㕑 脯  銝漤     𧢲   㚁 峕 讠兛 眏 Mi Fitness  𨘥 誯    ");
            ch.enableVibration(false);                 //   𣈯
            ch.setVibrationPattern(new long[]{0});     //   𡒊Ⅱ銝漤
            Uri sound = Settings.System.DEFAULT_NOTIFICATION_URI;
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ch.setSound(sound, attrs);                 //   贝 脤𨺗
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

                    // 1. Preview 閮剖
                    Preview preview = new Preview.Builder().build();
                    previewView.setImplementationMode(PreviewView.ImplementationMode.PERFORMANCE);
                    previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);

                    // 摰匧 典 硋 埈 贝
                    Display display = previewView.getDisplay();
                    int rotation = (display != null) ? display.getRotation() : Surface.ROTATION_0;
                    preview.setTargetRotation(rotation);
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    // 2.  豢   屸𨘥
                    CameraSelector cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build();

                    // 3. ImageAnalysis 閮剖
                    ImageAnalysis analysis = new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(new android.util.Size(1920, 1080)) //   閬  滚翰 虾 㺿 1280x720
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) //     㺿 RGBA
                            .setImageQueueDepth(1)
                            .build();
                    analysis.setTargetRotation(rotation);

                    //    霈  Preview & Analysis  梁鍂  䔶    ViewPort嚗     /瘥𥪯 衤  稲嚗
                    androidx.camera.core.ViewPort vp =
                            new androidx.camera.core.ViewPort.Builder(
                                    new android.util.Rational(previewView.getWidth(), previewView.getHeight()),
                                    rotation //  鍂雿惩 漤𢒰撌脩 堒末   rotation
                            )
                                    .setScaleType(androidx.camera.core.ViewPort.FIT) // 撠漤   PreviewView    FIT_CENTER 憿舐內
                                    .build();

                    androidx.camera.core.UseCaseGroup ucg =
                            new androidx.camera.core.UseCaseGroup.Builder()
                                    .addUseCase(preview)   //  鍂雿惩 漤𢒰撌脣遣蝡讠  preview
                                    .addUseCase(analysis)  //  鍂雿惩 漤𢒰撌脣遣蝡讠  analysis
                                    .setViewPort(vp)
                                    .build();

                    cameraProvider.unbindAll();
                    camera = cameraProvider.bindToLifecycle(this, cameraSelector, ucg);

                    // 4. 閮剖   Analyzer嚗 遣霅啁鍂銝餃嘑銵𣬚 𡜐   閬  鍂 cameraExecutor 銋笔虾嚗
                    analysis.setAnalyzer(cameraExecutor, image -> {
                        if (!analyzing.compareAndSet(false, true)) { image.close(); return; }

                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - lastAnalyzeMs < MIN_INTERVAL_MS) { // ~15fps嚗 虾 䌊銵諹矽 㟲
                            image.close();
                            analyzing.set(false);
                            return;
                        }
                        lastAnalyzeMs = now;

                        try {
                            frameIndex++;

                            //    韏啣翰 蠘楝敺𡢅   删 箔 𢠃𢒰  𡃏撓 枂 㺿    RGBA嚗
                            Bitmap bitmap = imageToBitmapFast(image);
                            currentBitmap = bitmap;
                            lastImageHeightPx = bitmap.getHeight();

                            if (focalMm > 0 && sensorHeightMm > 0 &&
                                    (fPxY <= 0f || lastImageHeightPx != bitmap.getHeight())) {
                                fPxY = (focalMm / sensorHeightMm) * bitmap.getHeight();
                            }

                            //      䌊 拇 匧像 𪊽嚗𡁏 誯 𥪯 撟   滚   2  2嚗 𡖂銝 撟 頝穃 典 吔 屸 滢   GC
                            boolean useTiles = false; //  嗆彍撟  𡁜像 𪊽
                            if ((frameIndex & 1) == 0 && lastTLHeightPx > 0 && lastTLHeightPx < 20) { //   瑼餃虾隤
                                useTiles = true;
                            }
                            List<DetectorMain.Recognition> detAll = useTiles
                                    ? detectTiled(bitmap, TILE_COLS, TILE_ROWS, TILE_OVERLAP)
                                    : detector.detect(bitmap, bitmap.getWidth(), bitmap.getHeight());

                            // 鈭日 朞 蠘 𣬚   瑼餃   擧蕪嚗屸 滢 𤾸 屸𢒰 OpenCV   鞎䭾
                            List<DetectorMain.Recognition> filtered = new ArrayList<>();
                            for (DetectorMain.Recognition r : detAll) {
                                if ("traffic_light".equals(r.getTitle())) {
                                    if (r.getConfidence() >= TL_CONF &&
                                            Math.min(r.getLocation().width(), r.getLocation().height()) >= MIN_BOX_PX) { // 蝔滚凝 𦆮憭扳 撠𤩺
                                        filtered.add(r);
                                    }
                                } else {
                                    filtered.add(r);
                                }
                            }

                            List<DetectorMain.Recognition> kept = nmsByClass(filtered, IOU_NMS);

                            // =====  ế 𪃾    罸 讛𠧧嚗  漤朌 +  蘨撠齿 憭批嗾 见 𡄯   =====
                            float maxTlH = -1f;

                            //   𤣰      厩 蝬删
                            List<DetectorMain.Recognition> tls = new ArrayList<>();
                            for (DetectorMain.Recognition r : kept) {
                                if ("traffic_light".equals(r.getTitle())) {
                                    tls.add(r);
                                    //   靘輯 㗛   憭折 睃漲嚗䔶 𥕦 屸𢒰 䌊 拇 厩鍂
                                    if (r.getLocation().height() > maxTlH) maxTlH = r.getLocation().height();
                                }
                            }
                            if (maxTlH > 0) lastTLHeightPx = maxTlH;

                            // 靘  bbox 擃睃漲 眏憭批 撠𤩺 鍦 𧶏  之   𡁜虜瘥磰 餈㻫  瘥磰 皜 璆𡄯
                            tls.sort((a, b) -> Float.compare(b.getLocation().height(), a.getLocation().height()));

                            //  蘨  烐 憭抒  1嚚 2  见 𡁜ế 𠧧嚗  踹 齿 誩 憭折   OpenCV 閮  梹
                            int maxColorCheck = Math.min(2, tls.size());

                            // 瘥  3 撟   滚 帋 甈∪ế 𠧧嚗  漤朌嚗屸 滢 𤾸辣 莎
                            // 每幀都偵測顏色，透過追蹤機制穩定化
                            boolean doColorThisFrame = true;

                            if (doColorThisFrame) {
                                for (int i = 0; i < maxColorCheck; i++) {
                                    DetectorMain.Recognition r = tls.get(i);
                                    // 先偵測原始顏色
                                    String rawColor = detectTrafficLightColor(bitmap, r.getLocation());
                                    // 🔧 關鍵修改：透過追蹤機制穩定化顏色
                                    String stabilizedColor = voteColorOverFrames(r.getLocation(), rawColor);
                                    // 設定穩定後的顏色
                                    r.setColor(stabilizedColor);

                                    // 可以打開 log 觀察效果
                                    if (TL_DEBUG_LOG) {
                                        Log.d("DEBUG_TL", String.format("TL #%d: raw=%s → stable=%s bbox=%s",
                                                i, rawColor, stabilizedColor, r.getLocation()));
                                    }
                                }
                            }

                            //  園 䁅 撠讐     峕𧋦撟    滚ế嚗偦 讛𠧧   銁銋见    Ｘ Ｚ 靝

                            //   惩    Overlay 摨扳 辷   萘 嗘 惩  𧋦頝嗪𣪧/憿舐內 讛摩嚗
                            int imgW = bitmap.getWidth(), imgH = bitmap.getHeight();
                            List<DetectorMain.Recognition> viewResults = toOverlayResults(kept, imgW, imgH);

                            // UI  凒 鰵銝笔 硺蜓 嘑銵𣬚
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
    //  𥅾雿䭾 埝 厰 坔 𧢲 雿㵪 屸 靘踹 牐
    private final java.util.concurrent.atomic.AtomicReference<Bitmap> reusableBmp =
            new java.util.concurrent.atomic.AtomicReference<>();

    private Bitmap imageToBitmapFast(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        int w = image.getWidth(), h = image.getHeight();

        Bitmap bmp = reusableBmp.get();
        if (bmp == null || bmp.getWidth() != w || bmp.getHeight() != h) {
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            reusableBmp.set(bmp);
        }

        ByteBuffer buf = plane.getBuffer();
        buf.rewind();
        bmp.copyPixelsFromBuffer(buf);

        int imgRotation = image.getImageInfo().getRotationDegrees();
        if (imgRotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(imgRotation);
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, w, h, m, true);

            if (rotated != bmp) {
                reusableBmp.set(rotated);
            }

            return rotated;
        }

        return bmp;
    }



    private final int[] pvLoc = new int[2];
    private final int[] ovLoc = new int[2];

    private RectF mapRectToOverlay(RectF imgRect, int imgW, int imgH) {
        float viewW = overlayView.getWidth();
        float viewH = overlayView.getHeight();

        //     PreviewView.FIT_CENTER 撠齿 厩  letterbox 蝮格𦆮
        float scale = Math.min(viewW / imgW, viewH / imgH);
        float dx = (viewW - imgW * scale) / 2f;
        float dy = (viewH - imgH * scale) / 2f;

        //  𥅾 previewView     overlayView  銁  摰孵膥雿滨蔭銝滚 䕘 諹 靝 羓㮾撠滢 滨宏
        previewView.getLocationInWindow(pvLoc);
        overlayView.getLocationInWindow(ovLoc);
        dx += (pvLoc[0] - ovLoc[0]);
        dy += (pvLoc[1] - ovLoc[1]);

        // 靽萘 嗘 䭾𠳿  厩 頝嗪𣪧/憿舐內 讛摩   鍂    瘥𥪯 贝   讐宏
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
            //  鍂銝餃嘑銵𣬚   Looper
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000, 0, locationListener, Looper.getMainLooper());
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 2000, 0, locationListener, Looper.getMainLooper());

            // 蝡见  鍂銝𠹺 甈∩ 滨蔭閫貊䔄銝 甈∴  停蝞埈 垍宏  蓥 蠘 賣   API嚗
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
                                Toast.makeText(MainActivity.this, "   𡁜仃   !", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "   𡁜仃   !", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnLogout.setOnClickListener(v -> {
            SharedPreferences loginPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            loginPrefs.edit().remove("user_id").apply();

            Toast.makeText(MainActivity.this, "撌脩蒈 枂", Toast.LENGTH_SHORT).show();

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
        if (bluetoothGatt != null) return; // 撌脫 厰   𡁶 隞嗅停銝漤 滢

        //   滨 躰
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w("MiBand", "  滨 蹱𧊋  见   諹 见   见 蠘 滨  ");
            try { startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); } catch (Exception ignore) {}
            return;
        }
        //   𣂷 𥟇 笔 𧢲   誯 閬   见 帋
        if (!isLocationEnabled()) {
            Log.w("MiBand", "蝟餌絞摰帋 齿𧊋  见    虾 賢 舘稲    讐 箇征");
            try { startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } catch (Exception ignore) {}
        }
        // 甈𢠃 琜 ㇁ndroid 12+嚗
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
        tryConnectFromBonded(); //    𧼮歇 滚 滨凒   峕 埝 匧 齿
    }

    private void sendAlertNotification(String title, String content) {
        //if (!isVibrationEnabled) return;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) //  𤜯  𥟇 𣂷 㰘䌊撌梁  icon
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // 瑼Ｘ䰻甈𢠃
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } else {
            Log.w(" 𡁶䰻甈𢠃  ", "撠𡁏𧊋  硋 烾 𡁶䰻甈𢠃 琜 𣬚 ⊥ 閖＊蝷粹 𡁶䰻");
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
                //  凒 𦻖霈   硋 𥕦 𥕦銁 analyzer 摮睃末  憿讛𠧧
                String color = r.getColor();
                Log.d("DEBUG_TL", "TrafficLight color (  𣂼   菜葫) = " + color);
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
            lastSpeechTime = now; //  凒 鰵隤鮋𨺗 瑕㭱
            Log.d("  鞾  ", " 偘 𦆮隤鮋𨺗嚗 " + message);
        }
    }

    private String detectTrafficLightColor(Bitmap fullBmp, RectF rawBox) {
        Mat mat = new Mat(), mask = new Mat(), maskLight = new Mat(), kernel = null;
        List<Mat> hsv = new ArrayList<>();
        Mat redMask = new Mat(), tmpRed = new Mat(), yellowMask = new Mat(), greenMask = new Mat();

        try {
            // 1) ROI 蝔滚凝 抒葬嚗屸 踹 漤 羓楠
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

            // 2) 頧  HSV
            Utils.bitmapToMat(cropSmall, mat);
            cropSmall.recycle();
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR);
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2HSV);

            // 3)    H/S/V
            Core.split(mat, hsv);
            Mat hueChan   = hsv.get(0);
            Mat satChan   = hsv.get(1);
            Mat valueChan = hsv.get(2);

            // 4)  鍂 Otsu  𪄳鈭桀  +   滢 𡡞ˊ   漲  瑼
            Imgproc.threshold(valueChan, mask, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
            Mat satMask = new Mat();
            Imgproc.threshold(satChan, satMask, 60, 255, Imgproc.THRESH_BINARY); // 60 瘥  100 撖祇
            Core.bitwise_and(mask, satMask, mask);
            satMask.release();

            // 5) 頛訫漲  钅 匧縧 臁
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN,  kernel);
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);

            // 6)   𡝗 憭找漁
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

            // 7) 銝㕑𠧧 焵 頣  銁 HSV 蝛粹 橒
            Core.inRange(mat, LOWER_RED1, UPPER_RED1, redMask);
            Core.inRange(mat, LOWER_RED2, UPPER_RED2, tmpRed);
            Core.add(redMask, tmpRed, redMask);
            Core.inRange(mat, LOWER_YELLOW, UPPER_YELLOW, yellowMask);
            Core.inRange(mat, LOWER_GREEN, UPPER_GREEN,   greenMask);

            //     鈭桀  㮾鈭
            Core.bitwise_and(redMask,    maskLight, redMask);
            Core.bitwise_and(yellowMask, maskLight, yellowMask);
            Core.bitwise_and(greenMask,  maskLight, greenMask);

            // 8)  𠂔 聢 ế 𠧧嚗 蜓閬 頝臬 𡢅
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

            // 9) 擛 蝬  fallback嚗 漁  憭芸 烐 𡝗 𥪯 衤 齿 𡡞＊  嚗
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

            // 10)   蝯  𨅯 𤏪 䥅ue 撟喳     踹 滚 冽糓 unknown嚗
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
        //     confidence  眏憭批 撠𤩺 鍦
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

        // FIT_CENTER = letterbox嚗帋誑頛 撠𤩺 𥪯 讠葬 𦆮嚗  滩
        // FILL_CENTER = centerCrop嚗帋誑頛 憭扳 𥪯 讠葬 𦆮嚗  鋆
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

        //  𥅾雿𨬭 峕 埝 剹 齿 见 閙   Bitmap   贝 㗇迤  𡢅 諹 峕糓    setTargetRotation嚗
        // 銝𥪯 删  菜葫蝯鞉 靝 滚銁  葫 膥摨扳 辷 屸 躰ㄐ    牐 𦠜 贝 厩畆 腼
        // 銝漤 𦒘 删𤌍  齿糓    Bitmap   贝 厩 箸迤  穃 漤   交芋  页  停銝漤 閬  齿 贝 剹
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
                        b.offset(x0, y0);  //   惩 𧼮 笔
                        rec.setLocation(b);
                        all.add(rec);
                    }
                } finally {
                    tile.recycle(); //     躰     滩 嚗屸 踹 滩 䀹 園 𥪜 枏
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
            out.addAll(nonMaxSuppression(grp, iouTh)); // 雿惩  𧋦   NMS
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
        // 清理過期
        for (int i = tlTracks.size() - 1; i >= 0; i--) {
            if (frameIndex - tlTracks.get(i).seenFrame > TRACK_TTL) tlTracks.remove(i);
        }

        // 找最佳匹配 track
        TLTrack best = null;
        float bestIou = 0f;
        for (TLTrack t : tlTracks) {
            float iouVal = iou(t.box, box);
            if (iouVal > bestIou && iouVal >= TRACK_IOU_MATCH) {
                best = t;
                bestIou = iouVal;
            }
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

        // unknown 處理 - 給予更多容忍度
        if (idx == 0) {
            if (best.stable != 0) {
                if (best.hold > COLOR_HOLD_FRAMES / 2) {
                    return colorStr(best.stable);
                } else if (best.hold > 0) {
                    best.hold--;
                    return colorStr(best.stable);
                } else {
                    best.stable = 0;
                    best.cand = 0;
                    best.streak = 0;
                    return "unknown";
                }
            }
            return "unknown";
        }

        // 候選顏色連續計數
        if (idx == best.cand) {
            best.streak++;
        } else {
            best.cand = idx;
            best.streak = 1;
        }

        // 顏色確認邏輯
        if (best.stable != 0) {
            if (best.cand != best.stable) {
                if (best.streak >= COLOR_CONFIRM_FRAMES) {
                    best.stable = best.cand;
                    best.hold = COLOR_HOLD_FRAMES;
                }
            } else {
                best.hold = COLOR_HOLD_FRAMES;
            }
        } else if (best.streak >= COLOR_CONFIRM_FRAMES) {
            best.stable = best.cand;
            best.hold = COLOR_HOLD_FRAMES;
        }

        // 🔧 關鍵修正：返回邏輯
        if (best.stable != 0) {
            return colorStr(best.stable);
        } else {
            // stable 還沒建立時，返回候選顏色而非 unknown
            return colorStr(best.cand);
        }
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

        //      漤 痹 𡁜 峕     highway     maxspeed嚗  餈𤑳   滚嗾蝑 嚗
        String q = "[out:json][timeout:8];"
                + "way(around:70," + lat + "," + lng + ")[\"highway\"];"
                + "out tags center 10;"; // out center   撣嗅 硺葉敹 暺痹 屸 𡁜虜靘肽 嗪𣪧  鍦

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
                    // 靘嘥 𧼮 喲 摨𧶏   𡁜虜  餈𡢅 㗇 𤑳洵銝  贝 賣捱摰𡁻 罸 鞟
                    for (OverpassResp.Element el : body.elements) {
                        if (el == null || el.tags == null) continue;

                        // 1)     maxspeed     凒 𦻖 鍂
                        Integer v = pickMaxspeedFromTags(el.tags);
                        if (v != null) { decided = v; break; }

                        // 2) 瘝埝   maxspeed     鍂 highway 憿𧼮 𧢲綫隡
                        String hw = el.tags.get("highway");
                        Integer guess = HighwaySpeedTable.fromHighway(hw);
                        if (guess != null) { decided = guess; break; }
                    }
                }

                //   敺  𨅯
                if (decided == null) decided = DEFAULT_OSM_SPEED_KMH; // 隞滨策靽嘥   50

                //   𩤃   銝滚 滨′   cap  銁 50嚗𥡝𥅾雿䭾 喃 嗪麬嚗 虾 cap  銁 90
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
                // 靽萘 坔 罸 罸 琜 𥟇 碶 牐 笔虾閮剖 𡁏   DEFAULT_OSM_SPEED_KMH
            }
        });
    }

    private void fetchOsmMaxspeed(final double lat, final double lon, final int radiusM,
                                  final java.util.concurrent.atomic.AtomicBoolean triedWider) {
        if (overpassApi == null) return;

        //  蘨  𤘪   highway    way嚗䔶 𥪜葆    maxspeed嚗𥕦蘨頛詨枂 tags   瘚
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

                currentSpeedLimitKmh = kmh; //  虾 賜   null嚗 𧊋 𪄳  嚗

                if (lastLocation != null) {
                    float sp = lastLocation.getSpeed() * 3.6f;
                    updateSpeedUi(sp);
                    maybeAlertOverspeed(sp, currentSpeedLimitKmh);
                }
            }

            @Override public void onFailure(retrofit2.Call<OverpassResp> call, Throwable t) {
                // 憭望 堒停   齿凒 鰵嚗䔶 萘 坔 笔
            }
        });
    }

    private static class SpeedAlertManager {

        interface SpeedLimitProvider {
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

        //    彍嚗  见  虾 𡁏 鞱身摰𡄯
        private int   toleranceKmh   = 5;      //  𤐄摰𡁜捆撌
        private float tolerancePct   = 0.10f;  //  蓡  瘥𥪜捆撌殷  10%嚗
        private int   minHoldMs      = 3000;   //      諹
        private int   cooldownMs     = 20000;  //   鞾 鍦 瑕㭱
        private int   requeryMs      = 30000;  //  罸 鞉䰻閰ａ 㯄

        private Integer lastLimit = null;
        private long lastLimitAt  = 0L;
        private long overspeedSince = 0L;
        private long lastAlertAt    = 0L;

        private final java.util.ArrayDeque<Float> buf = new java.util.ArrayDeque<>(4);

        SpeedAlertManager(Context c, SpeedLimitProvider p, Notifier n) {
            this.ctx = c.getApplicationContext();
            this.provider = p;
            this.notifier = n;
        }

        void onLocation(android.location.Location loc) {
            if (loc == null) return;
            if (loc.getAccuracy() > 25f) return; // 蝎曉漲憭芸榆  歲

            //  笔漲嚗ɑm/h嚗㚁 讠陛 鱓蝘餃 訫像
            float v = loc.getSpeed() * 3.6f;
            if (buf.size() == 4) buf.removeFirst();
            buf.addLast(v);
            float speed = 0f; for (Float x: buf) speed += x; speed /= buf.size();
            int spd = Math.round(speed);

            //  䲮  𡢅   典  map matching    鍂  嚗
            float heading = loc.hasBearing() ? loc.getBearing() : Float.NaN;

            //  罸 鞾 舘 𦠜 𡝗𧊋 䰻撠望䰻銝 甈∴   见 䔶 惩虾 㺿  琜 𡁏 偦 栞楝/雿滨宏頞  𦒘 摰朞 嗪𣪧 齿䰻嚗
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

    // === UI 憿舐內 峕  麄   ===
    private void updateSpeedUi(float speedKmh) {
        tvSpeed.setText(String.format(java.util.Locale.TAIWAN, "      %.1f km/h ", speedKmh));
    }

    public static final class HighwaySpeedTable {
        private HighwaySpeedTable() {}
        public static Integer fromHighway(String highway) {

            if (highway == null) return 50; //   敺  𨅯
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
    // === 頞    鞾 𡜐  窒 鍂雿惩  𧋦  隤鮋𨺗/ 𡁶䰻嚗  ===
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
            overspeedSinceMs = 0L; // 銝   漤 笔停  滨
        }
    }

    // === Haversine嚗𡁜 拚 噼 嗪𣪧嚗  砍偕嚗  ===
    private static float distanceMeters(float lat1, float lon1, float lat2, float lon2) {
        double R = 6371000.0; //  𧑐    𠰴
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

        // 撣貉 钅 墧彍
        if (s.equals("none") || s.equals("signals") || s.equals("variable") || s.equals("walk")) return null;

        // mph
        if (s.endsWith("mph")) {
            try {
                int v = Integer.parseInt(s.replace("mph","").trim());
                return Math.round(v * 1.60934f);
            } catch (Exception ignore) {}
        }

        // km/h     kph
        if (s.endsWith("km/h") || s.endsWith("kph")) {
            try {
                return Integer.parseInt(s.replace("km/h","").replace("kph","").trim());
            } catch (Exception ignore) {}
        }

        // 蝝娍彍摮梹   硋冗  𨅯 堒    𡝗彍摮梹
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

        //  嗡 硋   GB:nsl_single  TW:urban 銋钅 𧼮 见ê̌隞 Ⅳ  裦 𠬍   閬 撠滨 扯”撠勗 滚 𩤃
        return null;
    }
}