package client.java;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import client.java.utility.CommonResources;
import client.java.utility.Monitor;

public class UpdaterOneSec extends Thread {
    private String TAG = "UpdaterOneSec";
    private Handler handler;
    private Message message;
    private Bundle bundle;
    private CommonResources resources;
    private Monitor monitor;
    private float fpsAtt,fpsAvg;

    public UpdaterOneSec (Handler h, CommonResources r, Monitor m) {
        handler = h;
        resources = r;
        monitor = m;
    }

    @Override
    public void run() {
        String fpsString;

        while (true) {
            try {
                Log.d(TAG,Thread.activeCount() + " thread attivi.");
                sleep(1000);
                if (monitor.useFps()) {
                    if (resources.getStopUpdaterOneSec()) {
                        monitor.releaseFps();
                        Log.d(TAG,"UpdaterOneSec " + getId() + " terminato.");
                        break;
                    }
                    fpsAtt = resources.getFpsAtt();
                    fpsAvg = resources.getFpsAvg();
                    fpsString = "Att=" + fpsAtt + "fps  Avg=" + fpsAvg + "fps";

                    message = handler.obtainMessage();
                    bundle = new Bundle();
                    bundle.putString("oneSec", fpsString);
                    message.setData(bundle);
                    handler.sendMessage(message);

                    monitor.releaseFps();
                }
            } catch (InterruptedException e) {
                continue;
            }
        }
    }
}
