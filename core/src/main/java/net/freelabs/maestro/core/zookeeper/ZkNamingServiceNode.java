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
package net.freelabs.maestro.core.zookeeper;

/**
 *
 * Class that describes a node for the naming service.
 */
public class ZkNamingServiceNode {

    /**
     * The zNode path of the container to which the service refers.
     */
    private String zkContainerPath;
    /**
     * The status of the service.
     */
    private SERVICE_STATE status;

    /**
     * Enum with two (2) states.
     */
    private static enum SERVICE_STATE {
        INITIALIZED, NOT_INITIALIZED
    };

    /**
     * Cosntructor.
     *
     * @param zkContainerPath the zNode of the container offering the service.
     */
    public ZkNamingServiceNode(String zkContainerPath) {
        this.zkContainerPath = zkContainerPath;
        status = SERVICE_STATE.NOT_INITIALIZED;
    }
    
    /**
     * Default constructor, necessary for de-serialization with Jackson.
     */
    public ZkNamingServiceNode(){
        
    }

    // Getters -Setters
    public SERVICE_STATE getStatus() {
        return status;
    }

    public void setStatusInitialized() {
        this.status = SERVICE_STATE.INITIALIZED;
    }

    public void setStatusNotInitialized() {
        this.status = SERVICE_STATE.NOT_INITIALIZED;
    }

    public String getZkContainerPath() {
        return zkContainerPath;
    }

}
