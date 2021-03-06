package com.gscoder.androidglescamera2;

import android.Manifest;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.Arrays;

public class MainActivity extends FragmentActivity implements PermissionsHelper.PermissionsListener, CameraGLSurfaceView.OnBitmapAvailableListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private CameraGLSurfaceView mCameraGLSurfaceView;
    private ImageView mImageView;
    private PermissionsHelper mPermissionsHelper;
    private boolean mPermissionsSatisfied = false;

    @Override
    public void onCreate ( Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        int ui = getWindow().getDecorView().getSystemUiVisibility();
        ui = ui | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(ui);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView ( R.layout.main_layout );

        mCameraGLSurfaceView = (CameraGLSurfaceView) findViewById(R.id.cameraGLSurfaceView);
        mImageView = (ImageView) findViewById(R.id.imageView);
        mCameraGLSurfaceView.setOnBitmapAvailableListener(this);

        if(PermissionsHelper.isMorHigher())
            setupPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraGLSurfaceView.onResume();

        if(PermissionsHelper.isMorHigher() && !mPermissionsSatisfied) {
            if(!mPermissionsHelper.checkPermissions())
                return;
            else
                mPermissionsSatisfied = true; //extra helper as callback sometimes isnt quick enough for future results
        }
    }

    @Override
    protected void onPause() {
        mCameraGLSurfaceView.onPause();
        super.onPause();
    }

    private void setupPermissions() {
        mPermissionsHelper = PermissionsHelper.attach(this);
        mPermissionsHelper.setRequestedPermissions(
                Manifest.permission.CAMERA
        );
    }

    @Override
    public void onPermissionsSatisfied() {
        Log.d(TAG, "onPermissionsSatisfied()");
        mPermissionsSatisfied = true;

    }

    @Override
    public void onPermissionsFailed(String[] failedPermissions) {
        Log.e(TAG, "onPermissionsFailed()" + Arrays.toString(failedPermissions));
        mPermissionsSatisfied = false;
        Toast.makeText(this, "shadercam needs all permissions to function, please try again.", Toast.LENGTH_LONG).show();
        this.finish();
    }

    @Override
    public void onBitmapAvailable(final Bitmap var1) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mImageView.setImageBitmap(var1);
            }
        });
    }
}
