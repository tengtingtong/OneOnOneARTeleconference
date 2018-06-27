package com.exzy.oneononearteleconference;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usbcameracommon.UVCCameraHandlerMultiSurface;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.widget.UVCCameraTextureView;

import org.opencv.android.JavaCameraView;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;

// OpenCV Classes

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2, View.OnTouchListener {

    // Used for logging success or failure messages
    private static final String TAG = "OCVSample::Activity";

    // Loads camera view of OpenCV for us to use. This lets us see using OpenCV
    private CameraBridgeViewBase mOpenCvCameraView;

    // Used in Camera selection from menu (when implemented)
    private boolean mIsJavaCamera = true;
    private MenuItem mItemSwitchCamera = null;

    private Intent intent;

    private USBMonitor mUSBMonitor;
    private UVCCameraHandlerMultiSurface mCameraHandler;
    private UVCCameraTextureView mUVCCameraView;


    // These variables are used (at the moment) to fix camera orientation from 270degree to 0degree
    Mat mRgba, mRgb, fgmask, bg, result;
    Mat kernel;
    boolean DEBUG = true;

    private static final boolean USE_SURFACE_ENCODER = false;
    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    private static final int PREVIEW_MODE = 1;



    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.show_camera);

        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.show_camera_activity_java_surface_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        //get Callback when touch the screen
        mOpenCvCameraView.setOnTouchListener(this);

        //Set up to use front camera
        mOpenCvCameraView.setCameraIndex(1);

        mOpenCvCameraView.setCvCameraViewListener(this);

        mUVCCameraView = findViewById(R.id.camera_view);
        mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mCameraHandler = UVCCameraHandlerMultiSurface.createHandler(this, mUVCCameraView,
                USE_SURFACE_ENCODER ? 0:1, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);

    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onStart(){
        super.onStart();
        mUSBMonitor.register();
    }

    @Override
    public void onStop(){
        stopPreview();
        mCameraHandler.close();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        if (mCameraHandler != null) {
	        mCameraHandler.release();
	        mCameraHandler = null;
        }
        if (mUSBMonitor != null) {
	        mUSBMonitor.destroy();
	        mUSBMonitor = null;
        }
        mUVCCameraView = null;
        super.onDestroy();

    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mRgb = new Mat(height, width, CvType.CV_8UC3);
        bg = new Mat(height, width, CvType.CV_8UC3);
        fgmask = new Mat(height, width, CvType.CV_8UC3);
        kernel = Mat.ones(3,3, CvType.CV_8UC1);
        result = new Mat(height, width, CvType.CV_8UC3);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        result = BackgroundSubtraction(mRgba, bg);
        return result; // This function must return
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        bg = Imgcodecs.imread(SaveImage(mRgba));
        Toast.makeText(this, "Frame saved", Toast.LENGTH_SHORT).show();
        return false;
    }

    private int mPreviewSurfaceId;


    private void stopPreview() {
        if (DEBUG) Log.v(TAG, "stopPreview:");
        if (mPreviewSurfaceId != 0) {
            mCameraHandler.removeSurface(mPreviewSurfaceId);
            mPreviewSurfaceId = 0;
        }
        mCameraHandler.close();
    }



    /* ========================================================================================
     *                               About Device Connection
     *  ======================================================================================== */
    private final OnDeviceConnectListener mOnDeviceConnectListener
            = new OnDeviceConnectListener() {

        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(MainActivity.this,
                    "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnect(final UsbDevice device,
                              final UsbControlBlock ctrlBlock, final boolean createNew) {
            if (DEBUG) Log.v(TAG, "onDisconnect:");
            mCameraHandler.open(ctrlBlock);
			if (!mCameraHandler.isOpened()) {
			    mCameraHandler.startPreview();
				mCameraHandler.open(ctrlBlock);
				final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
				final Surface surface = new Surface(st);
				mPreviewSurfaceId = surface.hashCode();
				mCameraHandler.addSurface(mPreviewSurfaceId, surface, false);
				}
        }

        @Override
        public void onDisconnect(final UsbDevice device,
                                 final UsbControlBlock ctrlBlock) {

            if (DEBUG) Log.v(TAG, "onDisconnect:");
            if (mCameraHandler != null) {
                stopPreview();
                }
        }
        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(MainActivity.this,
                    "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };

    /* ========================================================================================
    *                                 Non - Listener Method
    *  ======================================================================================== */


    public String SaveImage(Mat mat) {
        Mat mIntermediateMat = new Mat();

        Imgproc.cvtColor(mRgba, mIntermediateMat, Imgproc.COLOR_RGBA2RGB);

        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        String filename = "Background.jpg";
        File file = new File(path, filename);

        Boolean bool = null;
        filename = file.toString();
        bool = Imgcodecs.imwrite(filename, mIntermediateMat);

        return filename;
    }

    public Mat BackgroundSubtraction(Mat original, Mat background){
        Imgproc.cvtColor(original, mRgb, Imgproc.COLOR_RGBA2RGB);
        Core.absdiff(mRgb, background, fgmask);
        Imgproc.cvtColor(fgmask, fgmask, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(fgmask, fgmask, 10.0d, 255.0d, Imgproc.THRESH_BINARY);

        Imgproc.dilate(fgmask, fgmask, kernel);
        Imgproc.erode(fgmask, fgmask, kernel, new Point(1,1),3);

        Imgproc.medianBlur(fgmask, fgmask, 5);
        Imgproc.cvtColor(fgmask, fgmask, Imgproc.COLOR_GRAY2RGB);

        Core.bitwise_and(mRgb, fgmask, mRgb);

        return mRgb;
    }

}