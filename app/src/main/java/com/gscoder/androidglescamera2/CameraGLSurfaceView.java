package com.gscoder.androidglescamera2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

import java.nio.ByteBuffer;

public class CameraGLSurfaceView extends GLSurfaceView implements ImageReader.OnImageAvailableListener {
    private static final String TAG = CameraGLSurfaceView.class.getSimpleName();

    private CameraViewRenderer mCameraViewRenderer;
    private CameraHandler mCameraHandler;

    private long lastProcessingTimeMs;
    private byte[][] yuvBytes;
    private boolean computing;
    private Handler handler;
    private HandlerThread handlerThread;

    public CameraGLSurfaceView(Context context, AttributeSet attrs) {
        super ( context, attrs);

        mCameraHandler = new CameraHandler(this);
        mCameraViewRenderer = new CameraViewRenderer(this, mCameraHandler);
        mCameraViewRenderer.setSyncPreviewAndImageProcess(false);

        setEGLContextClientVersion ( 2 );
        setRenderer (mCameraViewRenderer);
        setRenderMode ( GLSurfaceView.RENDERMODE_WHEN_DIRTY );

        yuvBytes = new byte[3][];

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.CameraGLSurfaceView);
        Boolean syncPreviewAndImageProcess = arr.getBoolean(R.styleable.CameraGLSurfaceView_syncPreviewAndImageProcess, false);
        arr.recycle();

        setSyncPreviewAndImageProcess(syncPreviewAndImageProcess);
    }

    public CameraGLSurfaceView(Context context) {
        this ( context, null);
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

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void onPause() {
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, e.getMessage());
        }

        mCameraViewRenderer.onPause();
        super.onPause();
    }

    public void setSyncPreviewAndImageProcess (boolean value) {
        mCameraViewRenderer.setSyncPreviewAndImageProcess(value);
    }

    public boolean gtSyncPreviewAndImageProcess () {
        return mCameraViewRenderer.getSyncPreviewAndImageProcess();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                Log.d(TAG, String.format("Initializing buffer %d at size %d", i, buffer.capacity()));
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;

        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (computing) {
                image.close();
                return;
            }
            computing = true;

            Trace.beginSection("imageAvailable");

            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, e.getMessage());
            Trace.endSection();
            return;
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final long startTime = SystemClock.uptimeMillis();

                        // PROCESS image here
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getStackTrace() + e.getMessage(), e);
                        }

                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        requestRender();
                        computing = false;
                    }
                });

        Trace.endSection();
    }

    /**
     * Options for scaling the bounds of an image to the bounds of this view.
     */
    public enum ScaleType {
        /**
         * Scale the image using {@link Matrix.ScaleToFit#FILL}.
         */
        FIT_XY      (1),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#START}.
         */
        FIT_START   (2),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#CENTER}.
         * From XML, use this syntax:
         */
        FIT_CENTER  (3),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#END}.
         */
        FIT_END     (4),
        /**
         * Scale the image uniformly (maintain the image's aspect ratio) so
         * that both dimensions (width and height) of the image will be equal
         * to or larger than the corresponding dimension of the view
         * (minus padding). The image is then centered in the view.
         */
        CENTER_CROP (6);
        ScaleType(int ni) {
            nativeInt = ni;
        }
        final int nativeInt;
    }
}

