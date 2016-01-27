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
package net.freelabs.maestro.broker.process;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides methods to monitor the entrypoint process.
 */
public final class EntrypointProcMon {

    /**
     * The possible states of the entrypoint process.
     */
    private enum STATE {
        NOT_RUNNING, RUNNING, INITIALIZED, NOT_INITIALIZED
    };
    /**
     * The current state of the entrypoint process.
     */
    private volatile STATE curState;
    /**
     * Indicates the process is running.
     */
    private volatile boolean running;
    /**
     * Indicates the process is initialized.
     */
    private volatile boolean initialized;
    /**
     * The entrypoint process.
     */
    private Process _proc;
    /**
     * Latch that is set when the process is waiting for initialization and
     * releases when initialized or failed to initialize.
     */
    private CountDownLatch initSignal;
    /**
     * Time to wait for initialization before aborting process.
     */
    private static final int INIT_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(2);
    /**
     * Latch that is used to wait for the process while running.
     */
    private CountDownLatch runningSignal;
    /**
     * Holds the IP and port where the process is running. Used to make a socket
     * connection and check process initialization. The host is the localhost.
     */
    private final InetSocketAddress isa;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(EntrypointProcMon.class);
    /**
     * The port at which the entrypoint process is listening.
     */
    private final int procPort;

    /**
     * Constructor.
     *
     * @param procPort the port at which the process is listening.
     */
    public EntrypointProcMon(int procPort) {
        curState = STATE.NOT_RUNNING;
        this.procPort = procPort;
        // create the host port socketAddress object. Host is localhost
        isa = new InetSocketAddress(InetAddress.getLoopbackAddress(), procPort);
    }

    /**
     * <p>
     * Starts the entrypoint monitor that will monitor the entrypoint process
     * state.
     * <p>
     * Sets state to RUNNING.
     * <p>
     * Starts monitoring the running and initialization state. Waits until
     * initialization is complete.
     * <p>
     * The method blocks.
     *
     * @param _proc the process to monitor.
     */
    public void start(Process _proc) {
        this._proc = _proc;
        setRunning(true);
        // start monitoring running
        monProcRun();
        // start monitoring initialization
        waitProcInit();
    }

    /**
     * Transition function. Calculates next state based on current state and
     * {@link #initialized initialized}, {@link #running running} values.*
     */
    private synchronized void transition() {
        if (curState == STATE.NOT_RUNNING && running == true) {
            curState = STATE.RUNNING;
        } else if (curState == STATE.RUNNING && initialized == true && running == true) {
            curState = STATE.INITIALIZED;
        } else if (curState == STATE.RUNNING && initialized == true && running == false) {
            curState = STATE.NOT_RUNNING;
        } else if (curState == STATE.RUNNING && initialized == false && running == false) {
            curState = STATE.NOT_RUNNING;
        } else if (curState == STATE.RUNNING && initialized == false && running == true) {
            curState = STATE.NOT_INITIALIZED;
        } else if (curState == STATE.INITIALIZED && running == false) {
            curState = STATE.NOT_RUNNING;
        } else if (curState == STATE.NOT_INITIALIZED && running == false) {
            curState = STATE.NOT_RUNNING;
        }
    }

    /**
     * Implements the logic that needs to be executed on various states.
     */
    private void action() {
        switch (curState) {
            case NOT_RUNNING:
                LOG.warn("Entrypoint process STOPPED.");
                runningSignal.countDown();
                initSignal.countDown();
                break;
            case RUNNING:
                LOG.info("STARTED entrypoint process.");
                runningSignal = new CountDownLatch(1);
                initSignal = new CountDownLatch(1);
                LOG.info("Waiting for the process to initialize...");
                break;
            case INITIALIZED:
                LOG.info("Process initialization complete.");
                initSignal.countDown();
                break;
            case NOT_INITIALIZED:
                LOG.error("Process initialization FAILED.");
                initSignal.countDown();
                break;
            default:
                break;
        }
    }

    /**
     * <p>
     * Monitors if the entrypoint is running.
     * <p>
     * Sets entrypoint process status to not running when then process stops.
     * <p>
     * Logic is executed on a new thread, consequently the method doesn't block.
     */
    protected void monProcRun() {
        new Thread(() -> {
            if (_proc != null) {
                try {
                    _proc.waitFor();
                    // set not running status
                    setRunning(false);
                } catch (InterruptedException ex) {
                    LOG.warn("Thread interrupted. Stopping: {}", ex.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        }
        ).start();
    }

    /**
     * <p>
     * Monitors the process initialization. Waits until initialized.
     * <p>
     * The method blocks.
     */
    public void waitProcInit() {
        // create new thread, check initialization condition and set init status
        checkInit();
        try {
            // block and wait until initialization status changes
            initSignal.await();
        } catch (InterruptedException ex) {
            LOG.warn("Interruption attempted: {}", ex.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * <p>
     * Checks the initialization condition and sets the init process status.
     * <p>
     * A new thread is created which handles the initialization condition. In
     * order to determine if the process is initialized and ready a
     * {@link java.net.Socket socket} is created and tries to make a connection,
     * within a {@link #INIT_TIMEOUT timeout} limit as long as the process is
     * running. If the connection succeeds, the process is set initialized. If
     * not, the connection is re-tried until timed out. If the connection times
     * out the process is set not initialized.
     * <p>
     */
    private void checkInit() {
        new Thread(() -> {
            // create a socket 
            Socket client = new Socket();
            long start = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
            long end = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
            while (running) {
                if (end - start < INIT_TIMEOUT) {
                    try {
                        // try to connect 
                        client.connect(isa, INIT_TIMEOUT);
                        // connection succeeded so set process status to initialized
                        setInitialized(true);
                        break;
                    } catch (SocketTimeoutException ex) {
                        LOG.error("Process initialization TIMEOUT!");
                        setInitialized(false);
                        break;
                    } catch (SocketException ex) {
                        // create a new socket as the socket is now closed.
                        client = new Socket();
                    } catch (IOException ex) {
                        // catch the exception to allow loop to continue                        
                    }
                    LOG.warn("Waiting server: {} on port: {}", isa.getHostName(), String.valueOf(procPort));
                    try {
                        TimeUnit.SECONDS.sleep(2);
                    } catch (InterruptedException ex1) {
                        LOG.warn("Interruption attempted: {}", ex1.getMessage());
                        Thread.currentThread().interrupt();
                    }
                } else {
                    LOG.error("Process initialization TIMEOUT!");
                    // set process status to NOT initialized
                    setInitialized(false);
                    break;
                }
                end = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
            }// end while
        }).start();
    }

    /**
     *
     * @return true if the process is in INITIALIZED state.
     */
    public boolean isInitialized() {
        return curState == STATE.INITIALIZED;
    }

    /**
     *
     * @return true if the entrypoint process is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Sets {@link #initialized initialized} field, calls {@link #transition()
     * transition} to calculate next state and {@link #action() action} to apply
     * any actions based on the process state.
     *
     * @param initialized true if the process is initialized.
     */
    private void setInitialized(boolean initialized) {
        this.initialized = initialized;
        transition();
        action();
    }

    /**
     * Sets {@link #running running} field, calls {@link #transition()
     * transition} to calculate next state and {@link #action() action} to apply
     * any actions based on the process state.
     *
     * @param running true if the process is running.
     */
    private void setRunning(boolean running) {
        this.running = running;
        transition();
        action();
    }

    /**
     * <p>
     * The method sets the caller into waiting for the entrypoint process to
     * stop.
     * <p>
     * The method blocks.
     */
    public void waitProc() {
        try {
            if (running) {
                runningSignal.await();
            } else {
                LOG.error("Cannot wait for process. Process is NOT RUNNING.");
            }
        } catch (InterruptedException ex) {
            LOG.warn("Interruption attempted: {}", ex.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
