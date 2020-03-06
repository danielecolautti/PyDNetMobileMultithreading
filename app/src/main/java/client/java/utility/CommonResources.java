package client.java.utility;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import client.java.MainActivity;
import client.java.transmission.FrameSharing;

public class CommonResources {
    private static final String TAG = "CommonResources";
    private ArrayList<Bitmap> daVisualizzare;
    private ArrayList<Long> daVisualizzareId;
    private String[] colors;
    private int w,h;
    private long avgInference = -1, totalInferenceTime = 0, inferences = 0;
    private int frameConcorrenti;
    private long lastUpdate = 0, frameFromLastUpdate = 0, firstFrameUpdated = 0, numberFrameUpdated = 0;
    private boolean stopUpdaterOneSec = false;

    public int[][] inputDaRaddrizare;
    public ByteBuffer[] input;
    public int[][] inputInt;
    public float[][] outputFloat;
    public int[][] outputInt;
    public ByteBuffer[] output;
    public float[][] output_reshaped;

    public boolean getStopUpdaterOneSec() {
        return stopUpdaterOneSec;
    }

    public void setStopUpdaterOneSec(boolean flag) {
        stopUpdaterOneSec = flag;
    }

    public int getFrameConcorrenti() {
        return frameConcorrenti;
    }

    public long getAvgInference() {
        return avgInference;
    }

    public CommonResources(int s, android.util.Size r) {
        frameConcorrenti = s;
        daVisualizzare = new ArrayList<>();
        daVisualizzareId = new ArrayList<>();
        colors = getPlasma();
        inputDaRaddrizare = new int[s][];
        input = new ByteBuffer[s];
        inputInt = new int[s][];
        outputFloat = new float[s][];
        outputInt = new int[s][];
        output = new ByteBuffer[s];
        output_reshaped = new float[s][];
        w = r.getWidth();
        h = r.getHeight();
        for (int i=0; i<s; i++) {
            inputDaRaddrizare[i] = new int[w*h];
            input[i] = ByteBuffer.allocateDirect(w * h * 3 * Float.BYTES).order(ByteOrder.nativeOrder());
            inputInt[i] = new int[w*h];
            outputFloat[i] = new float[w*h];
            outputInt[i] = new int[w*h];
            output[i] = ByteBuffer.allocateDirect(w * h * Float.BYTES).order(ByteOrder.nativeOrder());
            output_reshaped[i] = new float[w*h];
        }
    }

    private String[] getPlasma() {
        List<String> colorsList = Utils.getPlasma();
        String[] colorsArray = new String[colorsList.size()];
        for (int i=0; i<colorsArray.length; i++)
            colorsArray[i] = colorsList.get(i);
        return colorsArray;
    }

    public String[] getColors() {
        return colors;
    }

    public int getColorsSize() {
        return colors.length;
    }

    public int getColor(int index) {
        if (index < 0 || index >= colors.length)
            return Color.WHITE;
        return Color.parseColor(colors[index]);
    }

    public void removeFirstItem() {
        if (daVisualizzare.isEmpty() == false) {
            daVisualizzare.remove(0);
            daVisualizzareId.remove(0);
        }
    }

    public Bitmap getFirstItem() {
        return daVisualizzare.get(0);
    }

    public void calculateAvgInference(long time) {
        if (avgInference == -1) {
            avgInference = time;
            inferences = 1;
        } else {
            if (inferences > 15 && (time < avgInference - avgInference * 0.5 || time > avgInference + avgInference * 0.5))
                return;
            if (totalInferenceTime >= Long.MAX_VALUE - time) {
                inferences = 1;
                totalInferenceTime = avgInference;
            }
            totalInferenceTime += time;
            inferences++;
            avgInference = totalInferenceTime / inferences;
            Log.d(TAG, "AvgInference=" + avgInference + "ms");
        }
    }

    public void addItem(Bitmap v,long id) {
        daVisualizzare.add(v);
        daVisualizzareId.add(id);
    }

    public boolean daScartare(long id) {
        if (daVisualizzareId.isEmpty())
            return false;
        else if (daVisualizzareId.get(daVisualizzareId.size() -1) > id)
            return true;
        return false;
    }

    public void newUpdateDone() {
        frameFromLastUpdate++;
        if (firstFrameUpdated==0)
            firstFrameUpdated = System.currentTimeMillis();
        numberFrameUpdated++;
    }

    public float getFpsAtt() {
        float fpsAtt = frameFromLastUpdate * 1000f / (System.currentTimeMillis() - lastUpdate);
        fpsAtt = Math.round(fpsAtt*10) / 10f;
        frameFromLastUpdate = 0;
        lastUpdate = System.currentTimeMillis();
        return fpsAtt;
    }

    public float getFpsAvg() {
        float fpsAvg = numberFrameUpdated * 1000f / (System.currentTimeMillis() - firstFrameUpdated);
        fpsAvg = Math.round(fpsAvg*10) / 10f;
        Log.d(TAG,fpsAvg + "fps" + " Frame=" + numberFrameUpdated + ",Sec=" + (System.currentTimeMillis() - firstFrameUpdated)/1000);
        return fpsAvg;
    }

    public int getW() {
        return w;
    }

    public int getH() {
        return h;
    }

    public int[] getPixelFromBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] array = new int[width*height];
        bitmap.getPixels(array,0,width,0,0,width,height);
        return array;
    }

    public void getPixelFromBitmapInt(Bitmap bitmap, int i) {
        bitmap.getPixels(inputInt[i],0,w,0,0,w,h);
    }

    public void getPixelFromBitmapByteBuffer(Bitmap bitmap,int n) {
        int val;
        getPixelFromBitmapInt(bitmap,n);

        float IMAGE_MEAN = 127.5f;
        float IMAGE_STD = 127.5f;

        int index = 0;
        for (int i = 0; i < w; ++i) {
            for (int j = 0; j < h; ++j) {
                val = inputInt[n][index];
                index++;
                input[n].putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                input[n].putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                input[n].putFloat(((val & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }

        input[n].rewind();
    }

    public void getPixelFromBitmapByteBuffer(Bitmap bitmap,int n, boolean prova) {
        int val,indexI = 0;
        int[] inputBigger = new int[bitmap.getHeight() * bitmap.getWidth()];
        bitmap.getPixels(inputBigger,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());

        int lineeDaTogliere = (bitmap.getHeight()-h) / 2;
        for (int i=0; i<bitmap.getHeight(); i++) {
            if (i>=lineeDaTogliere && i<(bitmap.getHeight() - lineeDaTogliere)) {
                for (int j = 0; j < bitmap.getWidth(); j++) {
                    inputInt[n][indexI] = inputBigger[i * bitmap.getWidth() + j];
                    indexI++;
                }
            }
        }

        //(new FrameSharing("192.168.1.53",20000,inputInt[n],640,448)).start();

        float IMAGE_MEAN = 127.5f;
        float IMAGE_STD = 127.5f;

        int index = 0;
        for (int i = 0; i < w; ++i) {
            for (int j = 0; j < h; ++j) {
                val = inputInt[n][index];
                index++;
                input[n].putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                input[n].putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                input[n].putFloat(((val & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }

        input[n].rewind();
    }

    public void getPixelFromBitmapByteBuffer (Bitmap bitmap,int n,int  rotation) {
        inputDaRaddrizare[n] = getPixelFromBitmap(bitmap);
        if (rotation == Surface.ROTATION_90) {
            for (int y = 0; y < w; y++) {
                for (int x = 0; x < h; x++)
                    inputInt[n][(h - x - 1) * w + y] = inputDaRaddrizare[n][y * h + x];
            }
        } else {
            for (int y = 0; y < w; y++) {
                for (int x = 0; x < h; x++)
                    inputInt[n][w - y - 1 + x * w] = inputDaRaddrizare[n][y * h + x];
            }
        }
        float IMAGE_MEAN = 127.5f;
        float IMAGE_STD = 127.5f;
        int val;
        int index = 0;
        for (int i = 0; i < w; ++i) {
            for (int j = 0; j < h; ++j) {
                val = inputInt[n][index];
                index++;
                input[n].putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                input[n].putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                input[n].putFloat(((val & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }

        input[n].rewind();
    }

    public void getPixelFromBitmapByteBuffer (Bitmap bitmap,int n,int  rotation, boolean prova) {
        int bW = bitmap.getHeight(), bH = bitmap.getWidth();
        int[] inputBigger = getPixelFromBitmap(bitmap), inputBiggerDritto = new int[inputBigger.length];

        if (rotation == Surface.ROTATION_90) {
            for (int y = 0; y < bW; y++) {
                for (int x = 0; x < bH; x++)
                    inputBiggerDritto[(bH - x - 1) * bW + y] = inputBigger[y * bH + x];
            }
        } else {
            for (int y = 0; y < bW; y++) {
                for (int x = 0; x < bH; x++)
                    inputBiggerDritto[bW - y - 1 + x * bW] = inputBigger[y * bH + x];
            }
        }

        int indexI=0;
        for (int i=0; i<bH; i++) {
            if (i>=(bH-h)/2 && i<bH - ((bH-h)/2)) {
                for (int j = 0; j < bW; j++) {
                    inputInt[n][indexI] = inputBiggerDritto[i * bW + j];
                    indexI++;
                }
            }
        }

        //(new FrameSharing("192.168.1.53",20000,inputInt[n],640,448)).start();

        float IMAGE_MEAN = 127.5f;
        float IMAGE_STD = 127.5f;
        int val;
        int index = 0;
        for (int i = 0; i < w; ++i) {
            for (int j = 0; j < h; ++j) {
                val = inputInt[n][index];
                index++;
                input[n].putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                input[n].putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                input[n].putFloat(((val & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }

        input[n].rewind();
    }

}
