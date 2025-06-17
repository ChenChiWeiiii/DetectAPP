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
import java.util.List;

public class DetectorPerson {
    private static final String TAG = "DetectorPerson";
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.5f;

    private final Interpreter interpreter;
    private final List<String> labels;

    public DetectorPerson(AssetManager assetManager, String modelName) throws IOException {
        MappedByteBuffer modelBuffer = loadModelFile(assetManager, modelName);
        interpreter = new Interpreter(modelBuffer);

        labels = new ArrayList<>();
        labels.add("person");
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

    public List<Recognition> detect(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer inputBuffer = bitmapToFloatBuffer(resized);

        int[] shape = interpreter.getOutputTensor(0).shape();
        int B = shape[0], C = shape[1], N = shape[2];
        float[][][] output = new float[B][C][N];

        interpreter.run(inputBuffer, output);

        List<Recognition> recognitions = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            float[] data = new float[C];
            for (int c = 0; c < C; c++) {
                data[c] = output[0][c][i];
            }
            float confidence = data[4];
            if (confidence > CONFIDENCE_THRESHOLD) {
                int classId = (int) data[5];
                float cx = data[0], cy = data[1], w = data[2], h = data[3];

                float left = cx - w / 2;
                float top = cy - h / 2;
                float right = cx + w / 2;
                float bottom = cy + h / 2;

                RectF rect = new RectF(left, top, right, bottom);

                recognitions.add(
                        new Recognition("" + i, labels.get(classId), confidence, rect)
                );
            }
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