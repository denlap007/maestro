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
package net.freelabs.maestro.core.cl;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 * Class that initialized and handles the command line options.
 */
public class CommandLineOptions {

    /**
     * Command line options
     */
    private final Options options;

    /**
     * Constructor
     */
    public CommandLineOptions() {
        options = new Options();
        // initialize
        initOptions();
    }

    /**
     * Initializes an {@link Options Options} object with command line options.
     */
    private void initOptions() {
        options.addOption(Option.builder("w")
                .longOpt("workDir")
                .numberOfArgs(1)
                .argName("dirPath")
                .desc("Set the program's work directory path.")
                .build());
    }

    /**
     * Automatically generates the help statement
     */
    public void help() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setDescPadding(5);
        formatter.printHelp("maestro \n", options, true);
    }

    /**
     * @return the options
     */
    public Options getOptions() {
        return options;
    }

}
