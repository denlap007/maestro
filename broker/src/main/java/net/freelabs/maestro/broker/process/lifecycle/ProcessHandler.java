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
package net.freelabs.maestro.broker.process.lifecycle;

import net.freelabs.maestro.broker.process.ProcessData;

/**
 *
 * <p>
 * Class that provides methods to manage the life-cycle of a new process. The
 * class sets the required structure a {@link ProcessHandler ProcessHandler}
 * must have.
 * <p>
 * Subclasses may implement the required functionality.
 */
public abstract class ProcessHandler {

    /**
     * Holds all the configuration needed for the process handled by the
     * {@link ProcessHandler ProcessHandler}.
     */
    protected ProcessData data;
    
    protected Process _proc;
    
    protected ProcessBuilder pb;
    
    public ProcessHandler(ProcessData data){
        this.data = data;
    }

    /**
     * Creates a {@link ProcessBuilder ProcessBuilder} that will initialize and
     * run a new process.
     */
    protected void create() {
        pb = new ProcessBuilder();
    }

    /**
     * Initializes a new process.
     */
    protected abstract void init();

    /**
     * Starts a new process.
     * @return 
     */
    protected abstract boolean start();

    /**
     * Stops the process handled.
     */
    protected abstract void stop();
    /**
     * Executes chain of commands to create, init and start a new process.
     * @return true if process executed successfully.
     */
    protected final boolean execute(){
        create();    
        
        init();
        
        return start();
    }


}
