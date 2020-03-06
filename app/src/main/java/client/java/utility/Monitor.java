package client.java.utility;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Monitor {

    private CommonResources resources;

    private Lock newInferenceLock;
    private Condition newInferenceCondition;
    private boolean newInferenceFree = true;
    private boolean[] inferenceFree;

    private Lock listLock;
    private Condition listCondition;
    private boolean listFree = true;

    private Lock fpsLock;
    private Condition fpsCondition;
    private boolean fpsFree = true;

    private Lock avgInferenceTimeLock;
    private Condition avgInferenceTimeCondition;
    private boolean avgInferenceTimeFree = true;

    public Monitor (CommonResources r) {
        resources = r;

        newInferenceLock = new ReentrantLock();
        listLock = new ReentrantLock();
        fpsLock = new ReentrantLock();
        avgInferenceTimeLock = new ReentrantLock();

        newInferenceCondition = newInferenceLock.newCondition();
        listCondition = listLock.newCondition();
        fpsCondition = fpsLock.newCondition();
        avgInferenceTimeCondition = avgInferenceTimeLock.newCondition();

        inferenceFree = new boolean[resources.getFrameConcorrenti()];
        for (int i=0; i<resources.getFrameConcorrenti(); i++)
            inferenceFree[i] = true;
    }

    public int startNewInference() {
        int index = -1;
        newInferenceLock.lock();
        while (newInferenceFree == false) {
            try {
                newInferenceCondition.await();
            } catch (InterruptedException e) {
                newInferenceLock.unlock();
                return index;
            }
        }
        newInferenceFree = false;
        for (int i = 0; index==-1 && i<resources.getFrameConcorrenti(); i++) {
            if (inferenceFree[i]) {
                inferenceFree[i] = false;
                index = i;
            }
        }
        newInferenceFree = true;
        newInferenceCondition.signalAll();
        newInferenceLock.unlock();
        return index;
    }

    public void finishInference(int i) {
        newInferenceLock.lock();
        inferenceFree[i] = true;
        newInferenceCondition.signalAll();
        newInferenceLock.unlock();
    }

    public boolean useList() {
        listLock.lock();
        while (listFree == false) {
            try {
                listCondition.await();
            } catch (InterruptedException e) {
                listLock.unlock();
                return false;
            }
        }
        listFree = false;
        listLock.unlock();
        return true;
    }

    public void releaseList() {
        listLock.lock();
        listFree = true;
        listCondition.signalAll();
        listLock.unlock();
    }

    public boolean useFps() {
        fpsLock.lock();
        while (fpsFree == false) {
            try {
                fpsCondition.await();
            } catch (InterruptedException e) {
                fpsLock.unlock();
                return false;
            }
        }
        fpsFree = false;
        fpsLock.unlock();
        return true;
    }

    public void releaseFps() {
        fpsLock.lock();
        fpsFree = true;
        fpsCondition.signalAll();
        fpsLock.unlock();
    }

    public boolean useAvgInferenceTime() {
        avgInferenceTimeLock.lock();
        while (avgInferenceTimeFree == false) {
            try {
                avgInferenceTimeCondition.await();
            } catch (InterruptedException e) {
                avgInferenceTimeLock.unlock();
                return false;
            }
        }
        avgInferenceTimeFree = false;
        avgInferenceTimeLock.unlock();
        return true;
    }

    public void releaseAvgInferenceTime() {
        avgInferenceTimeLock.lock();
        avgInferenceTimeFree = true;
        avgInferenceTimeCondition.signalAll();
        avgInferenceTimeLock.unlock();
    }
}
