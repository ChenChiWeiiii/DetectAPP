package com.example.detect;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class DetectorMain {
    private static final String TAG = "DetectorMain";
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.28f;
    private final Interpreter interpreter;
    private final List<String> labels;
    private final String modelType;

    //三次除法改用查表
    private static final float[] NORM_LUT = new float[256];
    static {
        for (int i = 0; i < 256; i++) NORM_LUT[i] = i / 255f;
    }

    private final List<Recognition> tmpResults = new ArrayList<>();

    // --- 重用緩衝 ---
    private ByteBuffer inputBuffer;                 // (float32) 640*640*3*4 bytes
    private int[] pixels;                           // 640*640
    private final Bitmap resizedBitmap;             // 640x640，避免每幀新建
    private final Rect srcRect = new Rect();        // 畫布縮放時重用
    private final Rect dstRect = new Rect(0, 0, INPUT_SIZE, INPUT_SIZE);
    private Delegate delegate = null;
    private int outBatch, outBoxes, outElems;
    private float[][][] out3d;   // 扁平化輸出緩衝
    private final java.util.HashMap<Integer, Object> outputMap = new java.util.HashMap<>(1);
    private final Canvas reuseCanvas;
    public DetectorMain(AssetManager assetManager, String modelName, String modelType) throws IOException {
        this.modelType = modelType;
        MappedByteBuffer modelBuffer = loadModelFile(assetManager, modelName);

        // 先用暫時 Interpreter 讀一次輸出 shape（不加 delegate，開銷極小）
        Interpreter tmp = new Interpreter(modelBuffer);
        int[] oshp = tmp.getOutputTensor(0).shape(); // [1, 300, 6]
        tmp.close();

        outBatch = oshp[0];
        outBoxes = oshp[1];
        outElems = oshp[2];

// 配成 3D
        out3d = new float[outBatch][outBoxes][outElems];

// outputMap 綁定 3D 陣列
        outputMap.put(0, out3d);


        // Interpreter 設定
        Interpreter.Options opts = new Interpreter.Options();
        opts.setUseXNNPACK(true);
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        opts.setNumThreads(threads);

        boolean attached = false;
        // 嘗試 GPU -> NNAPI -> CPU
        try {
            // GPU 優先
            CompatibilityList compat = new CompatibilityList();
            if (compat.isDelegateSupportedOnThisDevice()) {
                GpuDelegate.Options gopt = compat.getBestOptionsForThisDevice();
                delegate = new GpuDelegate(gopt);
                opts.addDelegate(delegate);
                attached = true;
                Log.d(TAG, "TFLite delegate: GPU");
            }
        } catch (Throwable t) {
            Log.w(TAG, "GPU delegate not available", t);
            delegate = null;
        }

        if (!attached) {
            try {
                // NNAPI 後援
                delegate = new NnApiDelegate();
                opts.addDelegate(delegate);
                attached = true;
                Log.d(TAG, "TFLite delegate: NNAPI");
            } catch (Throwable t) {
                Log.w(TAG, "NNAPI not available, fallback to CPU", t);
                delegate = null;
            }
        }

        interpreter = new Interpreter(modelBuffer, opts);

        // 標籤
        labels = new ArrayList<>();
        labels.add("crosswalk");
        labels.add("person");
        labels.add("traffic_light");

        // --- 初始化重用緩衝 ---
        inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        inputBuffer.order(ByteOrder.nativeOrder());
        pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resizedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        reuseCanvas   = new Canvas(resizedBitmap);
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fd = assetManager.openFd(modelPath);
        try (FileInputStream is = new FileInputStream(fd.getFileDescriptor());
             FileChannel fc = is.getChannel()) {
            return fc.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    /** 把 bitmap 直接畫到可重用的 640x640 Bitmap，再轉成 float32 NHWC */
    private ByteBuffer bitmapToFloatBuffer(Bitmap bitmap) {
        // 1) 把原圖縮到 640x640 的 reused bitmap，不產生臨時 Bitmap
        srcRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        reuseCanvas.drawBitmap(bitmap, srcRect, dstRect, null);

        // 2) 讀像素到重用的 int[]
        resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        // 3) 填入重用的 ByteBuffer（RGB、0~1）
        inputBuffer.rewind();
        final int total = INPUT_SIZE * INPUT_SIZE;

// 每次處理 4 個像素
        int i = 0;
        for (; i <= total - 4; i += 4) {
            int p0 = pixels[i];
            inputBuffer.putFloat(NORM_LUT[(p0 >> 16) & 0xFF]);
            inputBuffer.putFloat(NORM_LUT[(p0 >>  8) & 0xFF]);
            inputBuffer.putFloat(NORM_LUT[(p0      ) & 0xFF]);

            int p1 = pixels[i + 1];
            inputBuffer.putFloat(NORM_LUT[(p1 >> 16) & 0xFF]);
            inputBuffer.putFloat(NORM_LUT[(p1 >>  8) & 0xFF]);
            inputBuffer.putFloat(NORM_LUT[(p1      ) & 0xFF]);

            int p2 = pixels[i + 2];
            inputBuffer.putFloat(NORM_LUT[(p2 >> 16) & 0xFF]);
            inputBuffer.putFloat(NORM_LUT[(p2 >>  8) & 0xFF]);
            inputBuffer.putFloat(NORM_LUT[(p2      ) & 0xFF]);

            int p3 = pixels[i + 3];
            inputBuffer.putFloat(NORM_LUT[(p3 >> 16) & 0xFF]);
            inputBuffer.putFloat(NORM_LUT[(p3 >>  8) & 0xFF]);
            inputBuffer.putFloat(NORM_LUT[(p3      ) & 0xFF]);
        }

// 可能剩 0~3 個像素沒處理，補尾巴
        for (; i < total; i++) {
            int p = pixels[i];
            inputBuffer.putFloat(NORM_LUT[(p >> 16) & 0xFF]);
            inputBuffer.putFloat(NORM_LUT[(p >>  8) & 0xFF]);
            inputBuffer.putFloat(NORM_LUT[(p      ) & 0xFF]);
        }
        inputBuffer.rewind();
        return inputBuffer;
    }

    public List<Recognition> detect(Bitmap bitmap, int previewWidth, int previewHeight) {
        ByteBuffer in = bitmapToFloatBuffer(bitmap);
        tmpResults.clear();

        if ("All".equals(modelType)) {
            // 跑推論：輸出寫進 out3d（[1, 300, 6]）
            interpreter.runForMultipleInputsOutputs(new Object[]{ in }, outputMap);

            // 解析 out3d（[batch=0][i][elem]）
            for (int i = 0; i < outBoxes; i++) {
                float x1 = out3d[0][i][0];
                float y1 = out3d[0][i][1];
                float x2 = out3d[0][i][2];
                float y2 = out3d[0][i][3];
                float confidence = out3d[0][i][4];
                int   classId    = (int) out3d[0][i][5];

                // 先過濾信心與類別
                if (confidence <= CONFIDENCE_THRESHOLD) continue;
                if (classId < 0 || classId >= labels.size()) continue;

                // 模型輸出假設是 0~1 的相對座標（左上 x1,y1；右下 x2,y2）
                float left   = Math.max(0, x1 * previewWidth);
                float top    = Math.max(0, y1 * previewHeight);
                float right  = Math.min(previewWidth,  x2 * previewWidth);
                float bottom = Math.min(previewHeight, y2 * previewHeight);
                if (right <= left || bottom <= top) continue;

                tmpResults.add(new Recognition(
                        String.valueOf(i),
                        labels.get(classId),
                        confidence,
                        new RectF(left, top, right, bottom)
                ));
            }
        } else {
            Log.e(TAG, "Unsupported model output");
        }
        return tmpResults;
    }

    public static class Recognition {
        private final String id;
        private String title;
        private final float confidence;
        private RectF location;
        private String color = "unknown";  // 燈號顏色
        private float colorStrength = 0f;
        public float getColorStrength() { return colorStrength; }
        public void setColorStrength(float s) { this.colorStrength = s; }

        public Recognition(String id, String title, float confidence, RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public float getConfidence() { return confidence; }
        public RectF getLocation() { return location; }
        public void setLocation(RectF location) { this.location = location; }
        public void setTitle(String title) { this.title = title; }

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
    }

    public void close() {
        try { interpreter.close(); } catch (Throwable ignore) {}
        try { if (delegate != null) delegate.close(); } catch (Throwable ignore) {}
    }
}