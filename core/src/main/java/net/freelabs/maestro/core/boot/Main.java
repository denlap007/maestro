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
import java.net.URL;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
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
        cl.setCaseSensitiveOptions(false);
        cl.setAllowAbbreviatedOptions(true);

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
            System.err.print(e.getMessage() + ". See \'maestro --help\'");
            // exit
            errExit();
        }

        // get the command, if any, entered by the user
        String parsedCmd = cl.getParsedCommand();

        if (opts.getDockerOptions() != null) {
            // parse docker and options
            boolean parsed = opts.parseDockerOpts();
            if (!parsed) {
                // print usage
                errExit();
            }
        }
        if (opts.getZkOptions() != null) {
            boolean parsed = opts.parseZkOpts();
            if (!parsed) {
                // print usage
                errExit();
            }
        }

        // set (if specified) cli options before commands
        pConf.setZkHosts(opts.getZkHosts());
        pConf.setZkSessionTimeout(opts.getZkTimeout());
        // docker
        pConf.setDockerHost(opts.getDockerHost());
        pConf.setDockerTlsVerify(opts.getDockerTls());
        pConf.setDockerCertPath(opts.getDockerCertPath());
        pConf.setDockerConfigPath(opts.getDockerConfigPath());
        pConf.setDockerApiVersion(opts.getDockerApiVer());
        pConf.setDockerRegistryUrl(opts.getDockerRegUrl());
        pConf.setDockerRegistryUser(opts.getDockerRegUser());
        pConf.setDockerRegistryPass(opts.getDockerRegPass());
        pConf.setDockerRegistryMail(opts.getDockerRegMail());
        // xml
        pConf.setXmlSchemaPath(startCmdOpt.getSchema());
        pConf.setXmlFilePath(startCmdOpt.getXml());
        // log
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
             java.util.logging.Logger.getLogger(Main.class.getName()).log(Level.SEVERE,
                     "FAILED to locate log4j.properties file. Check the program's .properties file and/or user input.");
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
                    boolean confInitialized = pConf.isConfInit();
                    if (confInitialized) {
                        // execute START command
                        cmdExec.exec_start();
                    } else {
                        LOG.error("Program configuration NOT initialized. Check the .properties file and/or user input.");
                        errExit();
                    }
                }
            } else if (parsedCmd.equals(stop)) {
                // stop command
                if (stopCmdOpt.isHelp()) {
                    cl.usage(stop);
                } else {
                    // check if program configuration is complete
                    boolean confInitialized = pConf.isConfInit();
                    if (confInitialized) {
                        // execute STOP command
                        cmdExec.exec_stop(stopCmdOpt.getArgs().get(0));
                    } else {
                        LOG.error("Program configuration NOT initialized. Check the .properties file and/or user input.");
                        errExit();
                    }
                }
            } else if (parsedCmd.equals(restart)) {
                // restart command
                if (restartCmdOpt.isHelp()) {
                    cl.usage(restart);
                } else {
                    // check if program configuration is complete
                    boolean confInitialized = pConf.isConfInit();
                    if (confInitialized) {
                        // execute RESTART command
                        cmdExec.exec_restart(restartCmdOpt.getArgs().get(0));
                    } else {
                        LOG.error("Program configuration NOT initialized. Check the .properties file and/or user input.");
                        errExit();
                    }
                }
            } else if (parsedCmd.equals(delete)) {
                // delete command
                if (deleteCmdOpt.isHelp()) {
                    cl.usage(delete);
                } else {
                    // check if program configuration is complete
                    boolean confInitialized = pConf.isConfInit();
                    if (confInitialized) {
                        // execute DELETE command
                        cmdExec.exec_delete(deleteCmdOpt.getArgs().get(0));
                    } else {
                        LOG.error("Program configuration NOT initialized. Check the .properties file and/or user input.");
                        errExit();
                    }
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
     * Loads the log4j properties file.
     *
     * @param file the path of the log4j properties file.
     */
    private static void loadLod4jProperties(String file) {
        // load from file on disk
        Properties logProperties = new Properties();
        try {
            logProperties.load(new FileInputStream(file));
            PropertyConfigurator.configure(logProperties);
            LOG.debug("Loaded log4j properties file: {}", file);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Main.class.getName()).log(Level.WARNING,
                    "Could to load log4j properties file: {0}. Trying application workDir. ", ex.getMessage());
            try {
                String workDir = System.getProperty("user.dir");
                file = workDir + "/log4j.properties";
                logProperties.load(new FileInputStream(file));
                PropertyConfigurator.configure(logProperties);
                LOG.info("Loaded log4j properties file: {}", file);
            } catch (IOException ex1) {
                java.util.logging.Logger.getLogger(Main.class.getName()).log(Level.WARNING,
                        "Could to load log4j properties file: {0} {1}. Trying application classpath", new Object[]{file, ex.getMessage()});
                // load log4j properties file on classpath
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                URL url = loader.getResource("log4j.properties");
                if (url != null) {
                    PropertyConfigurator.configure(url);
                    LOG.info("Loaded log4j properties file: {}", url.toString());
                } else {
                    java.util.logging.Logger.getLogger(Main.class.getName()).log(Level.SEVERE,
                            "FAILED to locate a valid log4j.properties file. Put the file in your workDir, classpath "
                            + "\n"
                            + "\tor declare its path to program's .properties file!");
                    errExit();
                }
            }

        }
    }

}
