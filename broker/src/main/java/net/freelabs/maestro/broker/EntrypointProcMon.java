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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import static net.freelabs.maestro.broker.EntrypointHandler.CONTROL_STRING;

/**
 *
 * Class that provides methods to monitor the entrypoint process.
 */
final class EntrypointProcMon {

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
     * The monitoring process.
     */
    private Process _proc;
    /**
     * Latch that is set when the process is waiting for initialization and
     * releases when initialized or failed to initialize.
     */
    private CountDownLatch initializedSignal;
    /**
     * The pid of the main container spawned.
     */
    private int main_proc_pid = -1;

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(EntrypointProcMon.class);

    /**
     * Constructor.
     */
    public EntrypointProcMon() {
        curState = STATE.NOT_RUNNING;
    }

    /**
     * <p>
     * Starts the entrypoint monitor that will monitor the entrypoint process
     * state.
     * <p>
     * Sets state to RUNNING.
     *
     * @param _proc the process to monitor.
     */
    public void start(Process _proc) {
        this._proc = _proc;
        setRunning(true);
        // start monitoring running
        monProcRun();
        // start monitoring initialization
        monProcInit();

    }

    /**
     * Transition function. Calculates next state based on current state and
     * {@link #initialized initialized}, {@link #running running} values.*
     */
    private synchronized void transition() {
        if (curState == STATE.NOT_RUNNING && running == true) {
            curState = STATE.RUNNING;
        } else if (curState == STATE.RUNNING && initialized == true) {
            curState = STATE.INITIALIZED;
        } else if (curState == STATE.RUNNING && initialized == false) {
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
                break;
            case RUNNING:
                initializedSignal = new CountDownLatch(1);
                break;
            case INITIALIZED:
                initializedSignal.countDown();
                LOG.info("Process initialization complete.");
                break;
            case NOT_INITIALIZED:
                initializedSignal.countDown();
                LOG.error("Process initialization FAILED.");
                break;
            default:
                break;
        }
    }

    /**
     * <p>
     * Monitors if the entrypoint is running.
     * <p>
     * Sets entrypoint process status to not running.
     */
    protected void monProcRun() {
        new Thread(() -> {
            if (_proc != null) {
                try {
                    _proc.waitFor();
                    // set not running status
                    setRunning(false);
                } catch (InterruptedException ex) {
                    LOG.warn("Interruption attempted: {}", ex.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        }
        ).start();
        LOG.info("Started monitoring the entrypoint process.");
    }

    /**
     * <p>
     * Reads the output of {@link #_proc _proc} process and sets the process
     * state to initialized or not.
     * <p>
     * The method reads the output and searches for the {@link #CONTROL_STRING
     * CONTROL_STRING}. If the CONTROL_STRING is found then the process is
     * initialized and will spawn the main container process.
     * <p>
     * By finding the CONTROL_STRING the method extracts also the pid of the
     * main container process that is spawned.
     */
    private void readOutForInit() {
        // create a new thread to read proc output
        new Thread(() -> {
            // set id for logging
            MDC.put("id", "entrypoint");
            // read process output
            BufferedReader inStream = new BufferedReader(new InputStreamReader(_proc.getInputStream()));
            String line;
            Scanner scan = new Scanner(inStream);
            while (scan.hasNextLine()) {
                line = scan.nextLine();
                LOG.info(line);
                // check if process is initialized 
                checkInit(line);
            }
        }
        ).start();
    }

    /**
     * Checks for the initialization {@link #CONTROL_STRING CONTROL_STRING} and
     * if found extracts the main process pid and sets entrypoint process status
     * to INITIALIZED.
     *
     * @param line
     */
    private void checkInit(String line) {
        if (line.contains(CONTROL_STRING)) {
            main_proc_pid = Integer.parseInt(line.substring(line.indexOf("=") + 1, line.length()));
            // set initialized status
            setInitialized(true);
        }
    }

    /**
     * Monitors the process initialization. Waits until initialized.
     */
    protected void monProcInit() {
        LOG.info("Waiting for the process to initialize...");

        // create a thread and read proc output, watch for CONTROL_STRING.
        readOutForInit();

        try {
            initializedSignal.await();
        } catch (InterruptedException ex) {
            LOG.warn("Interruption attempted: {}", ex.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     *
     * @return true if the process is in INITIALIZED state.
     */
    public boolean isInitialized() {
        return curState == STATE.INITIALIZED;
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
     * @return the {@link $main_proc_pid main_proc_pid}
     */
    public int getMain_proc_pid() {
        return main_proc_pid;
    }

}
