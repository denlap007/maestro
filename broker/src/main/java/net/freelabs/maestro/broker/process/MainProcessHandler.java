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

/**
 *
 * Class that provides methods to initialize, start, run and monitor the main
 * container process.
 */
public final class MainProcessHandler extends ProcessHandler {

    /**
     * Monitors the main process state.
     */
    private final MainProcMon mainProcMon;
    /**
     * Stores all the initialization data for the process.
     */
    private final MainProcessData mainPData;

    /**
     * Constructor
     *
     * @param pData the object that stores all the data necessary for the
     * process initialization.
     */
    public MainProcessHandler(MainProcessData pData) {
        super(pData);
        mainPData = pData;
        // create the main process monitor
        mainProcMon = new MainProcMon(pData.getProcPort());
    }

    /**
     * <p>
     * Initializes a new process.
     * <p>
     * The method Overrides the {@link ProcessHandler#init() init} method.
     * <p>
     * Redirects the stderr and stdout stream to the parent process.
     *
     */
    @Override
    protected void init() {
        super.init();
        // redirect error stream and output stream
        pb.redirectError(Redirect.INHERIT);
        pb.redirectOutput(Redirect.INHERIT);
    }

    /**
     * <p>
     * Starts the main process.
     * <p>
     * Spawns a new process and runs the script. Waits until initialization is
     * complete.
     * <p>
     * The method blocks.
     *
     * @return true if main process started successfully.
     */
    @Override
    protected boolean start() {
        try {
            // start the new process
            _proc = pb.start();
            // start the main process monitor
            mainProcMon.start(_proc);
        } catch (IOException ex) {
            LOG.error("FAILED to start main process: " + ex);
        }
        // if initialized, main process started successfully
        return mainProcMon.isInitialized();
    }

    @Override
    public void stop() {
        _proc.destroyForcibly();
    }

    /**
     * <p>
     * Checks if {@link MainProcessHandler MainProcessHandler} is initialized
     * properly.
     * <p>
     * In order to be properly initialized, fields {@link #pData pData},
     * {@link #execOnSuccess execOnSuccess} and
     * {@link #execOnFailure execOnFailure} must be set.
     *
     * @return true if handler is properly initialized.
     */
    @Override
    protected boolean isHandlerInitialized() {
        return (pData != null && execOnSuccess != null && execOnFailure != null);
    }

    /**
     * Checks if the main process is running.
     *
     * @return true if the main container process is running.
     */
    public boolean isMainProcRunning() {
        boolean running = mainProcMon.isRunning();
        return running;
    }

    /**
     * Blocks until the main process stops running.
     */
    public void waitForMainProc() {
        mainProcMon.waitProc();
    }
}
