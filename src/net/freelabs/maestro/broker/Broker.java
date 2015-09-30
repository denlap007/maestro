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
package net.freelabs.maestro.broker;

import net.freelabs.maestro.generated.Container;
import static net.freelabs.maestro.utils.Utils.getType;
import net.freelabs.maestro.zookeeper.ZookeeperConfig;

/**
 *
 * Class that defines a Broker client to the zookeeper configuration store.
 * Must implement the BrokerInterface.
 */
public class Broker implements BrokerInterface{
    
private final Container con;  
private final String parentZkNodeName;
private final ZookeeperConfig zkConf;

    public Broker(Container con, ZookeeperConfig zkConf){
        this.con = con;
        this.zkConf = zkConf;
        parentZkNodeName = getType(con);
    }

    @Override
    public final void inspectContainer() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
