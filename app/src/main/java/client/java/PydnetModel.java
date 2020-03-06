package client.java;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import client.java.utility.CommonResources;
import client.java.utility.Monitor;
import client.java.utility.Size;
import client.java.utility.Utils;

public class PydnetModel {

    private static final String TAG = "PydnetModel";
    private static final String modelName = "pydnet++.tflite";
    private MappedByteBuffer TFLmodel;
    private HashMap<String, String> inputNodes;
    private Map<Utils.Scale, String> outputNodes;
    private List<Utils.Resolution> validResolutions;
    private Interpreter.Options options;
    private Interpreter inferenceEngine;
    private int w,h,id;
    private CommonResources risorse;
    private Monitor monitor;
    private long s;

    private Object[] inputObjectArray;
    private Map<Integer,Object> outputMap;


    public PydnetModel(Context context, android.util.Size resolution,int i,CommonResources r, Monitor m){

        try {
            TFLmodel = loadModelFile(context, modelName);
        } catch (IOException e) {
            Log.d(TAG,"Errore caricamento file modello");
        }

        validResolutions = new ArrayList<>();
        outputNodes = new HashMap<>();
        inputNodes = new HashMap<>();

        h=resolution.getHeight();
        w=resolution.getWidth();
        id = i;
        risorse=r;
        monitor=m;

        inputObjectArray = new Object[1];

        addInputNode("image", "im0");
        addOutputNodes(Utils.Scale.HALF, "PSD/resize/ResizeBilinear");
        addOutputNodes(Utils.Scale.QUARTER, "PSD/resize_1/ResizeBilinear");
        addOutputNodes(Utils.Scale.HEIGHT, "PSD/resize_2/ResizeBilinear");

        Utils.Resolution res = null;
        int l=0;
        while (res==null && l<Utils.Resolution.values().length) {
            if (Utils.Resolution.values()[l].getWidth() == resolution.getWidth() &&
                    Utils.Resolution.values()[l].getHeight() == resolution.getHeight())
                res = Utils.Resolution.values()[l];
            l++;
        }
        if (res!=null)
            addValidResolution(res);
    }

    public void addOutputNodes(Utils.Scale scale, String node){
        this.outputNodes.put(scale,node);
    }

    public void addInputNode(String name, String node){
        if(!this.inputNodes.containsKey(name))
            this.inputNodes.put(name, node);
    }

    public void addValidResolution(Utils.Resolution resolution){
        if(!this.validResolutions.contains(resolution))
            this.validResolutions.add(resolution);
    }

    public String getInputNode(String name){
        return this.inputNodes.get(name);
    }

    public void update() {
        if (MainActivity.GPU == false) {
            // CPU
            options = (new Interpreter.Options())
                    .setNumThreads(Runtime.getRuntime().availableProcessors());
            inferenceEngine = new Interpreter(TFLmodel, options);
        } else {
            // GPU
            options = (new Interpreter.Options())
                    .setNumThreads(Runtime.getRuntime().availableProcessors())
                    .addDelegate(new GpuDelegate());

            if (inferenceEngine == null)
                close();
            inferenceEngine = new Interpreter(TFLmodel, options);
        }
        /* NNAPI
        options  = (new Interpreter.Options())
                    .setNumThreads(Runtime.getRuntime().availableProcessors())
                    .addDelegate(new NnApiDelegate());

        if (inferenceEngine == null) ???
            close();
            inferenceEngine = new Interpreter(TFLmodel,options);
        */
    }

    public void close() {
        if (inferenceEngine!=null)
            inferenceEngine.close();
        inferenceEngine = null;
    }

    /*
    public float[] doInference(ByteBuffer input) {
        if (inferenceEngine==null)
            update();
        risorse.output[id].rewind();
        s = System.currentTimeMillis();
        inferenceEngine.run(input, risorse.output[id]);
        risorse.calculateAvgInference(System.currentTimeMillis() - s);
        Log.d(TAG, "Inference Timecost: " + (System.currentTimeMillis() - s) + " ms.");
        risorse.output[id].rewind();
        for(int row_index=0; row_index < h; row_index++) {
            for(int col_index=0; col_index < w; col_index++)
                risorse.output_reshaped[id][row_index*w+col_index] = risorse.output[id].getFloat();
        }
        return risorse.output_reshaped[id];
    }
    */

    public float[] doInference(ByteBuffer input){
        if (MainActivity.GPU || inferenceEngine==null)
            update();
        risorse.output[id].rewind();
        s = System.currentTimeMillis();

        outputMap = new HashMap<>();
        outputMap.put(inferenceEngine.getOutputIndex(outputNodes.get(Utils.Scale.HALF)), risorse.output[id]);
        inputObjectArray[0] = input;

        this.inferenceEngine.runForMultipleInputsOutputs(inputObjectArray, outputMap);
        if (monitor.useAvgInferenceTime()) {
            risorse.calculateAvgInference(System.currentTimeMillis() - s);
            monitor.releaseAvgInferenceTime();
        }
        Log.d(TAG, "T" + Thread.currentThread().getId() + " M" + id + " Inference Timecost: " + (System.currentTimeMillis() - s) + " ms.");
        risorse.output[id].rewind();
        for (int i=0; i<risorse.output[id].capacity() / Float.BYTES; i++)
            risorse.outputFloat[id][i] = risorse.output[id].getFloat();
        return risorse.outputFloat[id];
    }


    public MappedByteBuffer loadModelFile(Context context, String file) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(file);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

}
