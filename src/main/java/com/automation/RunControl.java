package com.automation;

/**
 * Điều khiển Tạm dừng / Tiếp tục / Dừng khẩn cấp cho luồng automation.
 * Mọi thao tác chờ trong tool đều đi qua {@link #sleep(long)} và {@link #checkpoint()}
 * nên nút Stop có hiệu lực gần như tức thì.
 */
public class RunControl {

    private final Object lock = new Object();
    private volatile boolean stopRequested;
    private volatile boolean paused;
    private volatile Thread worker;
    private long pausedTotalMs;
    private long pauseStartedAt;

    public void bindWorker(Thread thread) {
        this.worker = thread;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    public boolean isPaused() {
        return paused;
    }

    public void requestStop() {
        stopRequested = true;
        synchronized (lock) {
            if (paused) {
                pausedTotalMs += System.currentTimeMillis() - pauseStartedAt;
            }
            paused = false;
            lock.notifyAll();
        }
        Thread w = worker;
        if (w != null) {
            w.interrupt();
        }
    }

    public void setPaused(boolean pause) {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            if (pause && !paused) {
                pauseStartedAt = now;
            } else if (!pause && paused) {
                pausedTotalMs += now - pauseStartedAt;
            }
            paused = pause;
            lock.notifyAll();
        }
    }

    /** Tổng thời gian đã tạm dừng (dùng để không tính thời gian pause vào timeout chờ phần tử). */
    public long totalPausedMillis() {
        synchronized (lock) {
            return pausedTotalMs + (paused ? System.currentTimeMillis() - pauseStartedAt : 0);
        }
    }

    /** Điểm kiểm tra: ném lỗi nếu đã bấm Stop, đứng chờ nếu đang Tạm dừng. */
    public void checkpoint() {
        if (stopRequested) {
            throw new AutomationStoppedException();
        }
        synchronized (lock) {
            while (paused && !stopRequested) {
                try {
                    lock.wait(200);
                } catch (InterruptedException e) {
                    if (stopRequested) {
                        throw new AutomationStoppedException();
                    }
                }
            }
        }
        if (stopRequested) {
            throw new AutomationStoppedException();
        }
    }

    /** Ngủ có thể ngắt bởi Stop; thời gian Tạm dừng không bị tính vào. */
    public void sleep(long millis) {
        long remaining = millis;
        while (remaining > 0) {
            checkpoint();
            long slice = Math.min(100, remaining);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException e) {
                if (stopRequested) {
                    throw new AutomationStoppedException();
                }
            }
            remaining -= slice;
        }
        checkpoint();
    }
}

