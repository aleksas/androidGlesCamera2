package com.gscoder.androidglescamera2;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import com.gscoder.androidglescamera2.CameraGLSurfaceView.ScaleType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

public class CameraViewRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = CameraViewRenderer.class.getSimpleName();

    private final String cameraVertexShader = "" +
            "attribute vec2 vPosition;\n" +
            "attribute vec4 vTexCoord;\n" +
            "uniform mat4 uTexRotateMatrix;\n" +
            "varying vec2 texCoord;\n" +
            "void main() {\n" +
            "  texCoord = vTexCoord.xy;\n" +
            "  gl_Position = uTexRotateMatrix *  vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );\n" +
            "}";

    private final String cameraFragmentShader = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "varying vec2 texCoord;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture,texCoord);\n" +
            "}";

    private int[] hTex;
    private FloatBuffer pVertex;
    private FloatBuffer pTexCoord;
    private int hProgram;

    private boolean mGLInit = false;
    private boolean mUpdateSurfaceTexture = false;

    private float[] mTexRotateMatrix = new float[] {1, 0, 0, 0,   0, 1, 0, 0,   0, 0, 1, 0,   0, 0, 0, 1};

    private CameraGLSurfaceView mSurfaceView;
    private CameraHandler mCameraHandler;
    private SurfaceTexture mSurfaceTexture;
    private boolean mSyncPreviewAndImageProcess;

    private WindowManager mWindowManager;
    private OrientationEventListener mOrientationListener;

    private Size mPreviewSize = null;

    CameraViewRenderer(CameraGLSurfaceView view, CameraHandler cameraHandler) {
        mSurfaceView = view;
        mCameraHandler = cameraHandler;

        float[] vtmp = {
                -1.0f, -1.0f,   // 0 bottom left   A
                 1.0f, -1.0f,   // 1 bottom right  B
                -1.0f,  1.0f,   // 2 top left      C
                 1.0f,  1.0f,   // 3 top right     D
        };

        float[] ttmp = {
                0.0f, 0.0f,     // 0 bottom left
                1.0f, 0.0f,     // 1 bottom right
                0.0f, 1.0f,     // 2 top left
                1.0f, 1.0f      // 3 top right
        };

        pVertex = ByteBuffer.allocateDirect(vtmp.length * Float.SIZE / 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pVertex.put ( vtmp );
        pVertex.position(0);

        pTexCoord = ByteBuffer.allocateDirect(ttmp.length * Float.SIZE / 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pTexCoord.put ( ttmp );
        pTexCoord.position(0);

        Context ctx = mSurfaceView.getContext();
        if (!view.isInEditMode()) {
            mCameraHandler.calcPreviewSize(ctx);
            mPreviewSize = mCameraHandler.getPreviewSize();
        }

        mWindowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);

        if (!view.isInEditMode()) {
            mOrientationListener = new OrientationListener(ctx);
        }

        mSyncPreviewAndImageProcess = false;
    }

    public void setSyncPreviewAndImageProcess (boolean value) {
        mSyncPreviewAndImageProcess = value;
    }

    public boolean getSyncPreviewAndImageProcess () {
        return mSyncPreviewAndImageProcess;
    }

    public void onResume() {
        mCameraHandler.startBackgroundThread();

        if (mOrientationListener.canDetectOrientation() == true) {
            Log.v(TAG, "Can detect orientation");
            mOrientationListener.enable();
        } else {
            Log.v(TAG, "Cannot detect orientation");
            mOrientationListener.disable();
        }
    }

    public void onPause() {
        mGLInit = false;
        mUpdateSurfaceTexture = false;
        mCameraHandler.closeCamera();
        mCameraHandler.stopBackgroundThread();
        mOrientationListener.disable();
    }

    @Override
    public void onSurfaceCreated (GL10 unused, javax.microedition.khronos.egl.EGLConfig eglConfig ) {
        initTex();
        mSurfaceTexture = new SurfaceTexture ( hTex[0] );
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        mSurfaceTexture.setOnFrameAvailableListener(this);

        mCameraHandler.setSurfaceTexture(mSurfaceTexture);

        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f); // Clear white
        checkGlError("glClearColor");

        hProgram = loadShader (cameraVertexShader, cameraFragmentShader);

        mCameraHandler.openCamera(mSurfaceView.getContext());

        mGLInit = true;

        updateViewport();
    }

    public void onDrawFrame ( GL10 unused ) {
        if ( !mGLInit ) return;
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        checkGlError("glClear");

        synchronized(this) {
            if (mUpdateSurfaceTexture) {
                mSurfaceTexture.updateTexImage();
                mUpdateSurfaceTexture = false;

                updateViewport();
            }
        }

        GLES20.glUseProgram(hProgram);
        checkGlError("glUseProgram");

        int trmh = GLES20.glGetUniformLocation ( hProgram, "uTexRotateMatrix" );
        checkGlError("glGetUniformLocation");

        GLES20.glUniformMatrix4fv(trmh, 1, false, mTexRotateMatrix, 0);
        checkGlError("glUniformMatrix4fv");

        int ph = GLES20.glGetAttribLocation(hProgram, "vPosition");
        checkGlError("glGetAttribLocation");

        int tch = GLES20.glGetAttribLocation ( hProgram, "vTexCoord" );
        checkGlError("glGetAttribLocation");

        GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4*2, pVertex);
        checkGlError("glVertexAttribPointer");

        GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4*2, pTexCoord );
        checkGlError("glVertexAttribPointer");

        GLES20.glEnableVertexAttribArray(ph);
        checkGlError("glEnableVertexAttribArray");

        GLES20.glEnableVertexAttribArray(tch);
        checkGlError("glEnableVertexAttribArray");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        checkGlError("glActiveTexture");

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
        checkGlError("glBindTexture");

        GLES20.glUniform1i(GLES20.glGetUniformLocation ( hProgram, "sTexture" ), 0);
        checkGlError("glUniform1i");

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");

        GLES20.glFlush();
        checkGlError("glFlush");
    }

    public void onSurfaceChanged (GL10 unused, int width, int height ) {
        updateTextureRotationMatrix();
        updateViewport();
    }


    private void updateViewport() {
        updateViewport(ScaleType.CENTER_CROP);
    }

    Matrix mMatrix = new Matrix();
    RectF mSurfaceRect = new RectF();
    RectF mLastImageRect = new RectF();
    RectF mImageRect = new RectF();
    Point mRealSize = new Point();

    private void updateViewport(ScaleType scaleType) {
        boolean swap = mSurfaceView.getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        int imageWidth  = !swap ? mCameraHandler.getPreviewSize().getWidth() : mCameraHandler.getPreviewSize().getHeight();
        int imageHeight = !swap ? mCameraHandler.getPreviewSize().getHeight() : mCameraHandler.getPreviewSize().getWidth();

        mSurfaceView.getDisplay().getRealSize(mRealSize);

        mImageRect.set(0, 0, imageWidth, imageHeight);

//        mImageRect.set(0, 0, mRealSize.x, mRealSize.y);

        if (scaleType == ScaleType.CENTER_CROP) {
            float scaleImage   = (float) imageWidth / imageHeight;
            float scaleSurface = (float) mRealSize.x / mRealSize.y;

            int newTextureWidth, newTextureHeight;
            int x, y;

            if (scaleImage < scaleSurface) {
                newTextureWidth  = (int) mRealSize.x;
                newTextureHeight = (int) (mRealSize.x / scaleImage);
            }
            else {
                newTextureWidth  = (int) (mRealSize.y * scaleImage);
                newTextureHeight = (int) mRealSize.y;
            }

            x = ((int) mRealSize.x - newTextureWidth)  / 2;
            y = ((int) mRealSize.y - newTextureHeight) / 2;

            mImageRect.set(x, y, x + newTextureWidth, y + newTextureHeight);
        } else {
            Matrix.ScaleToFit scaleToFit;

            switch (scaleType) {
                case FIT_CENTER:
                    scaleToFit = Matrix.ScaleToFit.CENTER;
                    break;
                case FIT_END:
                    scaleToFit = Matrix.ScaleToFit.END;
                    break;
                case FIT_START:
                    scaleToFit = Matrix.ScaleToFit.START;
                    break;
                case FIT_XY:
                    scaleToFit = Matrix.ScaleToFit.FILL;
                    break;
                default:
                    throw new RuntimeException("Unknown ScaleType enum value.");
            }

            mSurfaceRect.set(0, 0, mRealSize.x, mRealSize.y);
            mMatrix.setRectToRect(mImageRect, mSurfaceRect, scaleToFit);
            mMatrix.mapRect(mImageRect);
        }

        if (mLastImageRect != mImageRect) {
            GLES20.glViewport((int) mImageRect.left, (int) mImageRect.top, (int) mImageRect.width(), (int) mImageRect.height());
            checkGlError("glViewport");

            mLastImageRect.set(mImageRect);
        }
    }

    private void initTex() {
        hTex = new int[1];
        GLES20.glGenTextures ( 1, hTex, 0 );
        checkGlError("glGenTextures");

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
        checkGlError("glBindTexture");

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        checkGlError("glTexParameteri");

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        checkGlError("glTexParameteri");

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        checkGlError("glTexParameteri");

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        checkGlError("glTexParameteri");
    }

    public synchronized void onFrameAvailable ( SurfaceTexture st ) {
        mUpdateSurfaceTexture = true;
        if (!mSyncPreviewAndImageProcess)
            mSurfaceView.requestRender();
    }

    private void updateTextureRotationMatrix() {

        Display display = mWindowManager.getDefaultDisplay();

        float offset = 0;
        switch (display.getRotation()) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_90:
                offset = 180;
                break;
        }

        Log.i(TAG, String.format("OFFSET: %f", offset));

        android.opengl.Matrix.setRotateM(mTexRotateMatrix, 0, offset, 0f, 0f, 1f);

        if (mSurfaceView.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            android.opengl.Matrix.setRotateM(mTexRotateMatrix, 0, -90.0f + offset, 0f, 0f, 1f);
            Log.i(TAG, String.format("rotate: 0, %f x, 0.f, 0f, 1f", 90.0f + offset));
            //Matrix.scaleM(mTexRotateMatrix, 0, mTexRotateMatrix, 0, 1, -1, 1f);
        } else {
           // Matrix.setRotateM(mTexRotateMatrix, 0, offset, 0f, 0f, 1f);
            Log.i(TAG, String.format("rotate: 0, %f x, 0.f, 0f, 1f", offset));
        }

        int facing = mCameraHandler.getFacing();

        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            //Matrix.scaleM(mTexRotateMatrix, 0, mTexRotateMatrix, 0, -1, 1, 1f);
        }
    }

    private static int loadShader ( String vss, String fss ) {
        int vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        checkGlError("glCreateShader");

        GLES20.glShaderSource(vshader, vss);
        checkGlError("glShaderSource");

        GLES20.glCompileShader(vshader);
        checkGlError("glCompileShader");

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile vertex shader");
            Log.v("Shader", "Could not compile vertex shader:"+GLES20.glGetShaderInfoLog(vshader));

            GLES20.glDeleteShader(vshader);

            checkGlError("glGetShaderiv");

            vshader = 0;
        }

        int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        checkGlError("glCreateShader");

        GLES20.glShaderSource(fshader, fss);
        checkGlError("glShaderSource");

        GLES20.glCompileShader(fshader);
        checkGlError("glCompileShader");

        GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0);

        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile fragment shader");
            Log.v("Shader", "Could not compile fragment shader:"+GLES20.glGetShaderInfoLog(fshader));

            GLES20.glDeleteShader(fshader);

            checkGlError("glGetShaderiv");

            fshader = 0;
        }

        int program = GLES20.glCreateProgram();
        checkGlError("glCreateProgram");

        GLES20.glAttachShader(program, vshader);
        checkGlError("glAttachShader");

        GLES20.glAttachShader(program, fshader);
        checkGlError("glAttachShader");

        GLES20.glLinkProgram(program);
        checkGlError("glLinkProgram");


        return program;
    }

    public static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            Log.e(TAG, msg);
            throw new RuntimeException(msg);
        }
    }

    class OrientationListener extends OrientationEventListener {
        OrientationListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_UI);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // Force orientation recheck for case when 180 screen rotatation doesn't fire onSurfaceChanged.
            updateTextureRotationMatrix();
        }
    };
}