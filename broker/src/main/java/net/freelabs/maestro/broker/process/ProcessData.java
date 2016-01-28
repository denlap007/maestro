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
import java.util.Map;

/**
 *
 * <p>
 * Class that handles all the configuration for the initialization and run of a
 * new process.
 * <p>
 * The class provides the basic structure of a context object for a new process.
 * <p>
 * Subclasses should define extra functionality.
 */
public class ProcessData {

    /**
     * The path of the script.
     */
    private final String scriptPath;
    /**
     * The arguments, if any, to the script.
     */
    private final List<String> scriptArgs;
    /**
     * The environment of the process, which includes the necessary environment
     * for the main process to initialize and the environment from the required
     * services.
     */
    private final Map<String, String> environment;

    /**
     * Constructor.
     *
     * @param scriptPath the path of the script to execute.
     * @param scriptArgs the arguments, if any, to the script.
     * @param env the environment of the process.
     */
    public ProcessData(String scriptPath, List<String> scriptArgs, Map<String, String> env) {
        this.scriptPath = scriptPath;
        this.scriptArgs = scriptArgs;
        environment = env;
    }

    // Getters - Setters


    public String getScriptPath() {
        return scriptPath;
    }

    public List<String> getScriptArgs() {
        return scriptArgs;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

}
