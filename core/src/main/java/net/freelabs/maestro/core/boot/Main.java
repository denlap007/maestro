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
package net.freelabs.maestro.core.boot;

import net.freelabs.maestro.core.cmd.CommandHandler;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;
import net.freelabs.maestro.core.boot.cl.CliOptions;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that holds the main method of the program. Parses command line
 * arguments and boots the program.
 */
public final class Main {

    /**
     * A Logger object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    /**
     * Main.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        /* FOR TESTING*/
        Scanner sc = new Scanner(System.in);
        String input = sc.nextLine();
        String[] inputArgs = input.split(" ");
        args = inputArgs;
        //-------------------------------------------------------------------- 

        // create object that holds program's configuration
        ProgramConf pConf = new ProgramConf();
        // create commnad handler to execute commands
        CommandHandler cmdExec = new CommandHandler(pConf);
        // create object with supported command line parameters and commands
        CliOptions opts = new CliOptions();
        // create the command line parser, initialize with cli options-cmds defined
        JCommander cl = new JCommander(opts);
        cl.setProgramName(ProgramConf.getPROGRAM_NAME());

        /* COMMANDS
         instantiate inner classes of CliOptions (commands) */
        CliOptions.StartCmdOpt startCmdOpt = opts.new StartCmdOpt();
        CliOptions.StopCmdOpt stopCmdOpt = opts.new StopCmdOpt();
        CliOptions.RestartCmdOpt restartCmdOpt = opts.new RestartCmdOpt();
        CliOptions.DeleteCmdOpt deleteCmdOpt = opts.new DeleteCmdOpt();
        // get command names
        String start = cmdExec.getStartCmd().getCmdName();
        String stop = cmdExec.getStopCmd().getCmdName();
        String restart = cmdExec.getRestartCmd().getCmdName();
        String delete = cmdExec.getDeleteCmd().getCmdName();
        // add commands defined to parser
        cl.addCommand(start, startCmdOpt);
        cl.addCommand(stop, stopCmdOpt);
        cl.addCommand(restart, restartCmdOpt);
        cl.addCommand(delete, deleteCmdOpt);

        // parse cli arguments
        try {
            cl.parse(args);
        } catch (ParameterException e) {
            // show error msg
            System.err.print(e.getMessage());
            // print usage
            cl.usage();
            // exit
            errExit();
        }

        // get the command, if any, entered by the user
        String parsedCmd = cl.getParsedCommand();
        // set (if specified) cli options before commands
        pConf.setZkHosts(opts.getzHosts());
        pConf.setZkSessionTimeout(opts.getzTimeout());
        pConf.setLog4jPropertiesPath(opts.getLog4j());
        pConf.setDockerHost(startCmdOpt.getDocker());
        pConf.setDockerRemote(startCmdOpt.isDockerRemote());
        pConf.setXmlSchemaPath(startCmdOpt.getSchema());
        pConf.setXmlFilePath(startCmdOpt.getXml());
        pConf.setLog4jPropertiesPath(opts.getLog4j());
        // if custom path for program configuration is set load values
        // only those not set by the uer
        if (opts.getConf() != null) {
            pConf.loadFromFileUnset(opts.getConf());
        } else {
            // load from current running path, if exists, options not set by user
            pConf.loadFromFileUnset("");
        }

        if (opts.isHelp()) {
            // program help
            cl.usage();
        } else if (opts.isVersion()) {
            // program version
            LOG.info("Maestro  v" + ProgramConf.getVERSION());
        } else if (pConf.getLog4jPropertiesPath() == null) {
            // no log4j.properties file
            System.err.print("No log4j.properties file found. Check the program's .properties file and/or user input. ");
            errExit();
        } else {
            // load log4j properties file to initialize logging
            loadLod4jProperties(pConf.getLog4jPropertiesPath());

            if (parsedCmd.equals(start)) {
                // start command
                if (startCmdOpt.isHelp()) {
                    cl.usage(start);
                } else {
                    // check if program configuration is complete
                    boolean ok = pConf.isConfInit();
                    if (ok) {
                        // execute START command
                        cmdExec.exec_start();
                    } else {
                        errExit();
                    }
                }
            } else if (parsedCmd.equals(stop)) {
                // stop command
                if (stopCmdOpt.isHelp()) {
                    cl.usage(stop);
                } else // check if program's configuration is complete
                if (pConf.getZkHosts() != null && pConf.getZkSessionTimeout() != 0) {
                    // execute STOP command
                    cmdExec.exec_stop(stopCmdOpt.getArgs().get(0));
                } else {
                    LOG.error("Program configuration NOT initialized. Check the .properties file and/or user input.");
                    errExit();
                }
            } else if (parsedCmd.equals(restart)) {
                // restart command
                if (restartCmdOpt.isHelp()) {
                    cl.usage(restart);
                } else // check if program's configuration is complete
                if (pConf.getZkHosts() != null && pConf.getZkSessionTimeout() != 0) {
                    // execute RESTART command
                    cmdExec.exec_restart(restartCmdOpt.getArgs().get(0));
                } else {
                    LOG.error("Program configuration NOT initialized. Check the .properties file and/or user input.");
                    errExit();
                }

            } else if (parsedCmd.equals(delete)) {
                // delete command
                if (deleteCmdOpt.isHelp()) {
                    cl.usage(delete);
                } else // check if program's configuration is complete
                if (pConf.getZkHosts() != null && pConf.getZkSessionTimeout() != 0) {
                    // execute DELETE command
                    cmdExec.exec_delete(deleteCmdOpt.getArgs().get(0));
                } else {
                    LOG.error("Program configuration NOT initialized. Check the .properties file and/or user input.");
                    errExit();
                }
            }
        }
    }

    /**
     * Exits program due to error using an error code.
     */
    private static void errExit() {
        System.exit(1);
    }

    /**
     * Exits program normally with no error code.
     */
    private static void exit() {
        System.exit(0);
    }

    /**
     * Loads the log4j properties file.
     *
     * @param file the path of the log4j properties file.
     */
    private static void loadLod4jProperties(String file) {
        try {
            Properties logProperties = new Properties();
            // load log4j properties file
            logProperties.load(new FileInputStream(file));
            PropertyConfigurator.configure(logProperties);
        } catch (IOException ex) {
            System.err.println("FAILED to load log4j properties file: " + ex.getMessage());
        }

    }
}
