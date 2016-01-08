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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import java.lang.reflect.Field;
import net.freelabs.maestro.core.generated.DataContainer;
import net.freelabs.maestro.core.generated.DataEnvironment;
import net.freelabs.maestro.core.zookeeper.ZkConfig;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public class CoreDataBroker extends CoreBroker {

    private final DataContainer dataCon;

    public CoreDataBroker(ZkConfig zkConf, DataContainer con, DockerClient dockerClient) {
        super(zkConf, con, dockerClient);
        dataCon = con;
    }

    @Override
    public String bootContainer() {
        // boot configuration
        String containerName = dataCon.getName();
        Volume volume1 = new Volume("/broker");
        // get the container description
        String conEnv = setConDescription();

        // command to run on boot
        String runBrokerCmd = "java -jar /broker/broker.jar $ZK_HOSTS $ZK_SESSION_TIMEOUT $ZK_CONTAINER_PATH $ZK_NAMING_SERVICE $SHUTDOWN_NODE $CONF_NODE";

        // set container configuration for the docker client to create a new container
        CreateContainerResponse container = dockerClient.createContainerCmd(dataCon.getDockerImage())
                .withVolumes(volume1)
                .withBinds(new Bind("/home/dio/THESIS/maestro/core/src/main/resources", volume1, AccessMode.rw))
                .withCmd("/bin/sh", "-c", runBrokerCmd)
                .withName(containerName)
                .withNetworkMode("bridge")
                .withEnv(conEnv.split(","))
                .withPrivileged(true)
                .exec();

        // START CONTAINER
        dockerClient.startContainerCmd(container.getId()).exec();
        LOG.info("STARTING CONTAINER: " + containerName);

        return container.getId();
    }

    /**
     * <p>
     * Generates the container description.
     * <p>
     * The container description is all the necessary configuration for the
     * environment of the container in order to boot and initialize.
     *
     * @return a String with key/value pairs in the form key1=value1,
     * key2=value2 e.t.c. representing the container description.
     */
    private String setConDescription() {
        // set boot environment configuration
        String ZK_HOSTS = zkConf.getHosts();
        String ZK_SESSION_TIMEOUT = String.valueOf(zkConf.getSESSION_TIMEOUT());
        String ZK_CONTAINER_PATH = zNode.getPath();
        String ZK_NAMING_SERVICE = zkConf.getNamingServicePath();
        String SHUTDOWN_NODE = zkConf.getShutDownPath();
        String CONF_NODE = zNode.getConfNodePath();
        // create a string with all the key-value pairs
        String env = String.format("ZK_HOSTS=%s,ZK_SESSION_TIMEOUT=%s,"
                + "ZK_CONTAINER_PATH=%s,ZK_NAMING_SERVICE=%s,SHUTDOWN_NODE=%s,"
                + "CONF_NODE=%s", ZK_HOSTS, ZK_SESSION_TIMEOUT, ZK_CONTAINER_PATH,
                ZK_NAMING_SERVICE, SHUTDOWN_NODE, CONF_NODE);

        return env;
    }

    /**
     * Gets the environment of a DataContainer via reflection to a string.
     *
     * @return a string with key/value pairs in the form key1=value1,
     * key2=value2 e.t.c. with all the fields declared in the
     * {@link DataEnvironment DataEnvironment} class.
     */
    private String getDataConEnv_Reflection() {
        StringBuilder env = new StringBuilder();
        // get the class object of the DataEnvironment object
        Class<DataEnvironment> cls = DataEnvironment.class;
        // get all the declared fields of the DataEnvironment object
        Field[] fields = cls.getDeclaredFields();

        for (Field field : fields) {
            // set field accessible to true in case it cannot be accesed from this class
            field.setAccessible(true);
            // get the name of the field
            String fieldName = field.getName().toUpperCase();
            Object fieldValue = null;
            try {
                // get the value of the field 
                fieldValue = field.get(dataCon.getEnvironment());
                LOG.info("Field: " + fieldName + "Value: " + fieldValue); // for testing
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                LOG.error("Something went wrong: " + ex);
            }
            // append the field's name-value to the return string
            if (fieldValue != null && !fieldName.isEmpty()) {
                env.append(fieldName).append("=").append(fieldValue).append(",");
            }
        }
        
        return env.toString();
    }

}
