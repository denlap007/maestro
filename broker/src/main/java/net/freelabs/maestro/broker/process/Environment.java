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
package net.freelabs.maestro.broker.process;

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
 * Class whose instances hold information about the environment of a container
 * as declared in the application description.
 */
public class Environment {

    /**
     * The object holding the environment for a {@link Container
     * container} object.
     */
    private final ContainerEnvironment conEnv;
    /**
     * The name of the container to which the environment belongs.
     */
    private final String conName;
    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Environment.class);

    /**
     * Constructor.
     *
     * @param conName the name of the container to which the environment
     * belongs.
     * @param conEnv object holding the environment for a {@link Container
     * container} object.
     *
     */
    public Environment(String conName, ContainerEnvironment conEnv) {
        this.conEnv = conEnv;
        this.conName = conName;
    }

    /**
     * Returns the environment of a ContainerEnvironment subtype via reflection.
     *
     * @param prefix the prefix to be applied to all keys of the returned map.
     * Usually, the container name, whose environment is returned, is provided.
     * @return a map with all the declared fields and values.
     */
    public Map<String, String> getEnvMap(String prefix) {
        // create a map that will hold the extracted environment
        Map<String, String> env = new HashMap<>();
        // get the Class object of the running object.
        Class<? extends ContainerEnvironment> cls = conEnv.getClass();
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
                fieldValue = field.get(conEnv);
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

    // Getters
    public ContainerEnvironment getConEnv() {
        return conEnv;
    }

    public String getConName() {
        return conName;
    }
}
