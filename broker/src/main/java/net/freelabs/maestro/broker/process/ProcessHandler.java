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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * <p>
 * Class that provides methods to manage the life-cycle of a new process. The
 * class sets the required structure a {@link ProcessHandler ProcessHandler}
 * must have.
 * <p>
 * Subclasses may implement the required extra functionality.
 */
public abstract class ProcessHandler {

    /**
     * Holds all the data needed for the process handled by the
     * {@link ProcessHandler ProcessHandler}.
     */
    protected ProcessData data;
    /**
     * The process object associated with the
     * {@link ProcessHandler ProcessHandler}.
     */
    protected Process _proc;
    /**
     * Initialize and starts a new process.
     */
    protected ProcessBuilder pb;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ProcessHandler.class);

    /**
     * Constructor.
     *
     * @param data the object holding all the required data for the new process.
     */
    public ProcessHandler(ProcessData data) {
        this.data = data;
    }

    /**
     * Creates a {@link ProcessBuilder ProcessBuilder} that will be used to
     * initialize and run a new process.
     */
    protected void create() {
        pb = new ProcessBuilder();
    }

    /**
     * <p>
     * Initializes a new process.
     * <p>
     * The method acts on data found on {@link ProcessData ProcessData} object.
     * Initializes the environment and sets the script path and possible script
     * args.
     * <p>
     * Subclasses should Override this method to implements extra functionality.
     */
    protected void init() {
        // get the environmente of the new process
        Map<String, String> env = pb.environment();
        // add the necessary external environment defined in ProcessData obj
        env.putAll(data.getEnvironment());
        /* set process command and arguments. The process will execute the script. 
        The script may specify possible external arguments. Add all to list.*/
        List<String> procCmdArgs = new ArrayList<>();
        // add the script path and args
        procCmdArgs.add(data.getScriptPath());
        procCmdArgs.addAll(data.getScriptArgs());
        // set command and arguments
        pb.command(procCmdArgs);
    }

    /**
     * Starts a new process.
     *
     * @return
     */
    protected abstract boolean start();

    /**
     * Stops the process handled.
     */
    public abstract void stop();

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
     * @return true if process executed successfully. The process executed
     * successfully if {@link #start() start} method returned true.
     * Consequently, the return value matches the one from start method.
     */
    public final boolean execute() {
        create();

        init();

        return start(); 
    }

    /**
     * <p>
     * Returns the process pid through reflection.
     * <p>
     * If there is a {@link SecurityManager SecurityManager} installed, this
     * method may throw an exception.
     *
     * @return the process pid.
     */
    public final int getProcPid() {
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

}
