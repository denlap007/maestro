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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.freelabs.maestro.core.generated.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides methods to analyze container dependencies in order to
 * detect circular dependencies.
 */
public class DependencyAnalyzer {
    /**
     * List of containers declared on application schema.
     */
    private final List<Container> containers;
        /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DependencyAnalyzer.class);
    /**
     * Constructor.
     * @param containers list of containers declared on application schema.
     */
    public DependencyAnalyzer(List<Container> containers) {
        this.containers = containers;
    }

    /**
     * Detects circular dependencies.
     * @return true if a circular dependency is found.
     */
    public boolean detectCircular() {
        // create map <containername-Dependencies list>
        Map<String, List<String>> depMap = new HashMap<>();

        containers.stream().forEach((con) -> {
            String key = con.getName();
            List<String> value = con.getConnectWith();
            depMap.put(key, value);
        });
        // check if a container is in the dependencies list of the container
         for (Map.Entry<String, List<String>> entry : depMap.entrySet()) {
            // get the list of dependencies for a container
            String conName = entry.getKey();
            List<String> depList = entry.getValue();
            // check dependency list for the container from which the dependency depends
            if (depList.contains(conName)){
                LOG.error("Container \'{}\' cannot be a dependency to itself.", conName);
                return true;
            }
         }

        // check that every dependency container in the dependency list of a 
        // container does not have the container from which it depends listed as 
        // a dependency
        for (Map.Entry<String, List<String>> entry : depMap.entrySet()) {
            // get the list of dependencies for a container
            String conName = entry.getKey();
            List<String> depList = entry.getValue();
            // for every container-dependency in the dependency list
            for (String depName : depList){
                // find the depList of this container-dependency
                List<String> depDepList = depMap.get(depName);
                // search if the container from which the container-dependency
                // depends is foiund in the list
                if (depDepList.contains(conName)){
                    LOG.error("Container \'{}\' is a dependency of \'{}\'. Container \'{}\' is a dependency of \'{}\'", conName, depName, depName, conName);
                    return true;
                }
            }
        }
        return false;
    }
}
