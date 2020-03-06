package client.java;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import client.java.transmission.FrameSharing;
import client.java.transmission.SensorsSharing;
import client.java.utility.AutoFitImageView;
import client.java.utility.AutoFitTextureView;
import client.java.utility.CommonResources;
import client.java.utility.Monitor;
import client.java.utility.Utils;

public class MainActivity extends AppCompatActivity {

    // Per attivare la pseudo inferenza
    public static boolean pseudoInference = false;
    public static long pseudoInferenceTime = 500;
    private long inferenceTime = pseudoInference ? pseudoInferenceTime : 100;

    // Per stabilire il numero di frame da processare "contemporaneamente" e attivare il campionamento
    private int frameConcorrenti = 3;
    private boolean enableInferenceFrequency = true;
    private long inferenceFrequency = inferenceTime / frameConcorrenti;

    // Per attivare/disattivare l'invio dei dati dei sensori e dei frame al server
    private boolean enableSensorsSharing = false;
    private boolean enableFrameSharing = false;

    // Per attivare/disattivare uso gpu
    public static boolean GPU = false;

    // Per superare errori della rotazione dell'emulatore
    public boolean onEmulator = false;
    // Per attivare/disattivare ridimensionamento e rotazioni
    public boolean enableTransform = true;
    private int rotation,orientation;

    private static final String TAG = "MainActivity";
    private AutoFitTextureView textureView;
    private AutoFitImageView imageView;
    private TextView textViewFPS,textViewFPSTeorici;

    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    // The size of the camera preview
    private Size previewSize;
    private ImageReader imageReader;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    // The size of tensorflow input
    private Size inputSize = Size.parseSize(Utils.Resolution.RES4.toString());
    private Handler handler = null;
    private PydnetModel[] pydnetModel;
    private CommonResources resources;
    private Monitor monitor;
    private UpdaterThread uT;
    private SensorsSharing sensorsSharing;

    private long sharingFrameFrequency = 30 * 1000;
    private long lastInference = 0;

    private int firstFree;

    private boolean iniziaSubito = true;
    private int frameScartati = 0, frameDaScartare = 100;
    private float fpsCamera;
    private long millisIniziali;

    private static String IP = "192.168.1.53";
    private static int PORT = 20000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        orientation = this.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT)
            setContentView(R.layout.activity_main);
        else
            setContentView(R.layout.activity_main_land);
        rotation = this.getWindowManager().getDefaultDisplay().getRotation();

        Log.d("ROTAZIONE","O -> " + this.getResources().getConfiguration().orientation + " (1=P,2=L)"
            + "\tR -> " + this.getWindowManager().getDefaultDisplay().getRotation());

        /*
        Bundle dati = getIntent().getExtras();
        IP = dati.getString("IP");
        try {
            PORT = Integer.parseInt(dati.getString("PORT"));
        } catch (NumberFormatException e) {
            Log.d(TAG,"Errore inserimento porta.");
        }
        */

    }

    private TextureView.SurfaceTextureListener textureListener;
    private TextureView.SurfaceTextureListener newSurfaceTextureListener() {
        return new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera();
                configureTransform(width,height);
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                newFrameAvailable();
            }
        };
    }

    private CameraDevice.StateCallback stateCallback;
    private CameraDevice.StateCallback newStateCallback() {
        return new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                Log.e(TAG, "onOpened");
                cameraDevice = camera;
                createCameraPreview();
            }
            @Override
            public void onDisconnected(CameraDevice camera) {
                cameraDevice.close();
            }
            @Override
            public void onError(CameraDevice camera, int error) {
                cameraDevice.close();
                cameraDevice = null;
            }
        };
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        if (mBackgroundThread == null)
            return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null == cameraDevice) {
                        return;
                    }
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            //previewSize = map.getOutputSizes(SurfaceTexture.class)[0];
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        inputSize.getWidth(), inputSize.getHeight());

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            stateCallback = newStateCallback();
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }

    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (!enableTransform || previewSize == null || textureView == null)
            return;

        Log.d("Size","PreviewSize -> " + previewSize);
        Log.d("Size","TextureView -> " + size(textureView));
        Log.d("Size", "TextureView e PreviewSize -> " + sameAspectRatio(textureView.getWidth(),textureView.getHeight(),
                    previewSize.getWidth(),previewSize.getHeight()));
        Log.d("Size","ImageView -> " + size(imageView));
        Log.d("Size", "ImageView e InputSize -> " + sameAspectRatio(imageView.getWidth(),imageView.getHeight(),
                    inputSize.getWidth(),inputSize.getHeight()));

        Matrix matrix = new Matrix();
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0,0,viewWidth,viewHeight);
        RectF previewRectF;
        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();
        float scaleM,scaleI;

        if (onEmulator) {
            if (rotation == Surface.ROTATION_90) {
                float scale = Math.max((float) viewWidth / previewSize.getWidth(),
                        (float) viewHeight / previewSize.getHeight());
                Log.d("Size", "Scale --> " + scale);
                matrix.postScale(scale, scale, centerX, centerY);
                textureView.setTransform(matrix);
                return;
            } else if (rotation == Surface.ROTATION_0) {
                float scale = Math.max((float) viewWidth / previewSize.getWidth(),
                        (float) viewHeight / previewSize.getHeight());
                Log.d("Size", "Scale --> " + scale);
                matrix.postScale(scale, scale, centerX, centerY);
                textureView.setTransform(matrix);
                return;
            }
        }

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            previewRectF = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
            previewRectF.offset(centerX - previewRectF.centerX(), centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF,previewRectF, Matrix.ScaleToFit.FILL);
            scaleM = Math.max((float) viewWidth/previewSize.getWidth(),
                                (float) viewHeight/previewSize.getHeight());
            matrix.postScale(scaleM,scaleM,centerX,centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            /*
            scaleI = ((float)inputSize.getWidth() / inputSize.getHeight()) *
                    ((float)imageView.getWidth() / imageView.getHeight());
            imageView.setScaleX(scaleI);
            imageView.setScaleY(scaleI);
            Log.d("Size","Scale -> M " + scaleM + "\t I " + scaleI);
            */
        } else {
            if (rotation == Surface.ROTATION_180)
                matrix.postRotate(180, centerX, centerY);
            scaleM = ((float) previewSize.getWidth() / previewSize.getHeight()) *
                    ((float) textureView.getWidth() / textureView.getHeight());
            matrix.postScale(1, scaleM, centerX, centerY);
            imageView.setScaleY(scaleM);
        }

        textureView.setTransform(matrix);
    }

    private void avvio() {
        textureView = (AutoFitTextureView) findViewById(R.id.textureView);

        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            Size auxPreview = chooseOptimalSize(manager.getCameraCharacteristics(manager.getCameraIdList()[0]).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class),
                    inputSize.getWidth(), inputSize.getHeight());
            textureView.setAspectRatio(auxPreview.getWidth(), auxPreview.getHeight());
        } catch (CameraAccessException e) {
            Log.d(TAG, "Avvio -> errore preview.");
        }

        textureListener = newSurfaceTextureListener();
        textureView.setSurfaceTextureListener(textureListener);

        imageView = (AutoFitImageView) findViewById(R.id.imageView);
        imageView.setAspectRatio(inputSize.getWidth(),inputSize.getHeight());

        textViewFPS = (TextView) findViewById(R.id.textViewFPS);
        textViewFPSTeorici = (TextView) findViewById(R.id.textViewFpsTeorici);

        resources = new CommonResources(frameConcorrenti,inputSize);
        monitor = new Monitor(resources);

        handler = new MyHandler(monitor,resources,enableFrameSharing,sharingFrameFrequency,IP,PORT,imageView,textViewFPS);

        pydnetModel = new PydnetModel[frameConcorrenti];
        for (int i=0; i<pydnetModel.length; i++)
            pydnetModel[i] = new PydnetModel(getApplicationContext(),inputSize,i,resources,monitor);

        if (enableSensorsSharing) {
            sensorsSharing = new SensorsSharing(IP, PORT, getApplicationContext());
            if (sensorsSharing.create())
                Toast.makeText(getApplicationContext(), "Gestore sensori avviato!", Toast.LENGTH_SHORT).show();
            else {
                sensorsSharing.destroy();
                Toast.makeText(getApplicationContext(), "Errore avvio gestore sensori!", Toast.LENGTH_SHORT).show();
            }
        }

        millisIniziali = System.currentTimeMillis();
    }

    public void newFrameAvailable() {
        if (iniziaSubito == false) {
            frameScartati++;
            if (frameScartati >= frameDaScartare) {
                iniziaSubito = true;
                fpsCamera = Math.round(10 * frameScartati*1000f/(System.currentTimeMillis() - millisIniziali)) / 10f;
                float fpsIV = Math.round(10 * 1000f * frameConcorrenti / inferenceTime) / 10f;
                String s = "FPS Camera=" + fpsCamera;
                if (pseudoInference)
                    s += "\nMaxFPSImageView=" + fpsIV;
                textViewFPSTeorici.setText(s);
            }
            return;
        }

        if (monitor.useAvgInferenceTime()) {
            if (!pseudoInference && resources.getAvgInference() != -1)
                inferenceFrequency = resources.getAvgInference() / frameConcorrenti;
            monitor.releaseAvgInferenceTime();
        }

        if ( (!enableInferenceFrequency ||
                System.currentTimeMillis() - lastInference > inferenceFrequency)
                && (firstFree = monitor.startNewInference()) != -1) {
            lastInference = System.currentTimeMillis();
            if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                //resources.getPixelFromBitmapByteBuffer(textureView.getBitmap(resources.getH(),resources.getW()), firstFree,rotation);
                resources.getPixelFromBitmapByteBuffer(textureView.getBitmap(previewSize.getHeight(),previewSize.getWidth()), firstFree,rotation,true);
            else
                //resources.getPixelFromBitmapByteBuffer(textureView.getBitmap(resources.getW(), resources.getH()), firstFree);
                resources.getPixelFromBitmapByteBuffer(textureView.getBitmap(previewSize.getWidth(),previewSize.getHeight()), firstFree, true);
            uT = new UpdaterThread(resources,monitor,handler,pydnetModel[firstFree],firstFree);
            uT.start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        avvio();
        if (textureView.isAvailable()) {
            openCamera();
            configureTransform(textureView.getWidth(),textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        stopBackgroundThread();
        if (monitor.useFps()) {
            resources.setStopUpdaterOneSec(true);
            monitor.releaseFps();
        }
        stateCallback = null;
        textureListener = null;
        if (textureView != null)
            textureView.setSurfaceTextureListener(null);
        handler = null;
        resources = null;
        monitor = null;
        closeCamera();
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the minimum of both, or an exact match if possible.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param width The minimum desired width
     * @param height The minimum desired height
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    protected static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
        final int minSize = Math.min(width, height);
        final Size desiredSize = new Size(width, height);

        // Collect the supported resolutions that are at least as big as the preview Surface
        boolean exactSizeFound = false;
        final List<Size> bigEnough = new ArrayList<Size>();
        final List<Size> tooSmall = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.equals(desiredSize)) {
                // Set the size but don't return yet so that remaining sizes will still be logged.
                exactSizeFound = true;
            }

            if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }

        if (exactSizeFound)
            return desiredSize;

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
            return chosenSize;
        } else
            return choices[0];
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }


    // DA ELIMINARE

    public static String size(View v) {
        return v.getWidth() + "x" + v.getHeight();
    }

    public static String sameAspectRatio(int w1,int h1,int w2, int h2) {
        if ((w1 / h1) / (w2 / h2) == 1)
            return "Aspect Ratio OK.";
        else
            return "Aspect Ratio Errato.";
    }


}
