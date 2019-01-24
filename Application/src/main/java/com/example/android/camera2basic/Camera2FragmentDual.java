package com.example.android.camera2basic;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;


public class Camera2FragmentDual extends Fragment
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final int REQUEST_CAMERA_PERMISSION = 0;
    private static final String FRAGMENT_DIALOG = "dialog";

    // Hardcoded for now. Can be obtained from android.hardware.camera2.CameraCharacteristics
    private static final String CAM_0_ID = "0";
    private static final String CAM_1_ID = "1";

    private Semaphore mCameraOpenCloseLock0 = new Semaphore(1);
    private Semaphore mCameraOpenCloseLock1 = new Semaphore(1);

    private Map<String, CameraDevice> cameraDeviceMap = new HashMap<>();

    private CameraManager mCameraManager;

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
                openCamera(camId ,width, height);
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

    private CameraDevice.StateCallback stateCallback0 = initSateCallback(CAM_0_ID);
    private CameraDevice.StateCallback stateCallback1 = initSateCallback(CAM_1_ID);

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

}
