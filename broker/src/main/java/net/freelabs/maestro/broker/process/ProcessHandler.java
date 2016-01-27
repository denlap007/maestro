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
public final class ProcessHandler {

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ProcessHandler.class);
    /**
     * The processs associated with the {@link ProcessHandler ProcessHandler}.
     */
    private Process _proc;
    /**
     * Used to initialize and start a new process.
     */
    private final ProcessBuilder pb;
    /**
     * Monitors the entrypoint process state.
     */
    private final EntrypointProcMon entryProcMon;

    /**
     * Constructor
     * @param procPort the port at which the entrypoint process is listening.
     */
    public ProcessHandler(int procPort) {
        // create a ProcessBuidler to spawn a new process
        pb = createProc();
        // create an entrypoint process monitor
        entryProcMon = new EntrypointProcMon(procPort);
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
     * @param entrypointArgs the arguments to the entrypoint script.
     */
    public void initProc(Map<String, String> outerEnv, String entrypointPath, List<String> entrypointArgs) {
        // get the environmente of the new process
        Map<String, String> env = pb.environment();
        // add the necessary external environment 
        env.putAll(outerEnv);
        // redirect error stream and output stream
        pb.redirectError(Redirect.INHERIT);
        pb.redirectOutput(Redirect.INHERIT);
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
     * <p>
     * Starts the main process.
     * <p>
     * Spawns a new process and runs the entrypoint script. This is the
     * entrypoint process. Waits until initialization is complete and then the
     * main container process is spawned.
     * <p>
     * The method blocks.
     *
     * @return true if main container process started successfully.
     */
    public boolean startProc() {
        try {
            // start the new process
            _proc = pb.start();
            // start the entrypoint monitor
            entryProcMon.start(_proc);
            // if entrypoint initialized, main process is spawned successfully
            if (entryProcMon.isInitialized()) {
                
                //mainProcMon.start(_proc, entryProcMon.getMain_proc_pid());
            }
        } catch (IOException ex) {
            LOG.error("FAILED to start entrypoint process: " + ex);
        }
        return entryProcMon.isInitialized();
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

    /**
     * Checks if the main container process is running.
     *
     * @return true if the main container process is running.
     */
    protected boolean isMainProcRunning() {
        return entryProcMon.isRunning();
    }

    /**
     * Blocks until the main container process stops running.
     */
    public void waitForMainProc() {
        entryProcMon.waitProc();
    }

}
