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
import java.util.List;
import net.freelabs.maestro.core.generated.RunElem;
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
    private final List<Resource> preMainRes = new ArrayList<>();

    /**
     * A list with the resources to execute after the main resource.
     */
    private final List<Resource> postMainRes = new ArrayList<>();
    /**
     * The main resource to execute.
     */
    private Resource mainRes;

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ResourceHandler.class);

    /**
     * Constructor.
     *
     * @param preMain
     * @param postMain
     * @param main
     */
    public ResourceHandler(List<RunElem> preMain, List<RunElem> postMain, String main) {
        initResources(preMain, postMain, main);
    }

    /**
     * Initializes the {@link #preMainRes preMainRes} list, {@link #postMainRes
     * postMainRes} list and {@link #mainRes mainRes}.
     */
    private void initResources(List<RunElem> preMain, List<RunElem> postMain, String main) {
        // create preMain resource list
        preMain.stream().forEach((elem) -> {

            Resource res = new Resource(elem.getValue(), elem.isWait(), elem.isAbortOnFail());
            preMainRes.add(res);
        });
        // create postMain resource list
        postMain.stream().forEach((elem) -> {
            Resource res = new Resource(elem.getValue(), elem.isWait(), elem.isAbortOnFail());
            postMainRes.add(res);
        });
        // create main resource
        mainRes = new Resource(main, true, true);
    }

    /**
     * Checks if a resource is defined and not empty.
     *
     * @param res the resource to check.
     * @return true if the resource is defined and not empty.
     */
    public boolean isResourceOk(Resource res) {
        boolean ok = false;

        if (res != null) {
            if (res.getRes().isEmpty()) {
                LOG.error("Resource defined but not initialized. Empty.");
            } else {
                ok = true;
            }
        } else {
            LOG.error("Resource NOT defined.");
        }
        return ok;
    }

    /**
     *
     * @return resources to run before the {@link #mainRes main resource}.
     */
    public List<Resource> getPreMainRes() {
        return preMainRes;
    }

    /**
     *
     * @return resources to run after {@link #mainRes main resource}..
     */
    public List<Resource> getPostMainRes() {
        return postMainRes;
    }

    /**
     *
     * @return the {@link #mainRes main resource} to run.
     */
    public Resource getMainRes() {
        return mainRes;
    }
}
