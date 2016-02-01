/*
 * Copyright (C) 2016 Dionysis Lappas <dio@freelabs.net>
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

/**
 *
 * Class that provides a default implementation for
 * {@link ProcessHandler ProcessHandler}.
 */
public class DefaultProcessHandler extends ProcessHandler {

    public DefaultProcessHandler(ProcessData pData) {
        super(pData);
    }
    /**
     * Indicates that the process started executing.
     */
    private boolean started;

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
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    }

    @Override
    protected boolean start() {
        try {
            // start the new process
            _proc = pb.start();
            // log the event
            LOG.info("Started process: {}", pData.getResDescription());
            // set flag to true
            started = true;
        } catch (IOException ex) {
            LOG.error("FAILED to start process: " + ex);
        }
        return started;
    }

    @Override
    public void stop() {
        _proc.destroyForcibly();
    }

}
