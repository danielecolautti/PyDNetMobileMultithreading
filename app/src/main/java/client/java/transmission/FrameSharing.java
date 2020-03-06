package client.java.transmission;

import android.util.Log;

public class FrameSharing extends Thread {

    private static final String TAG = "FrameSharing";
    private Connection connectionFrame;
    private int[] frame;
    private int width,height;

    public FrameSharing (String ip,int port,int[] array, int w, int h) {
        connectionFrame = new Connection(ip,port);
        frame = array.clone();
        width = w;
        height = h;
    }

    @Override
    public void run() {
        if (connectionFrame.startConnection() == 0) {
            long s = System.currentTimeMillis();
            Log.d(TAG, "Invio frame " + width + "x" + height + "...");
            connectionFrame.invia("Frame");
            connectionFrame.invia(width);
            connectionFrame.invia(height);
            connectionFrame.invia(frame);
            connectionFrame.stopConnection();
            Log.d(TAG, "Frame inviato in " + (System.currentTimeMillis() - s) + " ms!");
        }
    }
}
