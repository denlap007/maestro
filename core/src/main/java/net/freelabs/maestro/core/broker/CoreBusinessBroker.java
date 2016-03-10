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

import net.freelabs.maestro.core.zookeeper.ZkExecutor;
import com.github.dockerjava.api.DockerClient;
import net.freelabs.maestro.core.generated.BusinessContainer;
import net.freelabs.maestro.core.zookeeper.ZkConf;

/**
 * Class that provides methods to handle initialization and bootstrapping of a
 * Business container type.
 */
public class CoreBusinessBroker extends CoreBroker {

    /**
     * The container description.
     */
    private final BusinessContainer con;

    /**
     * Constructor.
     *
     * @param zkConf the zookeeper configuration.
     * @param con the container object.
     * @param dockerClient an instance of a docker client.
     * @param zkClient a zkClient that will make requests to zookeeper.
     */
    public CoreBusinessBroker(ZkConf zkConf, BusinessContainer con, DockerClient dockerClient, ZkExecutor zkClient) {
        super(zkConf, con, dockerClient, zkClient);
        this.con = con;
    }

    @Override
    protected void updateIP(String IP) {
        con.getEnvironment().setHost_IP(IP);
    }
}
