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

import java.nio.charset.Charset;

/**
 *
 * Class that holds all the zookeeper pre-configuration.
 */
public final class ZookeeperConfig {

    /**
     * The zookeeper hierarchy namespace root AND data if any. The field's data
     * encoding scheme is:
     * <pre> root[0] = /rootName | root[1] = rootData</pre>
     */
    private final String[] root;
    /**
     * The list of zookeepers hosts
     */
    private final String hosts;
    /**
     * The default charset to encode data
     */
    private static final Charset CHARSET = Charset.forName("UTF-8");
    /**
     * The zookeeper client session time out
     */
    private static final int SESSION_TIMEOUT = 5000;
    /**
     * Root node's children along with data. The field's data encoding scheme
     * is:
     * <pre> rootChildren[0] = /child1_Name | rootChildren[1] = child1_Data,
     * rootChildren[2] = /child2_Name | rootChildren[3] = child2_Data, ...
     * </pre>
     */
    private final String[] rootChildren;

    /**
     * Constructor.
     *
     * @param root the zookeeper hierarchy namespace root. Must follow the
     * format: <pre> /root, data </pre>
     *
     * @param hosts the list of zookeeper hosts. Must follow the format: 
     * <pre> HOST1_IP:PORT, HOST2_IP:PORT, ...</pre>
     *
     * @param rootChildren root's children with data. Must follow the format: <pre> /child, data, /child, data, ...
     * </pre>
     */
    public ZookeeperConfig(String[] root, String hosts, String[] rootChildren) {
        this.root = root;
        this.hosts = hosts;
        this.rootChildren = rootChildren;
    }

    /**
     * @return the hosts
     */
    public String getHosts() {
        return hosts;
    }

    /**
     * @return the CHARSET
     */
    public static Charset getCHARSET() {
        return CHARSET;
    }

    /**
     * @return the SESSION_TIMEOUT
     */
    public static int getSESSION_TIMEOUT() {
        return SESSION_TIMEOUT;
    }

    /**
     * @return the root
     */
    public String[] getRoot() {
        return root;
    }

    /**
     * @return the rootChildren names
     */
    public String[] getRootChildrenNames() {
        String[] children = null;
        if (getRootChildren() != null) {
            // Create a new array to return only the children names not data
            children = new String[getRootChildren().length/2];
            
            for (int i=0, j=0; i < getRootChildren().length; i++, j=j+2) {
                children[i] = getRootChildren()[j];
            }
        }

        return children;
    }

    /**
     * @return the rootChildren
     */
    public String[] getRootChildren() {
        return rootChildren;
    }
    
}
