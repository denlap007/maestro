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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.freelabs.maestro.core.generated.Resource;
import net.freelabs.maestro.core.generated.Script;

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
        environment = env;
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
    public List<String> getResCmdArgs() {
        List<String> cmdArgs = new ArrayList<>();
        if (res != null) {
            if (res.getCmd() != null) {
                if (!res.getCmd().isEmpty()) {
                    // split to tokens using space delimeter
                    String[] args = res.getCmd().split(" ");
                    // convert array to list and add to returnes list 
                    cmdArgs.addAll(Arrays.asList(args));
                } else if (res.getScript() != null) {
                    Script script = res.getScript();
                    // get the script path and args and add to list
                    String path = script.getPath();
                    if (!path.isEmpty()) {
                        List<String> args = script.getArgs();
                        // THE ORDER IS IMPORTANT(!)
                        cmdArgs.add(path);
                        cmdArgs.addAll(args);
                    }
                }
            } else if (res.getScript() != null) {
                Script script = res.getScript();
                // get the script path and args and add to list
                String path = script.getPath();
                if (!path.isEmpty()) {
                    List<String> args = script.getArgs();
                    // THE ORDER IS IMPORTANT(!)
                    cmdArgs.add(path);
                    cmdArgs.addAll(args);
                }
            }
        }
        return cmdArgs;
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
        String desc = "";
        if (res != null) {
            if (res.getCmd() != null) {
                if (!res.getCmd().isEmpty()) {
                    desc = res.getCmd();
                } else if (res.getScript() != null) {
                    desc = res.getScript().getPath();
                }
            } else if (res.getScript() != null) {
                desc = res.getScript().getPath();
            }
        }
        return desc;
    }
}
