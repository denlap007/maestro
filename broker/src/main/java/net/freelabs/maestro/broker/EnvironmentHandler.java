/*
 * Copyright (C) 2016 Dionysis Lappas <dio@freelabs.net>
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.generated.ContainerEnvironment;
import net.freelabs.maestro.core.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that handles the interaction with the environment of a {@link Container
 * container} object.
 */
public final class EnvironmentHandler {

    /**
     * The object holding the environment for the {@link Container
     * container} object associated with the Broker.
     */
    private final ContainerEnvironment conEnv;

    /**
     * A map, with the name of the containers dependencies to the container
     * associated with the Broker and the objects holding the environment for
     * the {@link Container container} objects of the dependencies.
     */
    private final Map<String, ContainerEnvironment> depConEnvMap;
    /**
     * The created environment for the container processes.
     */
    private Map<String, String> procsEnv;

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(EnvironmentHandler.class);

    /**
     * Constructor.
     *
     * @param conEnv the object holding the environment for the {@link Container
     * container} object associated with the Broker.
     * @param depConEnvMap a map, with the name of the containers dependencies
     * to the container associated with the Broker and the objects holding the
     * environment for the {@link Container container} objects of the
     * dependencies.
     */
    public EnvironmentHandler(ContainerEnvironment conEnv, Map<String, ContainerEnvironment> depConEnvMap) {
        this.conEnv = conEnv;
        this.depConEnvMap = depConEnvMap;
    }
    
    /**
     * <p>Creates the environment for all the container processes.
     * <p>The environment consists of all the environment variables defined in 
     * the application schema for the container associated with the Broker and 
     * all its required dependencies.
     * <p>The environment variables created comply to the following naming 
     * convention format: ${containerName_envVarSchemaName}, with sole exception
     * the env vars declared to the container object associated with the Broker. 
     * These env vars are not prefixed with the 'containerName'.
     * <p>Example: <br>
     * Declared app consists of three containers with dependencies as follows: 
     * data &lt;- business &lt;- web. <br>
     * Env var declarations:
     * <ul>
     * <li>data: db_name</li>
     * <li>business: app_name</li>
     * <li>web: host_port</li>
     * </ul>
     * Now, the created env vars in every container are as follows:
     * <ul>
     * <li>data: DB_NAME, BUSINESS_APP_NAME, WEB_HOST_PORT</li>
     * <li>business: APP_NAME, DATA_DB_NAME, WEB_HOST_PORT</li>
     * <li>web: HOST_PORT, BUSINESS_APP_NAME, DATA_DB_NAME</li>
     * </ul>
     * @return the environment for container processes.
     */
    public final Map<String, String> createProcsEnv() {
        // get environment from the container obj associated with the broker
        Map<String, String> env = getEnvMap(conEnv, "");
        // get environment from dependencies and add to environment
        env.putAll(getDependenciesEnv());
        // initialize var with procs environment
        procsEnv = env;
        return env;
    }


    /**
     * Extracts the environment from the services-dependencies of the main
     * container service.
     *
     * @return a map with all the environment variables defines in services-
     * dependencies.
     */
    private Map<String, String> getDependenciesEnv() {
        LOG.info("Extracting environment from dependencies.");
        // holds the global key-value entries for all container
        Map<String, String> dependenciesEnv = new HashMap<>();
        // holds data per repetition
        Map<String, String> env;

        // iterate through the container dependencies
        for (Map.Entry<String, ContainerEnvironment> entry : depConEnvMap.entrySet()) {
            String depName = entry.getKey();
            ContainerEnvironment envObj = entry.getValue();
            // get environment
            env = getEnvMap(envObj, depName);
            // print extracted environment
            LOG.info("Environment of dependency: {}", depName);
            for (Map.Entry<String, String> e : env.entrySet()) {
                String key = e.getKey();
                String value = e.getValue();
                LOG.info("{}={}", key, value);
            }
            // add map to dependencies map
            dependenciesEnv.putAll(env);
            // remove everything
            env.clear();
        }
        return dependenciesEnv;
    }

    /**
     * Returns the environment of a ContainerEnvironment subtype via reflection.
     *
     * @param obj an object of a ContainerEnvironment subclass.
     * @param prefix the prefix to be applied to all keys of the returned map.
     * Usually, the container name, whose environment is returned, is provided.
     * @return a map with all the declared fields and values.
     */
    public Map<String, String> getEnvMap(ContainerEnvironment obj, String prefix) {
        // create a map that will hold the extracted environment
        Map<String, String> env = new HashMap<>();
        // get the Class object of the running object.
        Class<? extends ContainerEnvironment> cls = obj.getClass();
        // get all the declared fields of the Class object and its superclass
        Collection<Field> fields = new ArrayList<>();
        fields = Utils.getAllFields(fields, cls);

        for (Field field : fields) {
            // set field accessible to true in case it cannotbe accesed from this class
            field.setAccessible(true);
            // get the name of the field
            String fieldName = field.getName();
            // create the final name for the key, if prefix is empty do not use it
            String key;
            if (prefix.isEmpty()) {
                key = (fieldName).toUpperCase();
            } else {
                key = (prefix + "_" + fieldName).toUpperCase();
            }

            Object fieldValue;
            try {
                // get the value of the field and convert it to String
                fieldValue = field.get(obj);
                if (fieldValue != null) {
                    String fieldValueStr = String.valueOf(fieldValue);
                    // put to map
                    env.put(key, fieldValueStr);
                    //System.out.println("Field: " + fieldName + ", Value: " + fieldValueStr); // for testing
                }
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                LOG.error("Somethng went wrong: " + ex);
            }
        }
        return env;
    }

}
