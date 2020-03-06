package client.java;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import client.java.utility.CommonResources;
import client.java.utility.Monitor;

public class UpdaterThread extends Thread {

    private static final String TAG = "UpdaterThread";
    private CommonResources risorse;
    private Monitor monitor;
    private PydnetModel pydnetModel;
    private Handler handler;
    private Message message;
    private Bundle bundle;
    private int modelIndex;

    public UpdaterThread (CommonResources r, Monitor m, Handler ha, PydnetModel model, int i) {
        risorse = r;
        monitor = m;
        handler = ha;
        pydnetModel = model;
        modelIndex = i;
    }

    @Override
    public void run() {
            if (MainActivity.pseudoInference) {
                long s = System.currentTimeMillis();
                try {
                    sleep(MainActivity.pseudoInferenceTime);
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < risorse.outputInt[modelIndex].length; i++)
                    risorse.outputInt[modelIndex][i] = risorse.inputInt[modelIndex][i];
                Log.d(TAG, "Pseudo Inference Timecost: " + (System.currentTimeMillis() - s) + " ms.");
            } else {
                risorse.outputFloat[modelIndex] = pydnetModel.doInference(risorse.input[modelIndex]);
                int colorIndex;
                for (int i = 0; i < risorse.outputInt[modelIndex].length; i++) {
                    colorIndex = (int) (risorse.outputFloat[modelIndex][i] * 10.5f);
                    colorIndex = Math.min(Math.max(colorIndex, 0), risorse.getColorsSize() - 1);
                    risorse.outputInt[modelIndex][i] = risorse.getColor(colorIndex);
                }
            }

            if (monitor.useList()) {
                if (risorse.daScartare(getId()) == false) {
                    risorse.addItem(Bitmap.createBitmap(risorse.outputInt[modelIndex], risorse.getW(), risorse.getH(), Bitmap.Config.ARGB_8888), getId());
                    message = handler.obtainMessage();
                    bundle = new Bundle();
                    bundle.putString("update", "");
                    message.setData(bundle);
                    handler.sendMessage(message);
                } else
                    Log.d(TAG, "Frame " + getId() + " scartato.");
                monitor.releaseList();
            }

            monitor.finishInference(modelIndex);
    }

}
