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

import net.freelabs.maestro.broker.process.start.MainProcessHandler;
import java.util.List;
import net.freelabs.maestro.broker.process.start.StartGroupHandler;
import net.freelabs.maestro.broker.process.stop.StopGroupHandler;

/**
 *
 * Class that manages process execution.
 */
public final class ProcessManager {

    /**
     * Process manager for processes defined in start section.
     */
    private StartGroupHandler startHandler;
    /**
     * Process manager for processes defined in stop section.
     */
    private StopGroupHandler stopHandler;

    /**
     * Initializes process manager that handles execution of processes defined
     * in start section.
     *
     * @param preHandlers
     * @param postHandlers
     * @param mainHandler
     */
    public void initStartHandler(List<ProcessHandler> preHandlers, List<ProcessHandler> postHandlers, MainProcessHandler mainHandler) {
        startHandler = new StartGroupHandler(preHandlers, postHandlers, mainHandler);
    }

    /**
     * Initializes process manager that handles execution of processes defined
     * in stop section.
     *
     * @param handlers list of handlers for processes to be executed.
     */
    public void initStopHandler(List<ProcessHandler> handlers) {
        stopHandler = new StopGroupHandler(handlers);
    }

    /**
     * Executes processes defined in start section.
     */
    public void exec_start() {
        startHandler.exec_startProcs();
    }

    /**
     * Executes processes defined in stop section.
     */
    public void exec_stop() {
        stopHandler.exec_stopProcs();
    }

    /**
     * Checks if the main process is running.
     *
     * @return true if the main container process is running.
     */
    public boolean isMainProcRunning() {
        return startHandler.isMainProcRunning();
    }

    /**
     * Waits until the main process stops running.
     */
    public void waitForMainProc() {
        startHandler.waitForMainProc();
    }

    /**
     * Checks if handler for stop process group is initialized.
     *
     * @return true if handler for stop process group is initialized.
     */
    public boolean isStopHandlerInit() {
        return stopHandler != null;
    }

    /**
     * Checks if handler for start process group is initialized.
     *
     * @return true if handler for start process group is initialized.
     */
    public boolean isStartHandlerInit() {
        return startHandler != null;
    }
}
