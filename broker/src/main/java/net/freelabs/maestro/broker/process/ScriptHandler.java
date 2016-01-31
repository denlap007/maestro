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
import net.freelabs.maestro.core.generated.Script;
import net.freelabs.maestro.core.generated.Scripts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides methods to handle the scripts for the main process and
 * other processes.
 */
public final class ScriptHandler {

    private final Scripts scripts;

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ScriptHandler.class);

    /**
     * Constructor.
     *
     * @param scripts a {@link Scripts Scripts} object with all the info for the
     * scripts to run.
     */
    public ScriptHandler(Scripts scripts) {
        this.scripts = scripts;
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
        boolean fileExists = Files.exists(Paths.get(scripts.getEntrypoint().getPath()));
        boolean isDir = Files.isDirectory(Paths.get(scripts.getEntrypoint().getPath()));

        if (fileExists && isDir) {
            LOG.error("No entrypoint script specified!");
        } else if (!fileExists) {
            LOG.error("No entrypoint script specified!");
        }

        return (fileExists && !isDir);
    }

    public boolean isScriptOk(Script script) {
        boolean fileExists = Files.exists(Paths.get(script.getPath()));
        boolean isDir = Files.isDirectory(Paths.get(script.getPath()));

        if (fileExists && isDir) {
            LOG.error("No script specified for process!");
        } else if (!fileExists) {
            LOG.error("No script specified for process!");
        }

        return (fileExists && !isDir);
    }

    /**
     *
     * @return the arguments of the entrypoint script.
     */
    public List<String> getEntrypointArgs() {
        return scripts.getEntrypoint().getArgs();
    }

    /**
     *
     * @return the path of the entrypoint script.
     */
    public String getEntrypointPath() {
        return scripts.getEntrypoint().getPath();

    }

    public List<Script> getPostEntrypointScirpts() {
        return scripts.getPostEntrypoint();

    }

}
