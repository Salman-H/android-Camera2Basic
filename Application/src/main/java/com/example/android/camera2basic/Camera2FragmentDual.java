package com.example.android.camera2basic;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

public class Camera2FragmentDual extends Fragment {

    // Hardcoded for now. Can be obtained from android.hardware.camera2.CameraCharacteristics
    private static final String CAM_0_ID = "0";
    private static final String CAM_1_ID = "1";

    private CameraManager mCameraManager;

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
    }
}
