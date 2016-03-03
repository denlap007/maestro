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
 * Class that holds all the configuration regarding the zookeeper service and
 * the application deployment to the zookeeper service.
 */
public class ZkConf {

    /**
     * Application configuration for deployment to the zookeeper service.
     */
    private final ZkAppConf zkAppConf;
    /**
     * Zookeeper service configuration.
     */
    private final ZkSrvConf zkSrvConf;

    /**
     * Constructor.
     *
     * @param zkAppConf the application configuration for the deployment to 
     * zookeeper service.
     * @param zkSrvConf the zookeeper service configuration.
     */
    public ZkConf(ZkAppConf zkAppConf, ZkSrvConf zkSrvConf) {
        this.zkAppConf = zkAppConf;
        this.zkSrvConf = zkSrvConf;
    }

    /**
     *
     * @return the configuration for the zookeeper service.
     */
    public ZkSrvConf getZkSrvConf() {
        return zkSrvConf;
    }

    /**
     *
     * @return the configuration regarding the application deployment to the
     * zookeeper service.
     */
    public ZkAppConf getZkAppConf() {
        return zkAppConf;
    }

    // Getters for ZkSrvConf
    /**
     *
     * @return the list of zookeeper hosts.
     */
    public String getSrvHosts() {
        return zkSrvConf.getHosts();
    }

    /**
     *
     * @return the client session timeout.
     */
    public int getSrvTimeout() {
        return zkSrvConf.getTimeout();
    }

}
