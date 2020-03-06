package client.java;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import client.java.transmission.FrameSharing;
import client.java.utility.CommonResources;
import client.java.utility.Monitor;

public class MyHandler extends Handler {
    private Monitor monitor;
    private CommonResources resources;
    private Bitmap bmp;
    private FrameSharing frameSharing;
    private UpdaterOneSec updaterOneSec;

    private ImageView imageView;
    private TextView textViewFPS;

    private boolean enableFrameSharing;
    private long lastFrameSharing = 0,sharingFrameFrequency;
    private String IP;
    private int PORT;


    public MyHandler(Monitor m,CommonResources cm, boolean enableSharing, long sharingFrequency,
                     String ip, int port, ImageView iv, TextView tvFPS) {
        monitor = m;
        resources = cm;
        enableFrameSharing = enableSharing;
        sharingFrameFrequency = sharingFrequency;
        IP = ip;
        PORT = port;
        imageView = iv;
        textViewFPS = tvFPS;
    }

    @Override
    public void handleMessage(Message msg) {
        Bundle bundle = msg.getData();
        if (bundle.containsKey("update")) {
            if (monitor != null && monitor.useList()) {

                if (enableFrameSharing && System.currentTimeMillis() - lastFrameSharing > sharingFrameFrequency) {
                    bmp = resources.getFirstItem();
                    frameSharing = new FrameSharing(IP, PORT, resources.getPixelFromBitmap(bmp), bmp.getWidth(), bmp.getHeight());
                    frameSharing.start();
                    lastFrameSharing = System.currentTimeMillis();
                }

                imageView.setImageBitmap(resources.getFirstItem());
                resources.removeFirstItem();

                if (updaterOneSec == null) {
                    updaterOneSec = new UpdaterOneSec(this, resources, monitor);
                    updaterOneSec.start();
                }

                monitor.releaseList();
            }

            if (monitor != null && monitor.useFps()) {
                resources.newUpdateDone();
                monitor.releaseFps();
            }
        }
        else if (bundle.containsKey("oneSec")) {
            if (monitor != null && monitor.useFps()) {
                textViewFPS.setText(bundle.getString("oneSec"));
                monitor.releaseFps();
            }
        }
    }
}
