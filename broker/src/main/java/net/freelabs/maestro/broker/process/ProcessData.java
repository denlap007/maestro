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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * <p>
 * Class that handles all the configuration for the initialization and run of a
 * new process.
 * <p>
 * The class provides the basic structure of a data object for a new process.
 * <p>
 * Subclasses should implement extra functionality.
 */
public class ProcessData {

    /**
     * A resource to execute.
     */
    private final Resource res;
    /**
     * The environment of the process, which includes the necessary environment
     * for the main process to initialize and the environment from the required
     * services.
     */
    private final Map<String, String> environment;

    /**
     * Constructor.
     *
     * @param res a resource to execute.
     * @param env the environment of the process.
     */
    public ProcessData(Resource res, Map<String, String> env) {
        this.res = res;
        if (env == null){
             environment = new HashMap<>();
        }else{
            environment = env;
        }
    }

    // Getters - Setters
    public Resource getRes() {
        return res;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    /**
     * Gets the command and arguments of a resource, in order to be used for a
     * new process initialization.
     *
     * @return a list with the command and arguments of the resource.
     */
    public List<String> getCmdArgs() {
        return res.getResCmdArgs();
    }

    /**
     * <p>
     * Returns the resource description. The resource description is the path of
     * the script or the command that this resource represents. Used mainly for
     * logging purposes.
     * <p>
     * If the resource is not initialized an empty string is returned.
     *
     * @return the description of the resource.
     */
    public String getResDescription() {
        return res.getDescription();
    }
}
