package net.elytrium.limbofilter.queue;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.elytrium.limbofilter.Settings;

public class ConnectionQueue {

    private volatile Semaphore semaphore;

    public ConnectionQueue() {
        this.semaphore = new Semaphore(Settings.IMP.MAIN.MAX_CONCURRENT_CHECKS, true);
    }

    public void reload() {
        this.semaphore = new Semaphore(Settings.IMP.MAIN.MAX_CONCURRENT_CHECKS, true);
    }

    public boolean acquire() {
        try {
            return this.semaphore.tryAcquire(Settings.IMP.MAIN.QUEUE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release() {
        this.semaphore.release();
    }
}