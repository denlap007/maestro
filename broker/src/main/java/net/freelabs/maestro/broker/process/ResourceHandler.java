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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import net.freelabs.maestro.core.generated.Resource;
import net.freelabs.maestro.core.generated.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides methods to handle all the resources to run.
 */
public final class ResourceHandler {

    /**
     * A list with the resources to execute before the main resource.
     */
    private final List<Resource> preMainRes;

    /**
     * A list with the resources to execute after the main resource.
     */
    private final List<Resource> postMainRes;
    /**
     * The main resource to execute.
     */
    private final Resource mainRes;

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ResourceHandler.class);

    /**
     * Constructor.
     *
     * @param preMainRes a list with the resources to execute before the main
     * resource.
     * @param postMainRes a list with the resources to execute after the main
     * resource.
     * @param mainRes the main resource to execute.
     */
    public ResourceHandler(List<Resource> preMainRes, List<Resource> postMainRes, Resource mainRes) {
        this.preMainRes = preMainRes;
        this.postMainRes = postMainRes;
        this.mainRes = mainRes;
    }

    /**
     * Checks if script file path exists and it is a file.
     *
     * @param script the script to check.
     * @return true if script path exists and it points to a file.
     */
    private boolean isScriptOk(Script script) {
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
     * <p>
     * Checks if a resource is properly initialized.
     * <p>
     * A resource is initialized if a script or command is set.
     * <p>
     * If the resource is a script, it must also have a valid file path and be a
     * file.
     *
     * @param res the resource to check.
     * @return true if the resource satisfies the restrictions.
     */
    public boolean isResourceOk(Resource res) {
        boolean set = false;
        if (res != null) {
            if (res.getCmd() != null) {
                if (!res.getCmd().isEmpty()) {
                    set = true;
                } else if (res.getScript() != null) {
                    if (!res.getScript().getPath().isEmpty()) {
                        set = isScriptOk(res.getScript());
                    }else{
                        LOG.error("No script or cmd specified for process!");
                    }
                } else {
                    LOG.error("No script or cmd specified for process!");
                }
            } else if (res.getScript() != null) {
                if (!res.getScript().getPath().isEmpty()) {
                    set = isScriptOk(res.getScript());
                } else {
                    LOG.error("No script or cmd specified for process!");
                }
            }
        } else {
            LOG.error("Resource not intialized for process!");
        }
        return set;
    }
    
    // Getters
    public List<Resource> getPostMainRes() {
        return postMainRes;
    }

    public List<Resource> getPreMainRes() {
        return preMainRes;
    }

    /**
     * <p>
     * Gets a {@link Resource resource} to run before the main resource.
     * <p>
     * Once the resource is returned it is removed from the list.
     * <p>
     * The method gets all the resources of the list exhaustively.
     *
     * @return a resource from {@link #preMainRes preMainRes} list. Null if
     * there are no more elements.
     *
    public Resource getPreMainRes() {
        Resource res = null;
        // get an iterator for the collection
        Iterator<Resource> resIter = preMainRes.iterator();
        // if there are elements in the collection
        if (resIter.hasNext()) {
            // get a resource
            res = resIter.next();
            // remove from the collection
            resIter.remove();
        }
        return res;
    }

    /**
     * <p>
     * Gets a {@link Resource resource} to run after the main resource.
     * <p>
     * Once the resource is returned it is removed from the list.
     * <p>
     * The method gets all the resources of the list exhaustively.
     *
     * @return a resource from {@link #postMainRes postMainRes} list. Null if
     * there are no more elements.
     *
    public Resource getPostMainRes() {
        Resource res = null;
        // get an iterator for the collection
        Iterator<Resource> resIter = postMainRes.iterator();
        // if there are elements in the collection
        if (resIter.hasNext()) {
            // get a resource
            res = resIter.next();
            // remove from the collection
            resIter.remove();
        }
        return res;
    }*/
    
    
    
    

    /**
     *
     * @return the main resource to run.
     */
    public Resource getMainRes() {
        return mainRes;
    }
}
