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
package net.freelabs.maestro.broker;

import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.zookeeper.ZkNamingServiceNode;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public class ContainerData {

    /**
     * The container associated with the broker. Holds the configuration.
     */
    private Container con;
    /**
     * The name of the container associated with the broker.
     */
    private String conName;
    /**
     * The name of the service.
     */
    private String srvName;
    /**
     * The zNode path of the service to the naming service namespace.
     */
    private String srvPath;
    /**
     * The znode of the container service to the naming service.
     */
    private ZkNamingServiceNode zkConSrvNode;

    /**
     * Indicates weather the container is initialized.
     */
    private volatile boolean conInitialized;
    
    /**
     * 
     * @return the znode path of the {@link Container container} object to the 
     * zookeeper namespace.
     */
    public String getZkConPath(){
        return zkConSrvNode.getZkContainerPath();
    }

}
