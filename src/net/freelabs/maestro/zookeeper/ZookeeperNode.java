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
package net.freelabs.maestro.zookeeper;

import java.io.Serializable;

/**
 *
 * Class that represents a zookeeper Node.
 */
public class ZookeeperNode implements Serializable{

    /**
     * The path of the zookeeper node.
     */
    private final String path;
    /**
     * The data of the zookeeper node.
     */
    private final byte[] data;

    /**
     * Constructor.
     * @param path the path of the zookeeper node.
     * @param data the data of the zookeeper node.
     */
    public ZookeeperNode(String path, byte[] data) {
        this.path = path;
        this.data = data;

    }

    /**
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * @return the data
     */
    public byte[] getData() {
        return data;
    }
    

}
