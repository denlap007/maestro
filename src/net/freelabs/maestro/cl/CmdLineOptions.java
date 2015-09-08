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
package net.freelabs.maestro.cl;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 *
 * @author Dionysis Lappas (dio@freelabs.net)
 */
public final class CmdLineOptions {

    private Options options;

    public CmdLineOptions() {
        // create Options object
        options = new Options();
    }

    public void initOptions() {

        options.addOption(Option.builder("g")
                .longOpt("generate")
                //.numberOfArgs(3)
                //.argName(".xsd path> <packageName> <outputDir")
                .desc("generate classes from .xsd.")
                .build());

        options.addOption("h", "help", false, "print help");

        options.addOption(Option.builder("c")
                .longOpt("compile")
                //.numberOfArgs(2)
                //.argName("classPath> <sourcePath")
                .desc("compile classes.")
                .build());

        options.addOption(Option.builder("a")
                //.numberOfArgs(1)
                //.argName("dir")
                .desc("add classes to classpath. Specify as <dir> the parent of the package folder.")
                .build());

        options.addOption(Option.builder("u")
                .longOpt("unmarshal")
                //.numberOfArgs(3)
                //.argName("package> <schema path> <.xml file path")
                .desc("validate .xml against schema and unmarshal. <package> "
                        + "is the package that holds java classes to bind to.")
                .build());

        options.addOption(Option.builder("p")
                .longOpt("process-conf")
                .desc("generate-compile-add to classPath classes and unmarshall .xml")
                .build());
        
    }

    /**
     * automatically generate the help statement
     */
    public void help() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setDescPadding(20);
        formatter.printHelp("maestro \n", options, true);
    }

    /**
     * @return the options
     */
    public Options getOptions() {
        return options;
    }

}
