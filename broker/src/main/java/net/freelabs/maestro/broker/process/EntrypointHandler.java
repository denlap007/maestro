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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides methods to handle the entrypoint script for the main
 * process.
 */
public final class EntrypointHandler {

    /**
     * The path of the entrypoint script.
     */
    public final String entrypointPath;
    /**
     * Possible arguments with the entrypoint script.
     */
    private final List<String> entrypointArgs;
    /**
     * A Logger object.
     */
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(EntrypointHandler.class);

    /**
     * Constructor.
     *
     * @param entrypointPath the path of the entrypoint script.
     * @param entrypointArgs possible arguments with the entrypoint script.
     */
    public EntrypointHandler(String entrypointPath, List<String> entrypointArgs) {
        this.entrypointPath = entrypointPath;
        this.entrypointArgs = entrypointArgs;
    }

    /**
     * <p>
     * Checks if the entrypoint script exists and if so if it is a file.
     * <p>
     * This method is used for error checking for the entrypoint script.
     *
     * @return true if entrypoint script exists and is a file.
     */
    public boolean isEntrypointOk() {
        boolean fileExists = Files.exists(Paths.get(entrypointPath));
        boolean isDir = Files.isDirectory(Paths.get(entrypointPath));

        if (fileExists && isDir) {
            LOG.error("No entrypoint script specified!");
        } else if (!fileExists) {
            LOG.error("No entrypoint script specified!");
        }

        return (fileExists && !isDir);
    }

    /**
     *
     * @return the arguments of the entrypoint script.
     */
    public List<String> getEntrypointArgs() {
        return entrypointArgs;
    }

    /**
     *
     * @return the path of the entrypoint script.
     */
    public String getEntrypointPath() {
        return entrypointPath;
    }

}
