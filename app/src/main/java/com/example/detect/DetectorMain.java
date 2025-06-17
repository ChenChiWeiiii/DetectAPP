package com.example.detect;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;

public class DetectorMain {
    private static final String TAG = "DetectorMain";
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.5f;

    private final Interpreter interpreter;
    private final List<String> labels1;
    private final List<String> labels2;
    private final int outputChannels;
    private Bitmap resizedBitmap;

    public DetectorMain(AssetManager assetManager, String modelName, int outputChannels) throws IOException {
        this.outputChannels = outputChannels;
        MappedByteBuffer modelBuffer = loadModelFile(assetManager, modelName);
        interpreter = new Interpreter(modelBuffer);

        labels1 = new ArrayList<>();
        labels1.add("crosswalk");
        labels1.add("traffic_light");

        labels2 = new ArrayList<>(Collections.nCopies(80, null)); // YOLOv8 COCO 原始為 80 類
        labels2.set(0, "person");
        labels2.set(3, "motorcycle");

    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private ByteBuffer bitmapToFloatBuffer(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        buffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int i = 0; i < pixels.length; ++i) {
            int pixel = pixels[i];
            float r = ((pixel >> 16) & 0xFF) / 255.0f;
            float g = ((pixel >> 8) & 0xFF) / 255.0f;
            float b = (pixel & 0xFF) / 255.0f;

            buffer.putFloat(r);
            buffer.putFloat(g);
            buffer.putFloat(b);
        }
        buffer.rewind();
        return buffer;
    }

    public List<Recognition> detect(Bitmap bitmap, int previewWidth, int previewHeight) {
        int[] shape = interpreter.getOutputTensor(0).shape();
        ByteBuffer inputBuffer = bitmapToFloatBuffer(bitmap);
        List<Recognition> recognitions = new ArrayList<>();

        if (shape[1] == 8400 && shape[2] == 6) { //best_float16_1.tflite

            Log.d(TAG, "Model output shape: " + Arrays.toString(shape));

            int batch = shape[0];
            int numBoxes = shape[1];
            int numElements = shape[2];

            Log.d(TAG, "Elements per box: " + numElements);

            float[][][] output = new float[batch][numBoxes][numElements];
            interpreter.run(inputBuffer, output);

            for (int i = 0; i < numBoxes; i++) {
                float y1 = output[0][i][0];
                float x1 = output[0][i][1];
                float y2 = output[0][i][2];
                float x2 = output[0][i][3];
                float confidence = output[0][i][4];
                int classId = (int) output[0][i][5];

                //float confidence = objConf * classProb;
                if (confidence > CONFIDENCE_THRESHOLD) {
                    float left = x1 * previewWidth;
                    float top = y1 * previewHeight;
                    float right = x2 * previewWidth;
                    float bottom = y2 * previewHeight;

                    RectF rect = new RectF(left, top, right, bottom);
                    recognitions.add(new Recognition("" + i, labels1.get(classId), confidence, rect));
                }
            }
        }
        else if (shape[1] == 300 && shape[2] == 6) { // yolov8n_float16_1.tflite
            Log.d(TAG, "Model output shape: " + Arrays.toString(shape));

            float[][][] output = new float[1][300][6];
            interpreter.run(inputBuffer, output);

            for (int i = 0; i < 300; i++) {
                float x1 = output[0][i][0];
                float y1 = output[0][i][1];
                float x2 = output[0][i][2];
                float y2 = output[0][i][3];
                float confidence = output[0][i][4];
                int classId = (int) output[0][i][5];

                if (confidence < CONFIDENCE_THRESHOLD) continue;
                if (classId != 0 && classId != 3) continue; // 只保留 person 和 motorcycle

                float left = x1 * previewWidth;
                float top = y1 * previewHeight;
                float right = x2 * previewWidth;
                float bottom = y2 * previewHeight;

                String label = classId == 0 ? "person" : "motorcycle";
                RectF rect = new RectF(left, top, right, bottom);
                if (confidence > CONFIDENCE_THRESHOLD && (classId == 0 || classId == 3) && classId < labels2.size() && labels2.get(classId) != null) {
                    recognitions.add(new Recognition("" + i, labels2.get(classId), confidence, rect));
                }
            }
        }
        else {
            Log.e(TAG, "Unsupported model output shape: " + Arrays.toString(shape));
        }
        return recognitions;
    }

    public static class Recognition {
        private final String id;
        private String title;
        private final float confidence;
        private RectF location;

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
    }
}