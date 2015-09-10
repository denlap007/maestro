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
import generated.Container;
import generated.Containers;
import generated.DataContainer;
import generated.WebContainer;
import java.util.Iterator;

/**
 * <p>
 * Class the handles the container types collections. Provides methods to get
 * containers from the collection of every container type or get the containers
 * exhaustively.
 * <p>
 * This Class may be extended to add extra functionality if any new container
 * types are declared and need to be handled.
 */
public class ContainerTypeHandler {

    private final Containers ct;

    public ContainerTypeHandler(Containers ct) {
        this.ct = ct;
    }

    /**
     * <p>
     * Returns a container of type: web, business, data and then removes it from
     * the collection.
     * <p>
     * This method may be used to exhaustively get all the containers.
     * <p>
     * Override this method to return more container types.
     *
     * @return a container type: web, business, data. NULL if there is no
     * container in the collection
     */
    public Container getContainer() {
        // Get iterator for each container type
        Iterator<WebContainer> webIter = ct.getWebContainer().iterator();
        Iterator<BusinessContainer> BusinessIter = ct.getBusinessContainer().iterator();
        Iterator<DataContainer> dataIter = ct.getDataContainer().iterator();

        // while there are elements in the collection
        while (webIter.hasNext()) {
            // get a web container
            WebContainer con = webIter.next();
            // remove the container from the collection
            webIter.remove();
            return con;
        }

        // while there are elements in the collection
        while (BusinessIter.hasNext()) {
            // get a web container
            BusinessContainer con = BusinessIter.next();
            // remove the container from the collection
            BusinessIter.remove();
            return con;
        }

        // while there are elements in the collection
        while (dataIter.hasNext()) {
            // get a web container
            DataContainer con = dataIter.next();
            // remove the container from the collection
            dataIter.remove();
            return con;
        }

        // If collections are empty, there's no container to return
        return null;
    }

    /**
     * Returns if there are any containers in any collection.
     * @return true if there is at least one container in any of the collection.
     */
    public Boolean hasContainers() {
        // Get iterator for each container type
        Iterator<WebContainer> webIter = ct.getWebContainer().iterator();
        Iterator<BusinessContainer> BusinessIter = ct.getBusinessContainer().iterator();
        Iterator<DataContainer> dataIter = ct.getDataContainer().iterator();

        return (webIter.hasNext() || BusinessIter.hasNext() || dataIter.hasNext());
    }

    /**
     * Returns a container of web container type and then removes it from the
     * collection.
     *
     * @return a container of web container type.
     */
    public final Container getWebContainer() {
        // Get iterator for web container type
        Iterator<WebContainer> webIter = ct.getWebContainer().iterator();
        // while there are elements in the collection
        while (webIter.hasNext()) {
            // get a web container
            WebContainer con = webIter.next();
            // remove the container from the collection
            webIter.remove();
            return con;
        }
        // If collection is empty, there's no container to return
        return null;
    }

    /**
     * Returns a container of business container type and then removes it from
     * the collection.
     *
     * @return a container of business container type.
     */
    public final Container getBusinessContainer() {
        // Get iterator for business container type
        Iterator<BusinessContainer> BusinessIter = ct.getBusinessContainer().iterator();
        // while there are elements in the collection
        while (BusinessIter.hasNext()) {
            // get a business container
            BusinessContainer con = BusinessIter.next();
            // remove the container from the collection
            BusinessIter.remove();
            return con;
        }
        // If collection is empty, there's no container to return
        return null;
    }

    /**
     * Returns a container of data container type and then removes it from the
     * collection.
     *
     * @return a container of data container type.
     */
    public final Container getDataContainer() {
        // Get iterator for data container type
        Iterator<DataContainer> DataIter = ct.getDataContainer().iterator();
        // while there are elements in the collection
        while (DataIter.hasNext()) {
            // get a data container
            DataContainer con = DataIter.next();
            // remove the container from the collection
            DataIter.remove();
            return con;
        }
        // If collection is empty, there's no container to return
        return null;
    }
}
