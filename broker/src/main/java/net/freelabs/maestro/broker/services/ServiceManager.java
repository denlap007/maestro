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
package net.freelabs.maestro.broker.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.freelabs.maestro.broker.services.ServiceNode.SRV_CONF_STATUS;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.zookeeper.ZkNamingServiceNode.SRV_STATE_STATUS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * <p>
 * Class that holds all the information about the service-dependencies for the
 * container.
 * <p>
 * The container needs the configuration from the services in order to
 * initialize and also to ensure that the required services are initialized (not
 * just running) in order to start the main container process.
 */
public final class ServiceManager {

    /**
     * The required services by the container. The list holds the zNode service
     * path to the naming service namespace.
     */
    private final List<String> services;
    /**
     * The service nodes of the required services.
     */
    private final Map<String, ServiceNode> srvNodes = new HashMap<>();

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ServiceManager.class);

    /**
     * Constructor.
     *
     * @param services the services-dependencies of the container.
     */
    public ServiceManager(List<String> services) {
        // initialize list of services
        this.services = services;
    }

    /**
     * Add a service node to the service manager.
     *
     * @param srvPath the zNode path of the service to the naming service.
     * @param srvNode the service node.
     */
    public void addSrvNode(String srvPath, ServiceNode srvNode) {
        srvNodes.put(srvPath, srvNode);
    }

    public boolean areSrvProcessed() {
        StringBuilder waitingServices = new StringBuilder();

        srvNodes.entrySet().stream().map((entry) -> entry.getValue()).forEach((srvNode) -> {
            SRV_CONF_STATUS srvConfStatus = srvNode.getSrvConfStatus();
            if (srvConfStatus == SRV_CONF_STATUS.NOT_PROCESSED) {
                waitingServices.append(srvNode.getServiceName()).append(" ");
            }
        });
        
        if (waitingServices.toString().isEmpty()) {
            if (!srvNodes.isEmpty()){
                LOG.info("All services PROCESSED.");
            }
            return true;
        } else {
            LOG.info("Waiting to process services: {}", waitingServices);
            return false;
        }
    }

    public boolean areSrvInitialized() {
        StringBuilder waitingServices = new StringBuilder();

        srvNodes.entrySet().stream().map((entry) -> entry.getValue()).forEach((srvNode) -> {
            SRV_STATE_STATUS srvStateStatus = srvNode.getSrvStateStatus();
            if (srvStateStatus != SRV_STATE_STATUS.INITIALIZED) {
                waitingServices.append(srvNode.getServiceName()).append(" ");
            }
        });

        if (waitingServices.toString().isEmpty()) {
            if (!srvNodes.isEmpty()){
                LOG.info("All services INITIALIZED.");
            }            
            return true;
        } else {
            LOG.info("Waiting for services to initialize: {}", waitingServices);
            return false;
        }
    }

    /**
     * Sets the service configuration status to PROCESSED.
     *
     * @param srvPath the zNode path of the service to the naming service
     * namespace.
     */
    public void setSrvConfStatusProc(String srvPath) {
        srvNodes.get(srvPath).setSrvConfStatus(SRV_CONF_STATUS.PROCESSED);
    }
    
    /**
     * Sets the service state status.
     *
     * @param srvPath the zNode path of the service to the naming service
     * namespace.
     * @param srvStateStatus the updated service state status.
     */
    public void setSrvStateStatus(String srvPath, SRV_STATE_STATUS srvStateStatus) {
        srvNodes.get(srvPath).setSrvStateStatus(srvStateStatus);
    }

    public void getSrvConfStatus(String srvPath) {
        srvNodes.get(srvPath).getSrvConfStatus();
    }
    
    public void setSrvNodeCon(String conPath, Container con){
        for (ServiceNode srvNode : srvNodes.values()){
            if (srvNode.getZkConPath().equals(conPath)){
                srvNode.setCon(con);
            }
        }
    }
    
    public boolean hasServices(){
        return !services.isEmpty();
    }

    public List<String> getServices() {
        return services;
    }
    
    /**
     * 
     * @return a {@link List List} with the {@link Container Container} objects of
     * all services.
     */
    public List<Container> getConsOfSrvs(){
        List<Container> srvConList = new ArrayList<>();
        srvNodes.values().stream().forEach((srvNode) -> {
            srvConList.add(srvNode.getCon());
        });
        return srvConList;
    }
}
