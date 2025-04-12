package com.example.lbsdemo.media;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.util.Log;

import com.example.lbsdemo.R;

import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
//拍照的实现：
public class PhotoActivity extends AppCompatActivity {

    private static final String TAG = "PhotoActivity";

    TextureView mPreviewview;
    ImageView capturedImageView;
    HandlerThread mHandlerThread;
    Handler mCameraHandler;
    CameraManager manager;
    Size mPreviewSize;
    Size mCaptureSize;
    String mCameraId;
    CameraDevice mCameraDevice;
    CaptureRequest.Builder mCaptureRequestBuilder;
    CaptureRequest mCaptureRequest;
    CameraCaptureSession mCameraCaptureSession;
    ImageReader mimageReader;

    private static final SparseArray<Integer> ORIENTATION = new SparseArray<>();
    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);
        mPreviewview = findViewById(R.id.textureView);
        capturedImageView = findViewById(R.id.capturedImageView); // 初始化 ImageView
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        startCameraThread();
        if (mPreviewview.isAvailable()) {
            Log.d(TAG, "TextureView already available, setting up camera.");
            try {
                setupCamera(mPreviewview.getWidth(), mPreviewview.getHeight());
                openCamera();
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error setting up camera onResume", e);
            }
        } else {
            Log.d(TAG, "TextureView not available, setting listener.");
            mPreviewview.setSurfaceTextureListener(mTextureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause called");
        closeCamera();
        stopCameraThread();
        super.onPause();
    }

    TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable: width=" + width + ", height=" + height);
            try {
                setupCamera(width, height);
                openCamera();
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error setting up camera onSurfaceTextureAvailable", e);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) { }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) { }
    };

    private void startCameraThread() {
        mHandlerThread = new HandlerThread("CameraThread");
        mHandlerThread.start();
        mCameraHandler = new Handler(mHandlerThread.getLooper());
    }

    private void setupCamera(int width, int height) throws CameraAccessException {
        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        for (String cameraID : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                continue;
            }

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                mCaptureSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
                    @Override
                    public int compare(Size o1, Size o2) {
                        return Long.signum(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                    }
                });
            }
            setupImageReader();
            mCameraId = cameraID;
            break;
        }
    }

    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 1) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size o1, Size o2) {
                    return Long.signum(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                }
            });
        }
        return sizeMap[0];
    }

    private void openCamera() {
        Log.d(TAG, "Attempting to open camera...");
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted when trying to open camera.");
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            return;
        }

        try {
            if (manager == null) {
                Log.e(TAG, "CameraManager is null, cannot open camera.");
                return;
            }
            if (mCameraId == null) {
                Log.e(TAG, "mCameraId is null, cannot open camera. Setup might have failed.");
                if(mPreviewview.isAvailable()) {
                    setupCamera(mPreviewview.getWidth(), mPreviewview.getHeight());
                    if(mCameraId == null) {
                        Log.e(TAG, "Still no camera ID after re-setup attempt.");
                        Toast.makeText(this, "无法找到相机设备", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else {
                    Log.e(TAG, "TextureView not available for camera re-setup.");
                    return;
                }
            }
            Log.d(TAG, "Opening camera: " + mCameraId);
            manager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException opening camera: " + mCameraId, e);
            runOnUiThread(() -> Toast.makeText(this, "无法访问相机", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Log.e(TAG, "Exception opening camera: " + mCameraId, e);
            runOnUiThread(() -> Toast.makeText(this, "打开相机时出错", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult: requestCode=" + requestCode);
        if (requestCode == 1) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Log.d(TAG, "All permissions granted. Opening camera.");
                if (mPreviewview.isAvailable()) {
                    openCamera();
                } else {
                    Log.w(TAG, "Permissions granted, but TextureView not available yet.");
                }
            } else {
                Log.w(TAG, "Permissions not granted.");
                Toast.makeText(this, "需要相机和存储权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        }
    }

    CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "CameraDevice.onOpened");
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.w(TAG, "CameraDevice.onDisconnected");
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "CameraDevice.onError: " + error);
            runOnUiThread(() -> Toast.makeText(PhotoActivity.this, "相机错误: " + error, Toast.LENGTH_LONG).show());
            closeCamera();
        }
    };

    private void startPreview() {
        Log.d(TAG, "startPreview called");
        if (mCameraDevice == null || !mPreviewview.isAvailable() || mPreviewSize == null) {
            Log.e(TAG, "startPreview preconditions not met: mCameraDevice=" + mCameraDevice +
                  ", isAvailable=" + mPreviewview.isAvailable() + ", mPreviewSize=" + mPreviewSize);
            return;
        }

        SurfaceTexture mSurfaceTexture = mPreviewview.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(mSurfaceTexture);
        try {
            Log.d(TAG, "Creating capture request builder for preview.");
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            Log.d(TAG, "Creating capture session.");
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mimageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "CameraCaptureSession.onConfigured");
                    if (mCameraDevice == null) return;

                    mCameraCaptureSession = session;
                    try {
                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        mCaptureRequest = mCaptureRequestBuilder.build();
                        Log.d(TAG, "Setting repeating request for preview.");
                        mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mCameraHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "CameraAccessException setting repeating request", e);
                        runOnUiThread(()->Toast.makeText(PhotoActivity.this, "启动预览失败", Toast.LENGTH_SHORT).show());
                    } catch (IllegalStateException e){
                        Log.e(TAG, "IllegalStateException setting repeating request", e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "CameraCaptureSession.onConfigureFailed");
                    runOnUiThread(()->Toast.makeText(PhotoActivity.this, "相机配置失败", Toast.LENGTH_SHORT).show());
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException creating capture session", e);
            runOnUiThread(()->Toast.makeText(PhotoActivity.this, "创建相机捕捉会话失败", Toast.LENGTH_SHORT).show());
        }
    }

    public void Capture(View view) throws CameraAccessException {
        CaptureRequest.Builder mCameraBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        mCameraBuilder.addTarget(mimageReader.getSurface());
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        mCameraBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
        CameraCaptureSession.CaptureCallback mCaptureCallBack = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                           @NonNull CaptureRequest request,
                                           @NonNull TotalCaptureResult result) {
                Toast.makeText(getApplicationContext(), "拍照结束，相片已保存", Toast.LENGTH_LONG).show();
                unLockFouces();
                super.onCaptureCompleted(session, request, result);
            }
        };

        mCameraCaptureSession.stopRepeating();
        mCameraCaptureSession.capture(mCameraBuilder.build(), mCaptureCallBack, mCameraHandler);
    }

    private void unLockFouces() {
        try {
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mCameraHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupImageReader() {
        mimageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(), ImageFormat.JPEG, 2);
        mimageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                mCameraHandler.post(new ImageSaver(reader.acquireLatestImage(), PhotoActivity.this));
            }
        }, mCameraHandler);
    }

    private static class ImageSaver implements Runnable {
        private Image mImage;
        private Context mContext;

        public ImageSaver(Image image, Context context) {
            mImage = image;
            mContext = context;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            mImage.close();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            Uri imageUri = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_" + timeStamp + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CameraV2/");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri uri = mContext.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = mContext.getContentResolver().openOutputStream(uri)) {
                        out.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    mContext.getContentResolver().update(uri, values, null, null);
                    imageUri = uri;
                }

            } else {
                File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                File cameraDir = new File(dcimDir, "CameraV2");
                if (!cameraDir.exists()) {
                    cameraDir.mkdirs();
                }
                File imageFile = new File(cameraDir, "IMG_" + timeStamp + ".jpg");
                try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                    fos.write(data, 0, data.length);
                    fos.flush();
                    imageUri = Uri.fromFile(imageFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (imageUri != null) {
                final Uri finalImageUri = imageUri;
                ((PhotoActivity) mContext).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PhotoActivity activity = (PhotoActivity) mContext;
                        activity.capturedImageView.setImageURI(finalImageUri);
                        activity.capturedImageView.setVisibility(View.VISIBLE);
                        activity.findViewById(R.id.capturedCardView).setVisibility(View.VISIBLE);
                    }
                });
            }
        }

    }

    private void closeCamera() {
        Log.d(TAG, "closeCamera called");
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mimageReader != null) {
            mimageReader.close();
            mimageReader = null;
        }
        Log.d(TAG, "Camera resources closed.");
    }

    private void stopCameraThread() {
        Log.d(TAG, "stopCameraThread called");
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join();
                mHandlerThread = null;
                mCameraHandler = null;
                Log.d(TAG, "Camera thread stopped.");
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping camera thread", e);
            }
        }
    }
}
