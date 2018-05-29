package com.douqu.media.mediarecorderdemo;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private Camera mCamera;
    //    private TextureView mPreview;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private MediaRecorder mMediaRecorder;
    private File mOutputFile;

    private boolean isRecording = false;
    private static final String TAG = "Recorder";
    private TextView captureButton;
    private ImageView okBtn;
    private ImageView cancelBtn;
    private int mRecordMaxTime = 1500;// 一次拍摄最长时间 15秒
    private int mTimeCount;// 时间计数
    private Timer mTimer;// 计时器
    TimerTask timerTask;
    private ProgressBar mProgressBar;
    private TextView secondes;
    private boolean preInitSuccess = false;
    private Camera.Parameters parameters;
    private Camera.Size optimalSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);// 去掉标题栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);// 设置全屏
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preInitSuccess = false;
        secondes = (TextView) findViewById(R.id.media_recorder_seconds);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();// 取得holder
        mSurfaceHolder.addCallback(this); // holder加入回调接口
        mSurfaceHolder.setKeepScreenOn(true);

//        mPreview = (TextureView) findViewById(R.id.surface_view);
        captureButton = (TextView) findViewById(R.id.button_capture);
        cancelBtn = (ImageView) findViewById(R.id.button_cancel);
        okBtn = (ImageView) findViewById(R.id.button_ok);
        mProgressBar = (ProgressBar) findViewById(R.id.media_actions_bar);
        captureButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (isRecording) {
                            stopRecord();
                        } else {
                            startRecord();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        stopRecord();
                        if (recordFinishListener != null) {
                            recordFinishListener.onRecordFinish();
                        }
                        break;
                }
                return true;
            }

        });
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelBtn.setVisibility(View.GONE);
                okBtn.setVisibility(View.GONE);
                captureButton.setVisibility(View.VISIBLE);
                mProgressBar.setProgress(0);
            }
        });
        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                finish();
            }
        });
    }

    private void setCaptureButtonText(boolean show) {
        if (show) {
            secondes.setVisibility(View.VISIBLE);
        } else {
            secondes.setVisibility(View.GONE);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        // if we are using MediaRecorder, release it first
        releaseMediaRecorder();
        // release the camera immediately on pause event
        releaseCamera();
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            // clear recorder configuration
            mMediaRecorder.reset();
            // release the recorder object
            mMediaRecorder.release();
            mMediaRecorder = null;
            // Lock camera for later use i.e taking it back from MediaRecorder.
            // MediaRecorder doesn't need it anymore and we will release it if the activity pauses.
            mCamera.lock();
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
        }
    }

    private boolean prepareVideoRecorder(int w, int h) {

        // BEGIN_INCLUDE (configure_preview)
        mCamera = CameraHelper.getDefaultBackFacingCameraInstance();
//        mCamera = CameraHelper.getDefaultCameraInstance();
        mCamera.setDisplayOrientation(90);// 将预览效果旋转90度
//        recorder.setOrientationHint(90); // 将获得的视频结果旋转90度
        parameters = mCamera.getParameters();

        // 自动对焦
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes != null) {
            for (String mode : focusModes) {
                if (mode.contains("continuous-video")) {
                    parameters.setFocusMode("continuous-video");
                    break;
                }
            }
        }
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
        optimalSize = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes,
                mSupportedPreviewSizes, w, h);
        Log.e("width", "mPreview" + mSurfaceView.getWidth());
        Log.e("width", "mPreview" + mSurfaceView.getHeight());
        // Use the same size for recording profile.

        // likewise for the camera object itself.
        parameters.setPreviewSize(optimalSize.width, optimalSize.height);
        mCamera.setParameters(parameters);
        try {
            // Requires API level 11+, For backward compatibility use {@link setPreviewDisplay}
            // with {@link SurfaceView}
//            mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
            return false;
        }
        mCamera.startPreview();

        // END_INCLUDE (configure_preview)
        return true;
    }

    /**
     * 录制前，初始化
     */
    private void initRecord() {
        try {

            if (mMediaRecorder == null) {
                mMediaRecorder = new MediaRecorder();

            }
            if (mCamera != null) {
                mCamera.unlock();
                mMediaRecorder.setCamera(mCamera);
            }
//            mMediaRecorder.setVideoFrameRate(25);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);// 视频源

            // Use the same size for recording profile.
            CamcorderProfile mProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
            mProfile.videoFrameWidth = optimalSize.width;
            mProfile.videoFrameHeight = optimalSize.height;

            mMediaRecorder.setProfile(mProfile);
            //该设置是为了抽取视频的某些帧，真正录视频的时候，不要设置该参数
//            mMediaRecorder.setCaptureRate(mFpsRange.get(0)[0]);//获取最小的每一秒录制的帧数
            // Step 4: Set output file
            mOutputFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO);

            mMediaRecorder.setOutputFile(mOutputFile.getPath());
            // END_INCLUDE (configure_media_recorder)
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            isRecording = true;
        } catch (Exception e) {
            e.printStackTrace();
            releaseRecord();
        }
    }

    /**
     * 释放资源
     */
    private void releaseRecord() {
        if (mMediaRecorder != null) {
            mMediaRecorder.setPreviewDisplay(null);
            mMediaRecorder.setOnErrorListener(null);
            try {
                mMediaRecorder.release();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mMediaRecorder = null;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        if (mCamera != null) {
            freeCameraResource();
        }

        try {
            mCamera = CameraHelper.getDefaultBackFacingCameraInstance();
            if (mCamera == null)
                return;
            mCamera.setDisplayOrientation(90);
            mCamera.setPreviewDisplay(mSurfaceHolder);
            parameters = mCamera.getParameters();// 获得相机参数

            List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
            List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
            optimalSize = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes,
                    mSupportedPreviewSizes, height, width);

            parameters.setPreviewSize(optimalSize.width, optimalSize.height); // 设置预览图像大小

//            parameters.set("orientation", "portrait");
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains("continuous-video")) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
//            mFpsRange =  parameters.getSupportedPreviewFpsRange();

            mCamera.setParameters(parameters);// 设置相机参数
            mCamera.startPreview();// 开始预览

        } catch (Exception io) {
            io.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    /**
     * 开始录制视频
     */
    public void startRecord() {

        cancelBtn.setVisibility(View.GONE);
        okBtn.setVisibility(View.GONE);
        try {
            initRecord();
            mTimeCount = 0;// 时间计数器重新赋值
            mTimer = new Timer();
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    mTimeCount++;
                    mProgressBar.setProgress(mTimeCount);
                    if (mTimeCount >= mRecordMaxTime) {// 达到指定时间，停止拍摄
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                stopRecord();
                            }
                        });

                        if (recordFinishListener != null) {
                            recordFinishListener.onRecordFinish();
                        }

                    }
                }
            };
            mTimer.schedule(timerTask, 0, 150);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 释放摄像头资源
     */
    private void freeCameraResource() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.lock();
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        cancelBtn.setVisibility(View.VISIBLE);
        okBtn.setVisibility(View.VISIBLE);
        captureButton.setVisibility(View.INVISIBLE);
//        mProgressBar.setProgress(0);
        if (timerTask != null)
            timerTask.cancel();
        if (mTimer != null)
            mTimer.cancel();
        if (mMediaRecorder != null) {
            // BEGIN_INCLUDE(stop_release_media_recorder)

            // stop recording and release camera
            try {
                mMediaRecorder.stop();  // stop the recording
                mMediaRecorder.reset();
            } catch (RuntimeException e) {
                // RuntimeException is thrown when stop() is called immediately after start().
                // In this case the output file is not properly constructed ans should be deleted.
                Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
                //noinspection ResultOfMethodCallIgnored
//                mOutputFile.delete();
            }
//            releaseMediaRecorder(); // release the MediaRecorder object
//            mCamera.lock();         // take camera access back from MediaRecorder

            // inform the user that recording has stopped
            setCaptureButtonText(true);
            isRecording = false;
//            releaseCamera();
            // END_INCLUDE(stop_release_media_recorder)

        }
    }

    OnRecordFinishListener recordFinishListener = new OnRecordFinishListener() {
        @Override
        public void onRecordFinish() {
            Toast.makeText(MainActivity.this, "拍摄完毕", Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * 录制完成回调接口
     */
    public interface OnRecordFinishListener {
        void onRecordFinish();
    }
}
