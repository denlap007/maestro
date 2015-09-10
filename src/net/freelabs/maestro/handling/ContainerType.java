/*
 * Copyright (C) 2015 Dionysis Lappas (dio@freelabs.net)
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
package net.freelabs.maestro.handling;

import generated.BusinessContainer;
import generated.Containers;
import generated.DataContainer;
import generated.WebContainer;
import java.util.Collection;

/**
 * <p>
 * Class that holds collections with the basic container types and provides
 * setters and getters.
 * <p>
 * This class provides extensibility and may be extended if any new
 * container types are to be declared.
 */
public class ContainerType {

    private final Collection<WebContainer> webContainers;
    private final Collection<BusinessContainer> businessContainers;
    private final Collection<DataContainer> dataContainers;

    /**
     * Default constructor. Initializing class fields.
     *
     * @param containers
     */
    public ContainerType(Containers containers) {
        webContainers = containers.getWebContainer();
        businessContainers = containers.getBusinessContainer();
        dataContainers = containers.getDataContainer();
    }

    /**
     * @return the webContainers
     */
    public final Collection<WebContainer> getWebContainers() {
        return webContainers;
    }

    /**
     * @return the businessContainers
     */
    public final Collection<BusinessContainer> getBusinessContainers() {
        return businessContainers;
    }

    /**
     * @return the dataContainers
     */
    public final Collection<DataContainer> getDataContainers() {
        return dataContainers;
    }

}
