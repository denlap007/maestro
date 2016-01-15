/*
 * Copyright (C) 2015-2016 Dionysis Lappas <dio@freelabs.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.freelabs.maestro.broker;

import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that holds the main container process state and monitors weather the
 * process is running or not.
 */
public class MainProcMon {

    /**
     * The pid of the main container process.
     */
    private int pid;
    /**
     * The state of the main container process.
     */
    private volatile boolean running;
    /**
     * The process to be monitored. This is not the main container process. It
     * is the process that spawned the main container process.
     */
    private Process _proc;
    /**
     * Latch that is used to wait for the process while running.
     */
    private final CountDownLatch runningSignal = new CountDownLatch(1);
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(MainProcMon.class);

    /**
     * Starts monitoring the process. Sets state to RUNNING.
     *
     * @param _proc the process to monitor.
     * @param pid the pid of the main container process.
     */
    public void start(Process _proc, int pid) {
        this._proc = _proc;
        running = true;
        monRunning();
    }

    /**
     *
     * @return true if the main container process is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * <p>
     * Monitors if the process is running
     * <p>
     * The method waits for the process and when it exits sets state to NOT
     * RUNNING, while releases the {@link #runningSignal runningSingal} latch,
     * in order to notify the threads waiting.
     * <p>
     * The method blocks.
     */
    protected void monRunning() {
        new Thread(() -> {
            if (_proc != null) {
                try {
                    _proc.waitFor();
                    running = false;
                    // notify all waiting threads that process stopped
                    runningSignal.countDown();
                    LOG.warn("Main process stopped.");
                } catch (InterruptedException ex) {
                    LOG.warn("Interruption attempted: {}", ex.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        }
        ).start();
        LOG.info("Started monitoring the main process.");
    }
    /**
     * Sets the main process pid.
     * @param pid 
     */
    public void setPid(int pid) {
        this.pid = pid;
    }
    /**
     * Gets the main process pid.
     * @return 
     */
    public int getPid() {
        return pid;
    }

    /**
     * <p>
     * The method sets the caller into waiting for the main process to stop.
     * <p>
     * The method blocks.
     */
    public void setWaitOnMainProc() {
        try {
            runningSignal.await();
        } catch (InterruptedException ex) {
            LOG.warn("Interruption attempted: {}", ex.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
