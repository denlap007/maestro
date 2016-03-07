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
package net.freelabs.maestro.broker.process.stop;

import java.util.List;
import net.freelabs.maestro.broker.process.ProcessHandler;

/**
 *
 * Class that manages stop process execution defined in stop
 */
public final class StopGroupHandler {

    /**
     * List with all handlers.
     */
    private final List<ProcessHandler> handlers;
    /**
     * Constructor. 
     * @param handlers list of process handlers of the processes to execute.
     */
    public StopGroupHandler(List<ProcessHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * <p>
     * Starts executing declared processes.
     * <p>
     * Method waits for processes to execute. Processes are executed serially.
     * <p>
     * Method blocks.
     */
    public void exec_stopProcs() {
        handlers.stream().forEach((handler) -> {
            handler.execute();
        });
    }
}
