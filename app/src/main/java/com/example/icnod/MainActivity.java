package com.example.icnod;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.icnod.firebase.CameraSource;
import com.example.icnod.firebase.FaceDetectionProcessor;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;

import java.io.IOException;
import java.util.List;

import static java.lang.Math.abs;

/**
 * @author Nisan Keshishyan
 * 10/20/2019
 */
@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements FaceLookCallback {

    public static final double ROTATIONALLIMIT = 0.1;
    FirebaseVisionFaceDetector detector;
    TextView textView;
    private FaceState faceState;
    private double threshholdNoseRatio = 0.05;
    private double currentNoseRatio = 0.0;
    private CameraSource cameraSource;
    private CameraPreview cameraPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);

        // Disable the title bar
        getSupportActionBar().hide();
        // Enable fullscreen & remove the notifications bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Setup the view AFTER changing title/notification bar properties
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.faceState);
        // Request permissions if not already given
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        }

        // If camera is present, take picture
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            faceState = new FaceState();
            //setupCamera();
            cameraSource = new CameraSource(this);
            cameraSource.setMachineLearningFrameProcessor(new FaceDetectionProcessor(this));
            cameraPreview = new CameraPreview(this);
            ((LinearLayout) findViewById(R.id.linearLayout)).addView(cameraPreview);
            createCalibrateButton();
        }

        FirebaseVisionFaceDetectorOptions highAccuracyOpts =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                        .build();
        detector = FirebaseVision.getInstance().getVisionFaceDetector(highAccuracyOpts);
    }

    @Override
    public void onResume() {
        super.onResume();
        startCameraSource();
    }

    public CameraPreview getCameraPreview() {
        return cameraPreview;
    }

    private void startCameraSource() {
        if (cameraSource != null) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            try {
                cameraSource.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        cameraSource.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

    /**
     * Take a picture button
     */
    private void createCalibrateButton() {
        Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                threshholdNoseRatio = currentNoseRatio;
            }
        });
    }


    /**
     * Source: https://stackoverflow.com/questions/19577299/android-camera-preview-stretched
     */
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int targetWidth, int targetHeight) {
        final double ASPECT_TOLERANCE = 0.5;
        double targetRatio = (double) targetHeight / targetWidth;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onDetection(Bitmap originalImage, List<FirebaseVisionFace> faces) {
        for (FirebaseVisionFace face : faces) {
            face.getHeadEulerAngleZ();
            FirebaseVisionFaceLandmark nose = face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE);

            if (face.getHeadEulerAngleY() < -30) {
                textView.setText("Right");
                faceState.addFaceDirection(FaceDirection.RIGHT, MainActivity.this);
            } else if (face.getHeadEulerAngleY() > 30) {
                textView.setText("Left");
                faceState.addFaceDirection(FaceDirection.LEFT, MainActivity.this);
            } else {
                if (nose == null) return; //didnt initialize all values yet
                currentNoseRatio = face.getBoundingBox().height() / nose.getPosition().getY();

                double difference = (currentNoseRatio - threshholdNoseRatio) / threshholdNoseRatio;


                if (difference > ROTATIONALLIMIT) {
                    textView.setText("Up");
                    faceState.addFaceDirection(FaceDirection.UP, MainActivity.this);
                } else if (difference < -ROTATIONALLIMIT) { //looking down
                    textView.setText("Down");
                    faceState.addFaceDirection(FaceDirection.DOWN, MainActivity.this);
                } else {
                    textView.setText("Center");
                    faceState.addFaceDirection(FaceDirection.CENTER, MainActivity.this);
                }

            }
        }
    }

    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder holder;

        private int canvasWidth;
        private int canvasHeight;


        public CameraPreview(Context context) {
            super(context);

            holder = getHolder();
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            canvasWidth = size.x;
            canvasHeight = size.y;
        }

        public void drawBitmap(Bitmap bitmap) {
            Canvas canvas = holder.lockCanvas();
            canvas.drawBitmap(bitmap, null, new RectF(0, 0, canvasWidth, canvasHeight), null);
            holder.unlockCanvasAndPost(canvas);
        }

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {

        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }
    }
}