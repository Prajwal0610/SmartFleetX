package com.example.smartfleetx.ai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class YOLOv8Detector {

    private static final String MODEL_FILE = "yolov8n.tflite";
    private static final String LABEL_FILE = "labels.txt";
    public static final int INPUT_SIZE = 640; // public so callers can pre-scale
    private static final float CONFIDENCE_THRESHOLD = 0.5f;
    private static final float IOU_THRESHOLD = 0.45f;

    private Interpreter tflite;
    private List<String> labels = new ArrayList<>();
    private final int numClasses;
    // Pre-allocated output buffer reused across every inference call.
    // Avoids 84 LOS allocations (float[8400] arrays, ~3MB total) per detection cycle.
    private float[][][] outputBuffer;

    public static class Recognition {
        public final String id;
        public final String title;
        public final Float confidence;
        public final RectF location;
        public final int classId;

        public Recognition(String id, String title, Float confidence, RectF location, int classId) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
            this.classId = classId;
        }
    }

    public YOLOv8Detector(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        CompatibilityList compatList = new CompatibilityList();
        
        if (compatList.isDelegateSupportedOnThisDevice()) {
            try {
                GpuDelegate gpuDelegate = new GpuDelegate();
                options.addDelegate(gpuDelegate);
            } catch (Throwable t) {
                // Fallback to CPU if GPU delegate initialization fails
                android.util.Log.e("YOLOv8Detector", "Failed to initialize GPU delegate, falling back to CPU", t);
            }
        }
        
        tflite = null;
        try {
            tflite = new Interpreter(loadModelFile(context.getAssets()), options);
            labels = loadLabelList(context.getAssets());
            numClasses = labels.size();
            // Pre-allocate once; reused on every inference call to avoid per-cycle LOS GC pressure.
            outputBuffer = new float[1][4 + numClasses][8400];
        } catch (Exception e) {
            android.util.Log.e("YOLOv8Detector", "Failed to load model or labels: " + e.getMessage());
            throw new IOException("Invalid model or labels file", e);
        }
    }

    private ByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabelList(AssetManager assetManager) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(LABEL_FILE)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    public List<Recognition> detect(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);
        // YOLOv8n output shape is [1, 84, 8400] for COCO (80 classes + 4 box coords)
        tflite.run(inputBuffer, outputBuffer);
        return postProcess(outputBuffer[0]);
    }

    /**
     * Run inference on a bitmap already scaled to {@link #INPUT_SIZE}×{@link #INPUT_SIZE}.
     * Skips the internal createScaledBitmap call AND reuses the pre-allocated output buffer,
     * saving ~84 LOS array allocations (~3 MB) per detection cycle.
     */
    public List<Recognition> detectPreScaled(Bitmap bitmap) {
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(bitmap);
        tflite.run(inputBuffer, outputBuffer);
        return postProcess(outputBuffer[0]);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        
        for (int pixelValue : intValues) {
            byteBuffer.putFloat(((pixelValue >> 16) & 0xFF) / 255.0f);
            byteBuffer.putFloat(((pixelValue >> 8) & 0xFF) / 255.0f);
            byteBuffer.putFloat((pixelValue & 0xFF) / 255.0f);
        }
        return byteBuffer;
    }

    private List<Recognition> postProcess(float[][] output) {
        List<Recognition> recognitions = new ArrayList<>();
        
        for (int i = 0; i < 8400; i++) {
            float maxConf = -1.0f;
            int classId = -1;
            
            for (int j = 4; j < 4 + numClasses; j++) {
                if (output[j][i] > maxConf) {
                    maxConf = output[j][i];
                    classId = j - 4;
                }
            }
            
            if (maxConf > CONFIDENCE_THRESHOLD) {
                float cx = output[0][i];
                float cy = output[1][i];
                float w = output[2][i];
                float h = output[3][i];
                
                RectF rect = new RectF(
                    Math.max(0, cx - w / 2),
                    Math.max(0, cy - h / 2),
                    Math.min(INPUT_SIZE, cx + w / 2),
                    Math.min(INPUT_SIZE, cy + h / 2)
                );
                
                recognitions.add(new Recognition(
                    String.valueOf(i),
                    labels.get(classId),
                    maxConf,
                    rect,
                    classId
                ));
            }
        }
        
        return applyNMS(recognitions);
    }

    private List<Recognition> applyNMS(List<Recognition> recognitions) {
        List<Recognition> nmsList = new ArrayList<>();
        for (int k = 0; k < numClasses; k++) {
            List<Recognition> classRecognitions = new ArrayList<>();
            for (Recognition res : recognitions) {
                if (res.classId == k) classRecognitions.add(res);
            }
            
            // Sort by confidence
            classRecognitions.sort((a, b) -> b.confidence.compareTo(a.confidence));
            
            while (!classRecognitions.isEmpty()) {
                Recognition best = classRecognitions.get(0);
                nmsList.add(best);
                classRecognitions.remove(0);
                
                classRecognitions.removeIf(res -> calculateIoU(best.location, res.location) > IOU_THRESHOLD);
            }
        }
        return nmsList;
    }

    private float calculateIoU(RectF a, RectF b) {
        float intersectionArea = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left)) *
                                 Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        return intersectionArea / (areaA + areaB - intersectionArea);
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}
