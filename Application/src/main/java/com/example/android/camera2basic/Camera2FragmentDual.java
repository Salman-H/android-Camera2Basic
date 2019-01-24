package com.example.android.camera2basic;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaActionSound;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class Camera2FragmentDual extends Fragment
        implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private static final int REQUEST_CAMERA_PERMISSION = 0;
    private static final String FRAGMENT_DIALOG = "dialog";

    public static final int REQUEST_SEND_IMAGE = 1;
    public static final int RESULT_PICTURE_SENT = 1;

    // Hardcoded for now. Can be obtained from android.hardware.camera2.CameraCharacteristics
    private static final String CAM_0_ID = "0";
    private static final String CAM_1_ID = "1";

    private Semaphore mCameraOpenCloseLock0 = new Semaphore(1);
    private Semaphore mCameraOpenCloseLock1 = new Semaphore(1);

    private Map<String, CameraDevice> cameraDeviceMap = new HashMap<>();
    private Map<String, CaptureRequest.Builder> cameraCaptureRequestBuilderMap = new HashMap<>();
    private Map<String, CameraCaptureSession> cameraCaptureSessionMap = new HashMap<>();

    private CameraManager mCameraManager;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private TextureView mTextureView0;
    private TextureView mTextureView1;

    private Button mStartStopCam0;
    private Button mStartStopCam1;
    private Button mCaptureButton;

    private TextClock mDatetimeView;
    private TextView mCam0StatusView;
    private TextView mCam1StatusView;

    private boolean mCam0Running;
    private boolean mCam1Running;

    private Size mPreviewSize;

    protected File imageFile;
    private boolean isInPreviewMode;
    MediaActionSound sound = new MediaActionSound();


    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new Camera2BasicFragment.ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener0 = initSurfaceTextureListener(CAM_0_ID);
    private TextureView.SurfaceTextureListener mSurfaceTextureListener1 = initSurfaceTextureListener(CAM_1_ID);

    private TextureView.SurfaceTextureListener initSurfaceTextureListener(final String camId) {
        return new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                                  int width, int height) {
                mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
                setUpCamera(camId, width, height);
                openCamera(camId);
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                configureTransform(camId, width, height);
            }
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return true; // not false
            }
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                // onSurfaceTextureUpdated()
            }
        };
    }

    private CameraDevice.StateCallback mStateCallback0 = initSateCallback(CAM_0_ID);
    private CameraDevice.StateCallback mStateCallback1 = initSateCallback(CAM_1_ID);

    private CameraDevice.StateCallback initSateCallback(final String camId) {
        final Semaphore camLock = camId.equals(CAM_0_ID)
                ? mCameraOpenCloseLock0 : mCameraOpenCloseLock1;
        return new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                camLock.release();
                cameraDeviceMap.put(camId, cameraDevice);
                if (camId.equals(CAM_0_ID)) createCameraPreviewSession(camId); // don't call cam1 on startup
            }
            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                camLock.release();
                cameraDevice.close();
                cameraDeviceMap.put(camId, null);
            }
            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int error) {
                camLock.release();
                cameraDevice.close();
                cameraDeviceMap.put(camId, null);
                Activity activity = getActivity();
                if (null != activity) {
                    activity.finish();
                }
            }
        };
    }


    public static Camera2FragmentDual newInstance() {
        return new Camera2FragmentDual();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_dual, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mDatetimeView = view.findViewById(R.id.datetimeView);
        mDatetimeView.setFormat24Hour("yyyy-MM-dd HH:mm:ss");

        mStartStopCam0 = view.findViewById(R.id.cam0ToggleButton);
        mStartStopCam0.setTag(1);
        mCam0Running = true;
        mStartStopCam0.setText("Stop");
        mStartStopCam0.setOnClickListener(this);
        mCam0StatusView = view.findViewById(R.id.cam0StatusView);
        mCam0StatusView.setText("On");

        mStartStopCam1 = view.findViewById(R.id.cam1ToggleButton);
        mStartStopCam1.setTag(0);
        mCam1Running = false;
        mStartStopCam1.setText("Start");
        mStartStopCam1.setOnClickListener(this);
        mCam1StatusView = view.findViewById(R.id.cam1StatusView);
        mCam1StatusView.setText("Off");

        mCaptureButton = view.findViewById(R.id.capture);
        mCaptureButton.setOnClickListener(this);

        mTextureView0 = (TextureView)view.findViewById(R.id.texture0);
        mTextureView1 = (TextureView)view.findViewById(R.id.texture1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(getContext(), "Couldn't access camera or save picture",
                        Toast.LENGTH_SHORT).show();
                getActivity().finish();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SEND_IMAGE) {
            if (resultCode == RESULT_PICTURE_SENT) {
                unlock();
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void setUpCamera(String cameraId, int width, int height) {
        try {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
            configureTransform(cameraId, width, height);
            return;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Camera2BasicFragment.ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    private Size chooseOptimalSize(Size[] outputSizes, int width, int height) {
        double preferredRatio = height / (double) width;
        Size currentOptimalSize = outputSizes[0];
        double currentOptimalRatio = currentOptimalSize.getWidth() / (double) currentOptimalSize.getHeight();
        for (Size currentSize : outputSizes) {
            double currentRatio = currentSize.getWidth() / (double) currentSize.getHeight();
            if (Math.abs(preferredRatio - currentRatio) <
                    Math.abs(preferredRatio - currentOptimalRatio)) {
                currentOptimalSize = currentSize;
                currentOptimalRatio = currentRatio;
            }
        }
        return currentOptimalSize;
    }

    private void configureTransform(String camId, int viewWidth, int viewHeight) {
        TextureView textureView;
        textureView = camId.equals(CAM_0_ID) ? mTextureView0 : mTextureView1;
        Activity activity = getActivity();
        if (null == textureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private void openCamera(String camId) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        try
        {
            if (camId.equals(CAM_0_ID)) {
                if (!mCameraOpenCloseLock0.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("Time out waiting to lock camera opening.");
                }
                mCameraManager.openCamera(camId, mStateCallback0, mBackgroundHandler);
            }
            else {
                if (!mCameraOpenCloseLock1.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("Time out waiting to lock camera opening.");
                }
                mCameraManager.openCamera(camId, mStateCallback1, mBackgroundHandler);
            }
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
        catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void createCameraPreviewSession(final String camId)
    {
        final CameraDevice cameraDevice = cameraDeviceMap.get(camId);
        SurfaceTexture surfaceTexture;
        try {
            surfaceTexture = camId.equals("0")
                    ? mTextureView0.getSurfaceTexture() : mTextureView1.getSurfaceTexture();
            assert surfaceTexture != null;
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            cameraCaptureRequestBuilderMap.put(camId, cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW));
            cameraCaptureRequestBuilderMap.get(camId).addTarget(previewSurface);

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSessionMap.put(camId, cameraCaptureSession); //mCaptureSession0 = cameraCaptureSession;
                            try {
                                // Finally, we start displaying the camera preview
                                cameraCaptureSessionMap.get(camId).setRepeatingRequest(
                                        cameraCaptureRequestBuilderMap.get(camId).build(),
                                        null, mBackgroundHandler); // captureCallback?
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            // onConfigureFailed()
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        if (mTextureView0.isAvailable()) {
            openCamera(CAM_0_ID);
        } else {
            mTextureView0.setSurfaceTextureListener(mSurfaceTextureListener0);
        }

        if (mTextureView1.isAvailable()) {
            openCamera(CAM_1_ID);
        } else {
            mTextureView1.setSurfaceTextureListener(mSurfaceTextureListener1);
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("camera_background_thread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    public void onStop() {
        super.onStop();
        closeCamera(CAM_0_ID);
        closeCamera(CAM_1_ID);
        stopBackgroundThread();
        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    private void closeCamera(String camId) {
        Semaphore cameraLock = camId.equals("0") ? mCameraOpenCloseLock0 : mCameraOpenCloseLock1;
        CameraCaptureSession captureSession = cameraCaptureSessionMap.get(camId);
        CameraDevice cameraDevice = cameraDeviceMap.get(camId);
        try {
            cameraLock.acquire();
            if (null != captureSession) {
                captureSession.close();
            }
            if (null != cameraDevice) {
                cameraDevice.close();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraLock.release();
        }
    }

    private void stopBackgroundThread() {
        if (mBackgroundHandler != null) {
            mBackgroundThread.quitSafely();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.cam0ToggleButton: {
                final int status = (Integer)view.getTag();
                if (status == 1) {  // cam0 already running, so only option is to stop
                    freezePreview(CAM_0_ID);  // Since preview is stopped,
                    mCam0Running = false;
                    mCam0StatusView.setText("Off");
                    mStartStopCam0.setText("Start");  // button should display start as option
                    view.setTag(0);
                }
                else {  // cam0 already stopped, so only option is to start
                    createCameraPreviewSession(CAM_0_ID);  // Since new preview session is started,
                    mCam0Running = true;
                    mCam0StatusView.setText("On");
                    mStartStopCam0.setText("Stop");  // button should display stop as option
                    view.setTag(1);
                }
                break;
            }
            case R.id.cam1ToggleButton: {
                final int status = (Integer)view.getTag();
                if (status == 1) {  // cam1 already running, so only option is to stop
                    freezePreview(CAM_1_ID);  // Since preview is stopped,
                    mCam1Running = false;
                    mCam1StatusView.setText("Off");
                    mStartStopCam1.setText("Start");  // button should display start as option
                    view.setTag(0);
                }
                else {  // cam1 already stopped, so only option is to start
                    createCameraPreviewSession(CAM_1_ID); // start new session
                    mCam1Running = true;
                    mCam1StatusView.setText("On");
                    mStartStopCam1.setText("Stop");  // button should display stop as option
                    view.setTag(1);
                }
                break;
            }
            case R.id.capture: {
                // Only capture picture from live/running camera
                if (mCam0Running && mCam1Running) {
                    captureImage(CAM_0_ID);
                    captureImage(CAM_1_ID);
                }
                else if (mCam0Running)
                    captureImage(CAM_0_ID);
                else if (mCam1Running)
                    captureImage(CAM_1_ID);

                break;
            }
        }
    }

    private void freezePreview(String camId) {
        try {
            cameraCaptureSessionMap.get(camId).stopRepeating();
        } catch (CameraAccessException cae) {
            cae.printStackTrace();
        } catch (IllegalStateException ise) {
            ise.printStackTrace();
        }
    }

    public void captureImage(String camId) {
        try {
            imageFile = createImageFile(createImageGallery());
        } catch (IOException e) {
            e.printStackTrace();
        }

        final Bitmap textureViewBitmap;

        if (camId.equals("0")) textureViewBitmap = mTextureView0.getBitmap();
        else textureViewBitmap = mTextureView1.getBitmap();

        lock(textureViewBitmap);

        mBackgroundHandler.post(new Runnable() {
            FileOutputStream outputPhoto = null;

            @Override
            public void run() {
                try {
                    outputPhoto = new FileOutputStream(imageFile);
                    textureViewBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputPhoto);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (outputPhoto != null) {
                            outputPhoto.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected File createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File imageFolder = new File(storageDirectory, getResources().getString(R.string.app_name));
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }
        return imageFolder;
    }

    protected File createImageFile(File imageFolder) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String imageFileName = "image_" + timeStamp + "_";
        return File.createTempFile(imageFileName, ".jpg", imageFolder);
    }

    private void lock(Bitmap previewImage) {
        isInPreviewMode = true;
        sound.play(MediaActionSound.SHUTTER_CLICK);
        Toast.makeText(getActivity(), "Saved", Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void unlock() {
        imageFile.delete();
        isInPreviewMode = false;
    }


}
