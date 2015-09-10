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
package net.freelabs.maestro.conf;

import generated.Container;
import generated.WebApp;
import net.freelabs.maestro.cl.CmdLineOptions;
import java.io.File;
import java.io.IOException;
import net.freelabs.maestro.broker.Broker;
import net.freelabs.maestro.broker.BrokerGenerator;
import net.freelabs.maestro.handling.ContainerTypeHandler;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;

/**
 *
 * Class that holds the main method for testing.
 */
public class TheMainXml {

    public static void main(String[] args) throws IOException, Exception {
        ConfProcessor classGen = new ConfProcessor();

        CmdLineOptions opt = new CmdLineOptions();
        opt.initOptions();

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(opt.getOptions(), args);
        } catch (ParseException ex) {
            // Unrecognized arguments
            System.out.println(ex);
            opt.help();
            System.exit(1);
        }

        if (cmd.hasOption("g")) {
            // [GENERATE CLASSES]
            String schemaPath = "/home/dio/THESIS/maestro/xmlSchema.xsd";
            String packageName = "pack";
            String outputDir = "/home/dio/testClass/source";
            // Parse option values
            String[] values = cmd.getOptionValues("g");
            //Generate classes
            //classGen.xmlToClass(values[0], values[1], values[2]);
            classGen.xmlToClass(schemaPath, packageName, outputDir);
        } else if (cmd.hasOption("c") || cmd.hasOption("compile")) {
            // [COMPILE CLASSES]
            String classpath = "/home/dio/testClass/class";
            String srcPath = "/home/dio/testClass/source/pack";
            // Parse option values
            String[] values = cmd.getOptionValues("c");
            //classGen.compile(values[0], values[1]);
            classGen.compile(classpath, srcPath);
        } else if (cmd.hasOption("a") || cmd.hasOption("add")) {
            // [ADD CLASSES TO CLASSPATH]
            String classpath = "/home/dio/testClass/class";
            String srcPath = "/home/dio/testClass/source/pack";
            // Parse option values
            String[] values = cmd.getOptionValues("a");
            // classGen.addToClasspath(values[0]);
            classGen.addToClasspath("/home/dio/testClass/class");
        } else if (cmd.hasOption("u") || cmd.hasOption("unmarshal")) {
            // [UNMARSHALL xml file]
            String packageName = "pack";
            String schemaPath = "/home/dio/THESIS/maestro/xmlSchema.xsd";
            String xmlFilePath = "/home/dio/THESIS/maestro/xmlTest.xml";

            // Parse option values
            String[] values = cmd.getOptionValues("u");
            // classGen.unmarshal(values[0], values[1], values[2]);
            classGen.addToClasspath("/home/dio/testClass/class");
            classGen.unmarshal(packageName, schemaPath, xmlFilePath);
        } else if (cmd.hasOption("p") || cmd.hasOption("process-conf")) {
        // [PROCESS CONFIGURATION PIPELINE]

            /// [generate classes]
            String schemaPath = "/home/dio/THESIS/maestro/xmlSchema.xsd";
            String packageName = "pack";
            String genClassDir = "/home/dio/testClass/source";

            classGen.xmlToClass(schemaPath, packageName, genClassDir);

            // [compile classes]
            String classpath = "/home/dio/testClass/class";
            String srcPath = "/home/dio/testClass/source" + File.separator + packageName;

            classGen.compile(classpath, srcPath);

            // [add classes to classpath]
            classGen.addToClasspath(classpath);

            // [unmarchall .xml]
            String xmlFilePath = "/home/dio/THESIS/maestro/xmlTest.xml";
            classGen.unmarshal(packageName, schemaPath, xmlFilePath);

        } else if (cmd.hasOption("t") || cmd.hasOption("test")) {
      // [TEST IMPLEMENTATION]
               // [unmarchall .xml]
            String xmlFilePath = "/home/dio/THESIS/maestro/xmlTest.xml";
            String schemaPath = "/home/dio/THESIS/maestro/xmlSchema.xsd";
            
            //-------------------- TEST --------------------
            // Get root Object
            WebApp webApp = (WebApp)classGen.unmarshal("generated", schemaPath, xmlFilePath);
            // Get a handler for containers
            ContainerTypeHandler handler = new ContainerTypeHandler(webApp.getContainers());
            System.out.println("[Created handler]");
            // Create a broker generator to instantiate brokers
            BrokerGenerator bg = new BrokerGenerator();
            System.out.println("[Created Broker Generator]");
            
            // while there are containers
            while(handler.hasContainers()){
                // get a container object
                Container con = handler.getContainer();
                System.out.println(con);
                //Create a broker
                Broker newBroker = bg.createBroker(con);
                //Print broker
                System.out.println("Created broker: " + newBroker.toString());

            }
            
            
            
            
            
            
        } else if (cmd.hasOption("h") || cmd.hasOption("help")) {
            // Show help
            opt.help();
        }

    }
}
