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
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 *
 * Class that provides methods to initialize, start, run and monitor the main
 * container process.
 */
final class ProcessHandler {

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ProcessHandler.class);
    /**
     * The processs associated with the {@link ProcessHandler ProcessHandler}.
     */
    private Process _proc;

    private final ProcessBuilder pb;

    private volatile int main_proc_pid;

    private static final String CONTROL_STRING = "_proc_pid=";

    private final ProcMon procMon;

    /**
     * Constructor
     */
    public ProcessHandler() {
        // create a ProcessBuidler to spawn a new process
        pb = createProc();
        // create a process monitor object to minitor the state of the process
        procMon = new ProcMon();
    }

    /**
     * Returns a new {@link ProcessHandler ProcessHandler} to initialize a
     * process
     *
     * @return a {@link ProcessHandler ProcessHandler} object.
     */
    private ProcessBuilder createProc() {
        ProcessBuilder procBuild = new ProcessBuilder();
        return procBuild;
    }

    /**
     * <p>
     * Initializes a new process.
     * <p>
     * The method adds an external environment to the process, redirects the
     * stderr stream to the parent process, and then executes the entrypoint
     * script.
     *
     * @param outerEnv the environment to add to the new process.
     * @param entrypointPath the path of the entrypoint script to execute.
     */
    protected void initProc(Map<String, String> outerEnv, String entrypointPath, List<String> entrypointArgs) {
        // get the environmente of the new process
        Map<String, String> env = pb.environment();
        // add the necessary external environment 
        env.putAll(outerEnv);
        // redirect error stream
        pb.redirectError();
        /* set process command and arguments. The process will execute the entrypoint 
        script. The entrypoint may specify possible arguments. Add all to list.*/
        List<String> procCmdArgs = new ArrayList<>();
        // add the entrypoint path and args
        procCmdArgs.add(entrypointPath);
        procCmdArgs.addAll(entrypointArgs);
        // set command and arguments
        pb.command(procCmdArgs);
    }

    /**
     * Starts a new process.
     *
     * @return true if process started successfully.
     */
    protected void startProc() {
        try {
            // start the new process, set to started
            _proc = pb.start();
            procMon._started = true;
            LOG.info("STARTING Main process.");
            // start monitoring the process, set to running
            monProc();
            // read proc output
            readProcOutput();
        } catch (IOException ex) {
            LOG.error("FAILED to start main process: " + ex);
        }
    }

    /**
     * <p>
     * Reads the output of {@link #_proc _proc} process and sets the process
     * state to initialized if it initializes successfully. 
     * <p>
     * The method reads the output and searches for the {@link #CONTROL_STRING
     * CONTROL_STRING}. If the CONTROL_STRING is found then the process is
     * initialized and will spawn the main container process.
     * <p>
     * By finding the CONTROL_STRING the method extracts also the pid of the
     * main container process that is spawned.
     */
    private void readProcOutput() {
        // create a new thread to read proc output
        Thread t = new Thread() {
            @Override
            public void run() {
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
                    if (line.contains(CONTROL_STRING)) {
                        main_proc_pid = Integer.parseInt(line.substring(line.indexOf("=") + 1, line.length()));
                        procMon._initialized = true;
                    }
                }
            }
        };
        // start the thread
        t.start();
    }

    /**
     * <p>
     * Waits until the process is initialized.
     * <p>
     * The method creates a new thread that monitors the process initialization.
     * When the process is initialized, it interrupts the main execution thread
     * that is waiting to be notified of the event.
     */
    protected void waitForInitialization() {
        LOG.info("Waiting for the process to initialize...");
        // get the current executing thread
        Thread waitingThread = Thread.currentThread();
        // create a thread to monitor if the process is initialized
        Thread monThread = new Thread() {
            @Override
            public void run() {
                while (true) {
                    if (procMon._initialized) {
                        break;
                    }
                }
                // interrupt the thread that is waiting for the initialization
                waitingThread.interrupt();
            }
        };

        monThread.start();

        if (_proc != null) {
            try {
                _proc.waitFor();
            } catch (InterruptedException ex) {
                LOG.warn("Thread interrupted. Process initialization complete.");
                Thread.interrupted();
            }
        }
    }

    /**
     * <p>
     * Waits for the process to finish.
     * <p>
     * The method blocks until the process has finished. Any interruption
     * attempted is logged and ignored.
     *
     * @return the error code returned by the process execution.
     */
    protected int waitForProc() {
        int errCode = -1;
        if (_proc != null) {
            try {
                errCode = _proc.waitFor();
            } catch (InterruptedException ex) {
                LOG.warn("Interruption attempted: " + ex);
                Thread.currentThread().interrupt();
            }
        }
        return errCode;
    }

    /**
     * <p>
     * Monitors the running process.
     * <p>
     * The process status may be obtained by invoking the {@link ProcMon#isRunning()
     * isRunning} method. If the process is running the method will return true.
     *
     * @return a {@link ProcMon ProcMon} object.
     */
    protected void monProc() {
        // start in a new thread
        Thread t = new Thread(procMon);
        t.start();
        LOG.info("Started monitoring the main process.");
    }

    /**
     * Class that is used to monitor a process's state.
     */
    protected final class ProcMon implements Runnable {

        /**
         * Indicates if the process has started or not.
         */
        private volatile boolean _started;
        /**
         * Indicated if the process is initialized or not.
         */
        private volatile boolean _initialized;
        /**
         * Indicated if the process is running or not.
         */
        private volatile boolean _running;
        /**
         * Holds the process error code.
         */
        private volatile int errCode;

        @Override
        public void run() {
            _running = true;
            errCode = waitForProc();
            _running = false;
        }

        // Getters
        protected int getErrCode() {
            return errCode;
        }
    }

    /**
     * Returns the process's pid through reflection.
     *
     * @return the process pid.
     */
    private int getProcPid() {
        int pid = -1;

        try {
            Field pidField = _proc.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            pid = pidField.getInt(_proc);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            LOG.error("Something went wrong: " + ex);
        }

        return pid;
    }

    // Getters
    public boolean isProcInitialized() {
        return procMon._initialized;
    }

    protected boolean isProcRunning() {
        return procMon._running;
    }

    protected boolean hasProcStarted() {
        return procMon._started;
    }

}
