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

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that manages process execution.
 */
public final class ProcessManager {

    /**
     * The handler for the main process.
     */
    private MainProcessHandler mainProcHandler;
    /**
     * List of handlers for other processes to be executed before the main
     * process.
     */
    private List<ProcessHandler> preMainProcHandlers;
    /**
     * List of handlers for other processes to be executed after the main
     * process is executed successfully.
     */
    private List<ProcessHandler> postMainProcHandlers;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ProcessManager.class);

    /**
     * <p>
     * Starts executing declared processes.
     * <p>
     * If declared, any process to be run before the main is executed. Then, the
     * main container process is started. If executed successfully the other
     * processes are spawned.
     * <p>
     * The method waits for {@link MainProcessHandler#start() start} method of
     * {@link MainProcessHandler MainProcessHandler} to return.
     * <p>
     * The method blocks.
     */
    public void startProcesses() {
        if (isProcMngrInitialized()) {
            boolean preMainSuccess = true;

            // execute pre-main processes, if any
            if (!preMainProcHandlers.isEmpty()) {
                for (ProcessHandler procHandler : preMainProcHandlers) {
                    preMainSuccess = procHandler.execute();
                    // if there was error exit
                    if (!preMainSuccess) {
                        break;
                    }
                }
            }

            // if preMain procs executed successfully, execute main
            if (preMainSuccess) {
                // execute the main Process
                boolean mainSuccess = mainProcHandler.execute();

                // if main proc execute successfully, execute postMain procs
                if (mainSuccess) {
                    // execute post-main processes, if any
                    if (!postMainProcHandlers.isEmpty()) {
                        postMainProcHandlers.stream().forEach((procHandler) -> {
                            procHandler.execute();
                        });
                    }
                }
            } else {
                LOG.error("Pre-main process execution FAILED. "
                        + "ABORTING main process execution.");
            }

        } else {
            LOG.error("Process Manager CANNOT start: "
                    + "Main Process Handler NOT INITIALIZED properly.");
        }
    }

    /**
     *
     * @return true if {@link ProcessManager ProcessManager} is initialized
     * properly, that is when at least the
     * {@link MainProcessHandler MainProcessHandler} is set.
     */
    private boolean isProcMngrInitialized() {
        return (mainProcHandler != null);
    }

    /**
     * Checks if the main process is running.
     *
     * @return true if the main container process is running.
     */
    protected boolean isMainProcRunning() {
        return mainProcHandler.isMainProcRunning();
    }

    /**
     * Blocks until the main process stops running.
     */
    public void waitForMainProc() {
        mainProcHandler.waitForMainProc();
    }

    /**
     * Set the handler for the main process.
     *
     * @param mainProcHandler the handler for the main process.
     */
    public void setMainProcHandler(MainProcessHandler mainProcHandler) {
        this.mainProcHandler = mainProcHandler;
    }

    /**
     * Set the list of handlers for other processes to be executed after main
     * process executed successfully.
     *
     * @param postMainProcHandlers a list of handlers for other processes.
     */
    public void setPostMainProcHandlers(List<ProcessHandler> postMainProcHandlers) {
        this.postMainProcHandlers = postMainProcHandlers;
    }

    /**
     * Set the list of handlers for other processes to be executed before main
     * process.
     *
     * @param preMainProcHandlers a list of handlers for other processes.
     */
    public void setPreMainProcHandlers(List<ProcessHandler> preMainProcHandlers) {
        this.preMainProcHandlers = preMainProcHandlers;
    }

}
