package com.example.cameratwoapplication;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.Arrays;

public class Camera2VideoHelper {
    private static final String TAG = "Camera2VideoImage";
    private final Context context;
    private final TextureView textureView;
    private String cameraId;
    private CameraCharacteristics mCharacteristics = null;
    private CameraDevice cameraDevice;
    private Size videoSize;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ImageReader imageReader;
    private long lastFrameTime;
    private boolean isImageReaderSetup = false;
    private MainActivity mainActivity;

    public Camera2VideoHelper(Context context, TextureView textureView) {
        this.context = context;
        this.textureView = textureView;
    }

    public void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            Camera2VideoHelper.this.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            Camera2VideoHelper.this.cameraDevice = null;
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(videoSize.getWidth(), videoSize.getHeight());
            Surface surface = new Surface(texture);
            Surface surface2 = imageReader.getSurface();
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.addTarget(surface2);
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, surface2),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (cameraDevice == null) {
                                return;
                            }
                            Camera2VideoHelper.this.cameraCaptureSession = cameraCaptureSession;
//                            updatePreview();
                            setup3AControlsLocked(captureRequestBuilder);
                            startCaptureRequest(captureRequestBuilder);
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "onConfigureFailed: Failed");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setup3AControlsLocked(CaptureRequest.Builder builder) {
        // Enable auto-magical 3A run by camera device
        builder.set(CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO);

        builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON);
        // Allow AWB to run auto-magically if this device supports this
        builder.set(CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO);
    }

    protected void openCamera() {
        // Make sure ImageReader is set up before opening the camera
        if (!isImageReaderSetup) {
            setupImageReader();
            isImageReaderSetup = true;
        }

        setUpCameraOutputs();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (android.content.pm.PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)) {
                return;
            }

            if (cameraId != null) {
                // Use backgroundHandler to open the camera in a background thread
                manager.openCamera(cameraId, stateCallback, backgroundHandler);
            } else {
                Toast.makeText(context, "Camera not found..", Toast.LENGTH_SHORT).show();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCameraOutputs() {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.e(TAG, "Total Camera Found : " + manager.getCameraIdList().length);
            for (String cameraId : manager.getCameraIdList()) {
                Log.e(TAG, "Camera ID : " + cameraId);
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
//                    continue;
//                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
                this.cameraId = cameraId;
                this.mCharacteristics = characteristics;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        return choices[choices.length - 1];
    }

    private CameraCaptureSession.CaptureCallback mPreCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };

    private void updatePreview() {
        if (cameraDevice == null) {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), mPreCaptureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void startCaptureRequest(CaptureRequest.Builder captureRequestBuilder) {
        try {
            // Continuously repeat the capture request
            cameraCaptureSession.setRepeatingRequest(
                    captureRequestBuilder.build(), null,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /*               new CameraCaptureSession.CaptureCallback() {
       @Override
       public void onCaptureCompleted(@NonNull CameraCaptureSession session,
               @NonNull CaptureRequest request,
               @NonNull TotalCaptureResult result) {
           super.onCaptureCompleted(session, request, result);

//                            // Calculate FPS
//                            long currentTime = System.nanoTime();
//                            if (lastFrameTime != 0) {
//                                long frameTimeDiff = currentTime - lastFrameTime;
//                                double fps = 1e9 / frameTimeDiff ; // 1e9 nanoseconds in a second
//                                Log.d(TAG, "FPS: " + fps + " diff:-" + frameTimeDiff/1000);
//                            }
//                            lastFrameTime = currentTime;
       }
   }*/
    void setupImageReader() {
        imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            Log.i(TAG, image.getWidth() + " " + image.getHeight() + " "+image.getFormat() + " "+ image.getPlanes().length);
            // Calculate FPS
            long currentTime = System.nanoTime();
            if (lastFrameTime != 0) {
                long frameTimeDiff = currentTime - lastFrameTime;
                double fps = 1e9 / frameTimeDiff; // 1e9 nanoseconds in a second
//                String fps1
//                mainActivity.textView.setText((int) fps);
                Log.e(TAG, "FPS: " + fps + " diff:-" + frameTimeDiff / 1000);
            }
            lastFrameTime = currentTime;
            // Process the image data if needed
            image.close();
        }, backgroundHandler);
    }


    public TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
        }
    };
}
