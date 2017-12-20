package com.gscoder.androidglescamera2;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.SurfaceHolder;

public class CameraGLSurfaceView extends GLSurfaceView {
    CameraViewRenderer mCameraViewRenderer;
    CameraHandler mCameraHandler;

    CameraGLSurfaceView(Context context) {
        super ( context );
        mCameraHandler = new CameraHandler();
        mCameraViewRenderer = new CameraViewRenderer(this, mCameraHandler);
        setEGLContextClientVersion ( 2 );
        setRenderer (mCameraViewRenderer);
        setRenderMode ( GLSurfaceView.RENDERMODE_WHEN_DIRTY );
    }

    public void surfaceCreated ( SurfaceHolder holder ) {
        super.surfaceCreated ( holder );
    }

    public void surfaceDestroyed ( SurfaceHolder holder ) {
        super.surfaceDestroyed ( holder );
    }

    public void surfaceChanged ( SurfaceHolder holder, int format, int w, int h ) {
        super.surfaceChanged ( holder, format, w, h );
    }

    @Override
    public void onResume() {
        super.onResume();
        mCameraViewRenderer.onResume();
    }

    @Override
    public void onPause() {
        mCameraViewRenderer.onPause();
        super.onPause();
    }
}

