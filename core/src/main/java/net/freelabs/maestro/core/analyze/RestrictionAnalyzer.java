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
package net.freelabs.maestro.core.analyze;

import java.util.List;
import net.freelabs.maestro.core.generated.Container;

/**
 *
 * Class that provides methods to analyze restrictions applied to application
 * schema.
 */
public class RestrictionAnalyzer {

    /**
     * Analyzes restrictions on dependencies.
     */
    private DependencyAnalyzer depAnalyzer;
    /**
     * List of containers declared on schema.
     */
    private final List<Container> containers;

    /**
     * Constructor.
     *
     * @param containers list of containers declared on schema.
     */
    public RestrictionAnalyzer(List<Container> containers) {
        this.containers = containers;
    }

    /**
     * Analyzes declared container dependencies for circular dependencies.
     *
     * @return true if a circular dependency is found.
     */
    public boolean detectCircularDependencies() {
        // create dependency analyzer
        depAnalyzer = new DependencyAnalyzer(containers);
        // check for circular dependencies
        return depAnalyzer.analyzeDependencies();
    }

    /**
     * Detects duplicate container names.
     *
     * @return true if a duplicate container name is found.
     */
    public boolean detectDuplicateNames() {
        // create name analyzer
        ContainerNameAnalyzer nameAnalyzer = new ContainerNameAnalyzer(containers);
        // check for duplicate contaier names
        return nameAnalyzer.detectDuplicateNames();
    }

}
