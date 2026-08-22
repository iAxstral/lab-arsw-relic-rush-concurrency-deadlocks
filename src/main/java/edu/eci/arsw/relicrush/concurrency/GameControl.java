package edu.eci.arsw.relicrush.concurrency;

public class GameControl {
    private boolean paused = false;
    private volatile boolean stopped = false;

    public synchronized void pause() {
        paused = true;
    }

    public synchronized void resume() {
        paused = false;
        notifyAll();
    }

    public synchronized void awaitIfPaused() throws InterruptedException {
        while (paused && !stopped) {
            wait();
        }
    }

    public void stop() {
        stopped = true;
        synchronized (this) {
            notifyAll();
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    public synchronized boolean isPaused() {
        return paused;
    }
}