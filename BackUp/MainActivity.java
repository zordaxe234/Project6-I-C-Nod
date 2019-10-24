package com.example.icnod;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.media.FaceDetector;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Math.abs;

/**
 * @author Nisan Keshishyan
 * 10/20/2019
 */
@SuppressWarnings("deprecation")

public class MainActivity extends AppCompatActivity implements Camera.FaceDetectionListener {

    private Camera camera;
    final double SENSITIVITY_MIN = .05; //anything above this and below SENSITIVITY_MAX is up down
    final double SENSITIVITY_MAX = 0.2; //anything above this is left right
    FaceState faceState;
    double threshholdNoseRatio = 0.0;
    double currentNoseRatio = 0.0;

    enum FaceDirection {
        CENTER,
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    class FaceState {
        private List<FaceDirection> faceDirection = new ArrayList<>();
        private double centeredEyeRatio;

        FaceState() {
            faceDirection.add(FaceDirection.CENTER);
            centeredEyeRatio = 0.50;
        }

        public boolean checkDirectionChange(double verticalEyeRatio, double horizontalFaceEyeRatio) {
            if (centeredEyeRatio - verticalEyeRatio > SENSITIVITY_MIN) { //new eyeratio is smaller than center, facing up & not facing up already
                if (faceDirection.get(faceDirection.size() - 1) != FaceDirection.UP) {
                    faceDirection.add(FaceDirection.UP);
                    return true;
                }

            } else if (centeredEyeRatio - verticalEyeRatio < SENSITIVITY_MIN * -1 && centeredEyeRatio - verticalEyeRatio > SENSITIVITY_MAX * -1) { //new eyeratio is bigger than centered, and not facing down already
                if (faceDirection.get(faceDirection.size() - 1) != FaceDirection.DOWN) {
                    faceDirection.add(FaceDirection.DOWN);
                    return true;
                }

            } else if (centeredEyeRatio - verticalEyeRatio > SENSITIVITY_MAX) { //TODO: adding left and right changes
                faceDirection.add(FaceDirection.LEFT);
            } else if (centeredEyeRatio - verticalEyeRatio < SENSITIVITY_MAX * -1) {
                faceDirection.add(FaceDirection.RIGHT);
            } else if (faceDirection.get(faceDirection.size() - 1) != FaceDirection.CENTER) {
                faceDirection.add(FaceDirection.CENTER);
                return true;
            }
            return false;
        }

        public void calibrate() {
            centeredEyeRatio = verticalFaceEyeRatio;
            threshholdNoseRatio = currentNoseRatio;
            clearHistory();
            faceDirection.add(FaceDirection.CENTER);
            faceDetectorResults.setText("ratio " + verticalFaceEyeRatio + "\n" +
                    "we are currently facing" + faceState.getDirection());
        }

        public FaceDirection getDirection() {
            return faceDirection.get(faceDirection.size() - 1);
        }

        /**
         * @return 0 if no actions found, 1 if nod, 2 if shake
         */
        public int detectAction() {
            if (faceDirection.size() < 4) return 0; //no action could happen if not enough history

            if (faceDirection.get(faceDirection.size() - 4) == FaceDirection.UP &&
                    faceDirection.get(faceDirection.size() - 3) == FaceDirection.CENTER &&
                    faceDirection.get(faceDirection.size() - 2) == FaceDirection.DOWN &&
                    faceDirection.get(faceDirection.size() - 1) == FaceDirection.CENTER) { //A nod
                clearHistory();
                return 1; //nod detected.
            } else if (faceDirection.get(faceDirection.size() - 4) == FaceDirection.LEFT &&
                    faceDirection.get(faceDirection.size() - 3) == FaceDirection.CENTER &&
                    faceDirection.get(faceDirection.size() - 2) == FaceDirection.RIGHT &&
                    faceDirection.get(faceDirection.size() - 1) == FaceDirection.CENTER) { //A shake starting to the left
                clearHistory();
                return 2;
            } else if (faceDirection.get(faceDirection.size() - 4) == FaceDirection.RIGHT &&
                    faceDirection.get(faceDirection.size() - 3) == FaceDirection.CENTER &&
                    faceDirection.get(faceDirection.size() - 2) == FaceDirection.LEFT &&
                    faceDirection.get(faceDirection.size() - 1) == FaceDirection.CENTER) { //A shake starting to the right
                clearHistory();
                return 2;
            }
            return 0;
        }

        public void clearHistory() {
            faceDirection.clear();
            faceDirection.add(FaceDirection.CENTER);
        }
    }


    TextView faceDetectorResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);

        // Disable the title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        // Enable fullscreen & remove the notifications bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Setup the view AFTER changing title/notification bar properties
        setContentView(R.layout.activity_main);

        // Request permissions if not already given
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        }

        faceDetectorResults = findViewById(R.id.face_detector_results);

        // If camera is present, take picture
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            faceState = new FaceState();
            setupCamera();
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

    private static void logToConsole(String message) {
        //System.out.println("[Nisan] " + message);
        Log.i("[Nisan] ", message);
    }

    /**
     * Take a picture button
     */
    private void createCalibrateButton() {
        Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                faceState.calibrate();
            }
        });
        Button button2 = findViewById(R.id.hamoodButton);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });
    }

    FirebaseVisionFaceDetector detector;
    /**
     * Takes a picture
     */
    private void takePicture() {
        camera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] bytes, final Camera camera) {
                camera.startPreview();
                final Bitmap bitmap = flipImage(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));



            }
        });
    }

    // =========================================================================
    // Detection Related Code
    // =========================================================================
    Camera.Face face;
    double verticalFaceEyeRatio;
    double horizontalFaceEyeRatio;


    /**
     * FaceDetector.Face, API level 1
     */
    public void onFaceDetection(FaceDetector.Face[] facesArray) {
        if (face == null)
            return; //possible that FaceDetector runs before Camera.face, this safeguards the race.
        FaceDetector.Face fdFace = facesArray[0];


        if (face.score < 50) {
            faceDetectorResults.setText("No Face Detected");
            return;
        }

        //we're using FaceDetector.Face because it gives us eyes midpoint
        PointF midpointOfEyes = new PointF();
        fdFace.getMidPoint(midpointOfEyes);

        //using Camera.Face because it gives us bounding box & face height
        Rect faceBounds = face.rect;

        TextView cameraFaceResults = findViewById(R.id.camera_face_results);

        float eyesFromFace = midpointOfEyes.y + faceBounds.top; //faceBounds.top is a negative value for some reason, so getting difference between eyes y value on image and top of the face to get position of eyes in relation to the face.

        float eyesFromFaceHorizontal = midpointOfEyes.x + faceBounds.left;

        verticalFaceEyeRatio = eyesFromFace / (faceBounds.top * -1);
        horizontalFaceEyeRatio = eyesFromFaceHorizontal / faceBounds.left * -1;

        if (faceState.checkDirectionChange(verticalFaceEyeRatio, horizontalFaceEyeRatio)) {
            switch (faceState.detectAction()) {
                case 0:
                    break;
                case 1:
                    Toast.makeText(this, "Nod Detected!", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    Toast.makeText(this, "Shake Detected!", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
        faceDetectorResults.setText("ratio " + verticalFaceEyeRatio + "\n" +
                "Horizontal Ratio: " + horizontalFaceEyeRatio + "\n" +
                "we are currently facing" + faceState.getDirection());
    }


    /**
     * Camera.Face detection, API level 21
     */
    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
        for (Camera.Face face : faces) {
            this.face = face; // keeping this a global variable since there is a frequency difference in camera.face and FaceDetector.face. will use it when FaceDetector.face runs.
        }
    }

    // ===================================================================== //
    // CAMERA RELATED CODE                                                   //
    // See: https://developer.android.com/guide/topics/media/camera#manifest //
    // ===================================================================== //

    /**
     * Create camera and add to the view
     */
    private void setupCamera() {
        try {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            Camera.Parameters parameters = camera.getParameters();
            setRotation(camera, parameters, 1);

            if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }

            camera.setParameters(parameters);
        } catch (Exception e) {
            logToConsole("Camera not available (" + e.getMessage() + ")");
            return;
        }

        camera.setFaceDetectionListener(this);

        CameraPreview preview = new CameraPreview(this, camera);
        ((FrameLayout) findViewById(R.id.layout)).addView(preview);
    }

    /**
     * Fixes rotation issues when images are taken using Samsung phones.
     * <p>
     * See: https://github.com/google/cameraview/issues/22#issuecomment-383416821
     */
    private static int fixOrientation(Bitmap bitmap) {
        if (bitmap.getWidth() > bitmap.getHeight()) {
            return 90;
        }
        return 0;
    }

    /**
     * Fixes rotation issues when images are taken using Samsung phones.
     * <p>
     * See: https://github.com/google/cameraview/issues/22#issuecomment-383416821
     */
    public static Bitmap flipImage(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        int rotation = fixOrientation(bitmap);
        matrix.postRotate(rotation);
        matrix.preScale(-1, 1);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * Calculates the correct rotation for the given camera id and sets the rotation in the
     * parameters. It also sets the camera's display orientation and rotation.
     *
     * @param parameters the camera parameters for which to set the rotation
     * @param cameraId   the camera id to set rotation based on
     */
    @SuppressWarnings("SameParameterValue")
    private void setRotation(Camera camera, Camera.Parameters parameters, int cameraId) {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;
        int degrees = 0;
        int rotation = windowManager.getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                logToConsole("Bad rotation value: " + rotation);
        }

        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, cameraInfo);

        int angle;
        int displayAngle;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            angle = (cameraInfo.orientation + degrees) % 360;
            displayAngle = (360 - angle) % 360; // compensate for it being mirrored
        } else { // back-facing
            angle = (cameraInfo.orientation - degrees + 360) % 360;
            displayAngle = angle;
        }

        camera.setDisplayOrientation(displayAngle);
        parameters.setRotation(angle);
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

    /**
     * Finds the faces in a given preview frame or picture
     */
    private void findFaces(byte[] bytes) {
        final Bitmap bitmap = flipImage(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        final Bitmap rgb565 = bitmap.copy(Bitmap.Config.RGB_565, false);

        int maxNumberOfFaces = 1;
        final FaceDetector.Face[] facesArray = new FaceDetector.Face[maxNumberOfFaces];

        final FaceDetector faceDetector = new FaceDetector(rgb565.getWidth(), rgb565.getHeight(), maxNumberOfFaces);
        faceDetector.findFaces(rgb565, facesArray);

        if (facesArray[0] == null) return;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onFaceDetection(facesArray);
            }
        });
    }

    /**
     * A basic Camera preview class
     * <p>
     * Source: https://developer.android.com/guide/topics/media/camera#manifest
     */
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
        private SurfaceHolder holder;
        private Camera camera;
        private int frames = 0;
        private ExecutorService service = Executors.newCachedThreadPool();

        public CameraPreview(Context context, Camera camera) {
            super(context);
            this.camera = camera;

            holder = getHolder();
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void restartCamera() {
            try {
                camera.stopPreview();
            } catch (Exception e) {
                // ignore: tried to stop a non-existent preview
            } finally {
                try {
                    camera.setPreviewDisplay(this.holder);
                    camera.setPreviewCallback(this);
                    camera.startPreview();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                FrameLayout frameLayout = MainActivity.this.findViewById(R.id.layout);

                Camera.Parameters parameters = camera.getParameters();
                Camera.Size mPreviewSize = getOptimalPreviewSize(parameters.getSupportedPreviewSizes(), frameLayout.getWidth(), frameLayout.getHeight());
                parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
                parameters.setPreviewFormat(ImageFormat.NV21);

                Camera.Size size = camera.getParameters().getPreviewSize();
                logToConsole("Preview Size= " + mPreviewSize.width + "x" + mPreviewSize.height);
                logToConsole("Optimal Size= " + size.width + "x" + size.height);
                camera.setParameters(parameters);

                camera.setPreviewDisplay(this.holder);
                camera.setPreviewCallback(this);
                camera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.
            if (this.holder.getSurface() == null) {
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                camera.stopPreview();
            } catch (Exception e) {
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here

            // start preview with new settings
            try {
                camera.setPreviewDisplay(this.holder);
                camera.setPreviewCallback(this);
                camera.startPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Take preview frames and find faces in the images
         */
        @Override
        public void onPreviewFrame(final byte[] data, Camera camera) {
            if (++frames % 10 == 0) {
                byte[] bytes = convertPreviewFrame(data);
                Bitmap bitmap = flipImage(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
                detector.detectInImage(image)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<FirebaseVisionFace>>() {
                                    @Override
                                    public void onSuccess(List<FirebaseVisionFace> faces) {
                                        for (FirebaseVisionFace face : faces){
                                            face.getBoundingBox().height();

                                            face.getHeadEulerAngleZ();
                                            FirebaseVisionFaceLandmark nose = face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE);
                                            if ( face.getHeadEulerAngleY() < -16){
                                                Toast.makeText(MainActivity.this, "Left", Toast.LENGTH_SHORT).show();
                                            }else if (face.getHeadEulerAngleY() > 16){
                                                Toast.makeText(MainActivity.this, "Right", Toast.LENGTH_SHORT).show();
                                            }else{
                                                if (nose == null) return;
                                                currentNoseRatio = face.getBoundingBox().height()/ nose.getPosition().getY();

                                                double difference = (currentNoseRatio - threshholdNoseRatio) / threshholdNoseRatio;

                                                if (difference > 0.1){ //looking up
                                                    Toast.makeText(MainActivity.this, "Up", Toast.LENGTH_SHORT).show();
                                                }else if (difference < -0.1) { //looking down
                                                    Toast.makeText(MainActivity.this, "Down", Toast.LENGTH_SHORT).show();
                                                }else {
                                                    Toast.makeText(MainActivity.this, "Center", Toast.LENGTH_SHORT).show();
                                                }

                                            }
                                        }

                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                    }
                                });
//                service.execute(new Runnable() {
//                    @Override
//                    public void run() {
//                        //findFaces(convertPreviewFrame(data));
//
//                    }
//                });
            }

//            if (service instanceof ThreadPoolExecutor) {
//                logToConsole("Pool size is now " + ((ThreadPoolExecutor) service).getActiveCount());
//            }
        }

        /**
         * Converts a preview frame format to the proper image format
         */
        private byte[] convertPreviewFrame(byte[] data) {
            Camera.Parameters parameters = camera.getParameters();
            int width = parameters.getPreviewSize().width;
            int height = parameters.getPreviewSize().height;

            YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), width, height, null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);

            return out.toByteArray();
        }

    }

}