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
        startCameraThread();

        if (!mPreviewview.isActivated()) {
            mPreviewview.setSurfaceTextureListener(mTextureListener);
        } else {
            startPreview();
        }
    }

    TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            try {
                setupCamera(width, height);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
            openCamera();
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
        // 动态权限请求
        String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        List<String> requestList = new ArrayList<>();
        for (String p : permissions) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                requestList.add(p);
            }
        }
        if (!requestList.isEmpty()) {
            requestPermissions(requestList.toArray(new String[0]), 1);
            return;
        }

        try {
            manager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    };

    private void startPreview() {
        SurfaceTexture mSurfaceTexture = mPreviewview.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(mSurfaceTexture);
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mimageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCaptureRequest = mCaptureRequestBuilder.build();
                    mCameraCaptureSession = session;
                    try {
                        mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mCameraHandler);
                    } catch (CameraAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
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
                // Android 10及以上使用MediaStore
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
                // Android 9及以下使用传统方式
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
                // 更新 UI 显示拍摄的照片
                final Uri finalImageUri = imageUri;
                ((PhotoActivity) mContext).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PhotoActivity activity = (PhotoActivity) mContext;
                        activity.capturedImageView.setImageURI(finalImageUri);
                        activity.capturedImageView.setVisibility(View.VISIBLE);
                        activity.findViewById(R.id.capturedCardView).setVisibility(View.VISIBLE); // 显示 CardView
                    }
                });
            }
        }

    }
}
