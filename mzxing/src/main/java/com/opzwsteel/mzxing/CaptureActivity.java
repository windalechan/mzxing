package com.opzwsteel.mzxing;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewStub;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.zxing.Result;
import com.opzwsteel.mzxing.camera.CameraManager;
import com.opzwsteel.mzxing.decode.CaptureActivityHandler;
import com.opzwsteel.mzxing.decode.DecodeManager;
import com.opzwsteel.mzxing.decode.InactivityTimer;
import com.opzwsteel.mzxing.view.QrCodeFinderView;

import java.io.IOException;

/**
 * Created by xingli on 12/26/15.
 * <p/>
 * 二维码扫描类。
 */
public class CaptureActivity extends Activity implements Callback {
    private static final int REQUEST_SHOW_SCAN = 9991;
    private static final int REQUEST_PERMISSIONS = 1;

    private CaptureActivityHandler mCaptureActivityHandler;
    private boolean mHasSurface;
    private InactivityTimer mInactivityTimer;
    private QrCodeFinderView mQrCodeFinderView;
    private SurfaceView mSurfaceView;
    private ViewStub mSurfaceViewStub;
    private DecodeManager mDecodeManager = new DecodeManager();

    private ImageView btnFlashlight;
    private boolean playBeep;
    private boolean vibrate;
    private boolean flashlight = false;
    private SoundPool soundPool;
    private int soundId;
    //private int index = -1;
    private boolean isBarcode, isQRcode;
    private Bundle bundle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);

        //index = getIntent().getIntExtra("index", -1);
        isBarcode = getIntent().getBooleanExtra("barcode", false);
        isQRcode = getIntent().getBooleanExtra("qrcode", true);
        bundle = getIntent().getExtras();

        initView();
        initData();

        if (isBarcode && !isQRcode) {
            mQrCodeFinderView.changeToBarcode();
        }
    }

    public static void showActivity(Activity activity, int requestCode) {
        showActivity(activity, true, true, requestCode);
    }

    public static void showActivity(Activity activity, boolean isBarcode, boolean isQRcode) {
        showActivity(activity, isBarcode, isQRcode, REQUEST_SHOW_SCAN);
    }

    public static void showActivity(Activity activity, boolean isBarcode, boolean isQRcode, int requestCode) {
        Intent intent = new Intent(activity, CaptureActivity.class);
        intent.putExtra("barcode", isBarcode);
        intent.putExtra("qrcode", isQRcode);
        activity.startActivityForResult(intent, requestCode);
    }

    public static void showActivity(Activity activity, boolean isBarcode, boolean isQRcode, int index, int requestCode) {
        Intent intent = new Intent(activity, CaptureActivity.class);
        intent.putExtra("barcode", isBarcode);
        intent.putExtra("qrcode", isQRcode);
        intent.putExtra("index", index);
        activity.startActivityForResult(intent, requestCode);
    }

    private void initView() {
        mQrCodeFinderView = findViewById(R.id.qr_code_view_finder);
        mSurfaceViewStub = findViewById(R.id.qr_code_view_stub);

        btnFlashlight = findViewById(R.id.capture_light);
        btnFlashlight.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                flashlight = !flashlight;
                if (flashlight) {
                    turnFlashlightOn();
                    btnFlashlight.setImageResource(R.drawable.ic_flash_on_scan);
                } else {
                    turnFlashLightOff();
                    btnFlashlight.setImageResource(R.drawable.ic_flash_off_scan);
                }
            }
        });

        mHasSurface = false;
    }

    private void initData() {
        CameraManager.init();
        mInactivityTimer = new InactivityTimer(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA }, REQUEST_PERMISSIONS);
        }

        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;
    }

    private void initBeepSound() {
        if (playBeep && soundPool == null) {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
            soundId = soundPool.load(this, R.raw.beep, 1);
        }
    }

    private void initCamera() {
        if (null == mSurfaceView) {
            mSurfaceViewStub.setLayoutResource(R.layout.layout_surface_view);
            mSurfaceView = (SurfaceView) mSurfaceViewStub.inflate();
        }
        SurfaceHolder surfaceHolder = mSurfaceView.getHolder();
        if (mHasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCaptureActivityHandler != null) {
            try {
                mCaptureActivityHandler.quitSynchronously();
                mCaptureActivityHandler = null;
                mHasSurface = false;
                if (null != mSurfaceView) {
                    mSurfaceView.getHolder().removeCallback(this);
                }
                CameraManager.get().closeDriver();
            } catch (Exception e) {
                // 关闭摄像头失败的情况下,最好退出该Activity,否则下次初始化的时候会显示摄像头已占用.
                finish();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        findViewById(R.id.qr_code_view_background).setVisibility(View.VISIBLE);
        mQrCodeFinderView.setVisibility(View.GONE);
        mDecodeManager.showPermissionDeniedDialog(this);
    }

    @Override
    protected void onDestroy() {
        if (null != mInactivityTimer) {
            mInactivityTimer.shutdown();
        }
        super.onDestroy();
    }

    /**
     * Handler scan result
     *
     * @param result
     */
    public void handleDecode(Result result) {
        mInactivityTimer.onActivity();
        if (null == result) {
            mDecodeManager.showCouldNotReadQrCodeFromScanner(this, new DecodeManager.OnRefreshCameraListener() {
                @Override
                public void refresh() {
                    restartPreview();
                }
            });
        } else {
            String resultString = result.getText();
            handleResult(resultString);
        }
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            if (!CameraManager.get().openDriver(surfaceHolder, this)) {
                showPermissionDeniedDialog();
                return;
            }
        } catch (IOException e) {
            // 基本不会出现相机不存在的情况
            Toast.makeText(this, getString(R.string.qr_code_camera_not_found), Toast.LENGTH_SHORT).show();
            finish();
            return;
        } catch (RuntimeException re) {
            re.printStackTrace();
            showPermissionDeniedDialog();
            return;
        }
        mQrCodeFinderView.setVisibility(View.VISIBLE);
        btnFlashlight.setVisibility(View.VISIBLE);
        findViewById(R.id.qr_code_view_background).setVisibility(View.GONE);
        turnFlashLightOff();
        if (mCaptureActivityHandler == null) {
            mCaptureActivityHandler = new CaptureActivityHandler(this, isBarcode, isQRcode, mQrCodeFinderView.getFrameRect());
        }
    }

    private void restartPreview() {
        if (null != mCaptureActivityHandler) {
            try {
                mCaptureActivityHandler.restartPreviewAndDecode();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!mHasSurface) {
            mHasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHasSurface = false;
    }

    public Handler getCaptureActivityHandler() {
        return mCaptureActivityHandler;
    }


    private void turnFlashlightOn() {
        try {
            CameraManager.get().setFlashLight(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void turnFlashLightOff() {
        try {
            CameraManager.get().setFlashLight(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    protected void playBeepSoundAndVibrate() {
        if (playBeep && soundPool != null) {
            soundPool.play(soundId, 1f, 1f,0,0,1f);
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length != 0) {
            int cameraPermission = grantResults[0];
            if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
                initCamera();
            } else {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA }, REQUEST_PERMISSIONS);
            }
        }
    }

    private void handleResult(String resultString) {
        if (TextUtils.isEmpty(resultString)) {
            mDecodeManager.showCouldNotReadQrCodeFromScanner(this, new DecodeManager.OnRefreshCameraListener() {
                @Override
                public void refresh() {
                    restartPreview();
                }
            });
        } else {
            playBeepSoundAndVibrate();

            Intent data = new Intent();
            data.putExtra("epc", resultString);
            //if (index > -1) {
            //    data.putExtra("index", index);
            //}
            data.putExtras(bundle);
            setResult(RESULT_OK, data);
            finish();
        }
    }
}