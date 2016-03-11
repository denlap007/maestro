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
package net.freelabs.maestro.core.broker;

import com.github.dockerjava.api.command.CreateContainerResponse;

/**
 *
 * Interface that defines methods to control container life-cycle.
 */
public interface ContainerLifecycle {

    /**
     * Creates the necessary configuration for the container to boot.
     * @return a String with key/value pairs in the form key1=value1,
     * key2=value2 e.t.c. representing the boot environment.
     */
    public String createContainerEnv();

    /**
     * Creates a container.
     *
     * @return a {@link CreateContainerResponse CreateContainerResponse} object
     * that will be used to start the created container. NULL if could no create
     * the container.
     */
    public CreateContainerResponse createContainer();

    /**
     * Starts a container.
     *
     * @return the id of the started container. Null if the container failed to
     * start.
     */
    public String startContainer();

    /**
     * Stops a running container.
     *
     * @param con the name or if of the container to stop.
     * @return true if the container stopped.
     */
    public boolean stopContainer(String con);

    /**
     * Restarts a container.
     *
     * @param con the name of id of the container to restart.
     * @return true if the container restarted successfully.
     */
    public boolean restartContainer(String con);

    /**
     * Pulls container image from docker hub.
     *
     * @param img the name of the image to pull.
     */
    public void pullContainerImg(String img);

}
