package com.gscoder.androidglescamera2;

import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

public class CameraViewRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = CameraViewRenderer.class.getSimpleName();

    private final String cameraVertexShader = "" +
            "attribute vec2 vPosition;\n" +
            "attribute vec2 vTexCoord;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "varying vec2 texCoord;\n" +
            "void main() {\n" +
            "  texCoord = vTexCoord;\n" +
            "  gl_Position = vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );\n" +
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

    private float[] mTexMatrix = new float[] {1, 0, 0, 0,   0, 1, 0, 0,   0, 0, 1, 0,   0, 0, 0, 1};

    CameraGLSurfaceView mView;
    CameraHandler mCameraHandler;
    SurfaceTexture mSurfaceTexture;

    CameraViewRenderer(CameraGLSurfaceView view, CameraHandler cameraHandler) {
        mView = view;
        mCameraHandler = cameraHandler;

        float[] vtmp = {
                1.0f, -1.0f,
                -1.0f,-1.0f,
                1.0f, 1.0f,
                -1.0f, 1.0f
        };

        float[] ttmp = {
                1.0f, 1.0f,
                0.0f, 1.0f,
                1.0f, 0.0f,
                0.0f, 0.0f
        };

        pVertex = ByteBuffer.allocateDirect(vtmp.length * Float.SIZE / 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pVertex.put ( vtmp );
        pVertex.position(0);

        pTexCoord = ByteBuffer.allocateDirect(ttmp.length * Float.SIZE / 8).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pTexCoord.put ( ttmp );
        pTexCoord.position(0);
    }

    public void onResume() {
        mCameraHandler.startBackgroundThread();
    }

    public void onPause() {
        mGLInit = false;
        mUpdateSurfaceTexture = false;
        mCameraHandler.closeCamera();
        mCameraHandler.stopBackgroundThread();
    }

    @Override
    public void onSurfaceCreated (GL10 unused, javax.microedition.khronos.egl.EGLConfig eglConfig ) {
        initTex();
        mSurfaceTexture = new SurfaceTexture ( hTex[0] );
        mSurfaceTexture.setOnFrameAvailableListener(this);

        mCameraHandler.setSurfaceTexture(mSurfaceTexture);

        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f); // Clear white
        checkGlError("glClearColor");

        hProgram = loadShader (cameraVertexShader, cameraFragmentShader);

        Point ss = new Point();
        mView.getDisplay().getRealSize(ss);

        mCameraHandler.calcPreviewSize(mView.getContext(), ss.x, ss.y);
        mCameraHandler.openCamera(mView.getContext());

        mGLInit = true;
    }

    public void onDrawFrame ( GL10 unused ) {
        if ( !mGLInit ) return;
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        checkGlError("glClear");

        synchronized(this) {
            if (mUpdateSurfaceTexture) {
                mSurfaceTexture.updateTexImage();
                mUpdateSurfaceTexture = false;
            }
        }

        GLES20.glUseProgram(hProgram);
        checkGlError("glUseProgram");

        int tmh = GLES20.glGetUniformLocation ( hProgram, "uTexMatrix" );
        checkGlError("glGetUniformLocation");

        GLES20.glUniformMatrix4fv(tmh, 1, false, mTexMatrix, 0);
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
        GLES20.glViewport(0, 0, width, height);
        checkGlError("glViewport");
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
        mView.requestRender();
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
            checkGlError("glGetShaderiv");

            Log.e("Shader", "Could not compile vertex shader");
            Log.v("Shader", "Could not compile vertex shader:"+GLES20.glGetShaderInfoLog(vshader));
            GLES20.glDeleteShader(vshader);
            checkGlError("glDeleteShader");

            vshader = 0;
        }

        int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        checkGlError("glCreateShader");

        GLES20.glShaderSource(fshader, fss);
        checkGlError("glShaderSource");

        GLES20.glCompileShader(fshader);
        checkGlError("glCompileShader");

        GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        checkGlError("glGetShaderiv");

        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile fragment shader");
            Log.v("Shader", "Could not compile fragment shader:"+GLES20.glGetShaderInfoLog(fshader));

            GLES20.glDeleteShader(fshader);
            checkGlError("glDeleteShader");

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
}