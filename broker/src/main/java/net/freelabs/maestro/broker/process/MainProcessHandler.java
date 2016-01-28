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
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides methods to initialize, start, run and monitor the main
 * container process.
 */
public final class MainProcessHandler {

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(MainProcessHandler.class);
    /**
     * The process associated with the {@link MainProcessHandler MainProcessHandler}.
     */
    private Process _proc;
    /**
     * Used to initialize and start a new process.
     */
    private ProcessBuilder pb;
    /**
     * Monitors the main process state.
     */
    private final MainProcMon entryProcMon;
    /**
     * Stores all the initialization data for the process.
     */
    private final MainProcessData pData;

    /**
     * Constructor
     *
     * @param pData the object that stores all the data necessary for the
     * process initialization.
     */
    public MainProcessHandler(MainProcessData pData) {
        this.pData = pData;
        // create the main process monitor
        entryProcMon = new MainProcMon(pData.getProcPort());
    }

    /**
     * Creates a new {@link MainProcessHandler MainProcessHandler} to initialize a
     * process.
     *
     */
    private void create() {
        pb = new ProcessBuilder();
    }

    /**
     * <p>
     * Initializes a new process.
     * <p>
     * The method adds an external environment to the process, redirects the
     * stderr and stdout stream to the parent process, and then executes the
     * entrypoint script.
     *
     */
    private void init() {
        // get the environmente of the new process
        Map<String, String> env = pb.environment();
        // add the necessary external environment 
        env.putAll(pData.getEnvironment());
        // redirect error stream and output stream
        pb.redirectError(Redirect.INHERIT);
        pb.redirectOutput(Redirect.INHERIT);
        /* set process command and arguments. The process will execute the entrypoint 
        script. The entrypoint may specify possible arguments. Add all to list.*/
        List<String> procCmdArgs = new ArrayList<>();
        // add the entrypoint path and args
        procCmdArgs.add(pData.getScriptPath());
        procCmdArgs.addAll(pData.getScriptArgs());
        // set command and arguments
        pb.command(procCmdArgs);
    }

    /**
     * <p>
     * Starts the main process.
     * <p>
     * Spawns a new process and runs the entrypoint script. Waits until 
     * initialization is complete.
     * <p>
     * The method blocks.
     *
     * @return true if main process started successfully.
     */
    private boolean start() {
        try {
            // start the new process
            _proc = pb.start();
            // start the entrypoint monitor
            entryProcMon.start(_proc);
        } catch (IOException ex) {
            LOG.error("FAILED to start entrypoint process: " + ex);
        }
        // if initialized, main process started successfully
        return entryProcMon.isInitialized();
    }

    /**
     * <p>
     * Executes methods in succession: {@link #create() create}, {@link #init() 
     * init} and {@link #start() start} to create, initialize and start the new
     * process accordingly.
     * <p>
     * The method waits for {@link #start() start} method to return.
     * <p>
     * The method blocks.
     *
     * @return true if process executed successfully.
     */
    public boolean execute() {
        // creates a new ProcessBuilder to initialize a process
        create();
        // initializes the new process
        init();
        // starts the new process
        return start();
    }

    /**
     * Returns the process pid through reflection.
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

    /**
     * Checks if the main process is running.
     *
     * @return true if the main container process is running.
     */
    protected boolean isMainProcRunning() {
        return entryProcMon.isRunning();
    }

    /**
     * Blocks until the main process stops running.
     */
    public void waitForMainProc() {
        entryProcMon.waitProc();
    }

}
