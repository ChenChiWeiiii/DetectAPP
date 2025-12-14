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
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DetectorMain {
    private static final String TAG = "DetectorMain";
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.4f;

    private final Interpreter interpreter;
    private final List<String> labels;
    private final String modelType;

    // ---   滨鍂蝺抵   ---
    private ByteBuffer inputBuffer;                 // (float32) 640*640*3*4 bytes
    private int[] pixels;                           // 640*640
    private final Bitmap resizedBitmap;             // 640x640嚗屸 踹 齿 誩  鰵撱
    private final Rect srcRect = new Rect();        //  𧞄撣 蝮格𦆮    滨鍂
    private final Rect dstRect = new Rect(0, 0, INPUT_SIZE, INPUT_SIZE);

    private Delegate delegate = null;

    public DetectorMain(AssetManager assetManager, String modelName, String modelType) throws IOException {
        this.modelType = modelType;
        MappedByteBuffer modelBuffer = loadModelFile(assetManager, modelName);

        // Interpreter 閮剖
        Interpreter.Options opts = new Interpreter.Options();
        opts.setUseXNNPACK(true);
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        opts.setNumThreads(threads);

        //   𡑒岫 GPU -> NNAPI -> CPU
        boolean attached = false;
        try {
            delegate = new GpuDelegate();
            opts.addDelegate(delegate);
            attached = true;
            Log.d(TAG, "TFLite delegate: GPU");
        } catch (Throwable t) {
            Log.w(TAG, "GPU delegate not available, fallback to NNAPI", t);
            delegate = null;
        }
        if (!attached) {
            try {
                delegate = new NnApiDelegate();
                opts.addDelegate(delegate);
                attached = true;
                Log.d(TAG, "TFLite delegate: NNAPI");
            } catch (Throwable t) {
                Log.w(TAG, "NNAPI delegate not available, fallback to CPU", t);
                delegate = null;
            }
        }

        interpreter = new Interpreter(modelBuffer, opts);

        // 璅嗵惜
        labels = new ArrayList<>();
        labels.add("crosswalk");
        labels.add("person");
        labels.add("traffic_light");

        // ---   嘥 见 㚚 滨鍂蝺抵   ---
        inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        inputBuffer.order(ByteOrder.nativeOrder());
        pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resizedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fd = assetManager.openFd(modelPath);
        try (FileInputStream is = new FileInputStream(fd.getFileDescriptor());
             FileChannel fc = is.getChannel()) {
            return fc.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    /**     bitmap  凒 𦻖 𧞄   虾  滨鍂   640x640 Bitmap嚗  滩 㗇   float32 NHWC */
    private ByteBuffer bitmapToFloatBuffer(Bitmap bitmap) {
        // 1)   𠰴 笔 𣇉葬   640x640    reused bitmap嚗䔶 滨𤩎  蠘𠪊   Bitmap
        srcRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        Canvas c = new Canvas(resizedBitmap);
        c.drawBitmap(bitmap, srcRect, dstRect, null);

        // 2) 霈  讐 惩   滨鍂   int[]
        resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        // 3) 憛怠 仿 滨鍂   ByteBuffer嚗㇌GB  0~1嚗
        inputBuffer.rewind();
        final int total = INPUT_SIZE * INPUT_SIZE;
        for (int i = 0; i < total; i++) {
            int p = pixels[i];
            inputBuffer.putFloat(((p >> 16) & 0xFF) / 255f); // R
            inputBuffer.putFloat(((p >> 8) & 0xFF) / 255f);  // G
            inputBuffer.putFloat((p & 0xFF) / 255f);         // B
        }
        inputBuffer.rewind();
        return inputBuffer;
    }

    public List<Recognition> detect(Bitmap bitmap, int previewWidth, int previewHeight) {
        int[] shape = interpreter.getOutputTensor(0).shape();
        ByteBuffer in = bitmapToFloatBuffer(bitmap);
        List<Recognition> recognitions = new ArrayList<>();

        if (modelType.equals("All")) {
            Log.d(TAG, "Model output shape: " + Arrays.toString(shape));
            int batch = shape[0];
            int numBoxes = shape[1];
            int numElements = shape[2];

            float[][][] output = new float[batch][numBoxes][numElements];
            interpreter.run(in, output);

            for (int i = 0; i < numBoxes; i++) {
                float x1 = output[0][i][0];
                float y1 = output[0][i][1];
                float x2 = output[0][i][2];
                float y2 = output[0][i][3];
                float confidence = output[0][i][4];
                int classId = (int) output[0][i][5];

                if (confidence > CONFIDENCE_THRESHOLD && classId >= 0 && classId < labels.size()) {
                    float left   = Math.max(0, x1 * previewWidth);
                    float top    = Math.max(0, y1 * previewHeight);
                    float right  = Math.min(previewWidth,  x2 * previewWidth);
                    float bottom = Math.min(previewHeight, y2 * previewHeight);

                    if (right > left && bottom > top) {
                        RectF rect = new RectF(left, top, right, bottom);
                        recognitions.add(new Recognition("" + i, labels.get(classId), confidence, rect));
                    }
                }
            }
        } else {
            Log.e(TAG, "Unsupported model output shape: " + Arrays.toString(shape));
        }
        return recognitions;
    }

    public static class Recognition {
        private final String id;
        private String title;
        private final float confidence;
        private RectF location;
        private String color = "unknown";  //     罸 讛𠧧

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