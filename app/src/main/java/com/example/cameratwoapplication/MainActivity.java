package com.example.cameratwoapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private TextureView textureView;
    private Camera2VideoHelper camera2VideoHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
            // Move setupImageReader here after startCamera()
            camera2VideoHelper.setupImageReader();
        }
    }

    private void startCamera() {
        camera2VideoHelper = new Camera2VideoHelper(this, textureView);
        camera2VideoHelper.startBackgroundThread();
        if (textureView.isAvailable()) {
            camera2VideoHelper.openCamera();
        } else {
            textureView.setSurfaceTextureListener(camera2VideoHelper.surfaceTextureListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera2VideoHelper != null) {
            startCamera();
            // Move setupImageReader here after startCamera()
            camera2VideoHelper.setupImageReader();
        }
    }

    @Override
    protected void onPause() {
        if (camera2VideoHelper != null) {
            camera2VideoHelper.closeCamera();
            camera2VideoHelper.stopBackgroundThread();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (camera2VideoHelper != null) {
            camera2VideoHelper.closeCamera();
            camera2VideoHelper.stopBackgroundThread();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
            // Move setupImageReader here after startCamera()
            camera2VideoHelper.setupImageReader();
        } else {
            Toast.makeText(this, "Camera permissions are required", Toast.LENGTH_SHORT).show();
        }
    }
}
