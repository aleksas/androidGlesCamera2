package com.gscoder.androidglescamera2;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.SurfaceHolder;

public class CameraGLSurfaceView extends GLSurfaceView {
    CameraRenderer mCameraRenderer;

    CameraGLSurfaceView(Context context ) {
        super ( context );
        mCameraRenderer = new CameraRenderer(this);
        setEGLContextClientVersion ( 2 );
        setRenderer (mCameraRenderer);
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
        mCameraRenderer.onResume();
    }

    @Override
    public void onPause() {
        mCameraRenderer.onPause();
        super.onPause();
    }
}

