package com.example.icnod;

import android.graphics.Bitmap;

import com.google.firebase.ml.vision.face.FirebaseVisionFace;

import java.util.List;

public interface FaceLookCallback {
    void onDetection(Bitmap originalImage, List<FirebaseVisionFace> faces);
}
