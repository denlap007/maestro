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
package net.freelabs.maestro.core.boot.cl;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.util.List;

/**
 *
 * Class whose instance defines all the command line parameters and commands for
 * the program.
 */
public class CliOptions {

    @Parameter(names = {"-h", "--help"}, description = "Show this help message.", help = true)
    private boolean help;

    @Parameter(names = {"-v", "--version"}, description = "Show program version.")
    private boolean version;

    // Getters
    public boolean isVersion() {
        return version;
    }

    public boolean isHelp() {
        return help;
    }

    // start command
    @Parameters(commandDescription = "Start application deployment")
    public class StartCmdOpt {

        @Parameter(names = {"-h", "--help"}, description = "Help for this command", help = true)
        private boolean help;

        @Parameter(names = {"-s", "--schema"}, description = "<schema file> Path of schema file.", required = false)
        private String schema;

        @Parameter(names = {"-d", "--docker"}, description = "<docker uri> Docker daemon uri.", required = false)
        private String docker;

        @Parameter(names = {"-x", "--xml"}, description = "<app xml> Path of application description xml file.", required = false)
        private String xml;

        @Parameter(names = {"-c", "--conf"}, description = "<program conf> Path of .properties file with program's configuration.", required = false)
        private String conf;

        @Parameter(names = {"-t", "--timeout"}, description = "<session timeout> Zookeeper client session timeout.", required = false)
        private int zTimeout;

        @Parameter(names = {"-z", "--zookeeper"}, description = "<hosts> Zookeeper host list (comma seperated host:port pairs--no space).", required = false)
        private String zHosts;

        // Getters
        public boolean isHelp() {
            return this.help;
        }

        public String getSchema() {
            return schema;
        }

        public String getDocker() {
            return docker;
        }

        public String getXml() {
            return xml;
        }

        public String getConf() {
            return conf;
        }

        public int getzTimeout() {
            return zTimeout;
        }

        public String getzHosts() {
            return zHosts;
        }
    }

    // stop command
    @Parameters(commandDescription = "Stop application deployment")
    public class StopCmdOpt {

        @Parameter(names = {"-h", "--help"}, description = "Help for this command", help = true)
        private boolean help;

        @Parameter(description = "<app name> Name of the deployed application to stop.", required = true)
        private List<String> args;

        @Parameter(names = {"-c", "--conf"}, description = "<program conf> Path of .properties file with program's configuration.", required = false)
        private String conf;

        @Parameter(names = {"-t", "--timeout"}, description = "<session timeout> Zookeeper client session timeout.", required = false)
        private int zTimeout;

        @Parameter(names = {"-z", "--zookeeper"}, description = "<hosts> Zookeeper host list (comma seperated host:port pairs--no space).", required = false)
        private String zHosts;

        // Getters
        public boolean isHelp() {
            return this.help;
        }

        public List<String> getArgs() {
            return args;
        }

        public String getConf() {
            return conf;
        }

        public int getzTimeout() {
            return zTimeout;
        }

        public String getzHosts() {
            return zHosts;
        }
    }

    // restart command
    @Parameters(commandDescription = "Restart application deployment")
    public class RestartCmdOpt {

        @Parameter(names = {"-h", "--help"}, description = "Help for this command", help = true)
        private boolean help;

        @Parameter(description = "<app name> Name of application to restart.", required = true)
        private List<String> args;

        @Parameter(names = {"-c", "--conf"}, description = "<program conf> Path of .properties file with program's configuration.", required = false)
        private String conf;

        @Parameter(names = {"-t", "--timeout"}, description = "<session timeout> Zookeeper client session timeout.", required = false)
        private int zTimeout;

        @Parameter(names = {"-z", "--zookeeper"}, description = "<hosts> Zookeeper host list (comma seperated host:port pairs--no space).", required = false)
        private String zHosts;

        @Parameter(names = {"-d", "--docker"}, description = "<docker uri> Docker daemon uri.", required = false)
        private String docker;

        // Getters
        public boolean isHelp() {
            return this.help;
        }

        public List<String> getArgs() {
            return args;
        }

        public String getConf() {
            return conf;
        }

        public int getzTimeout() {
            return zTimeout;
        }

        public String getzHosts() {
            return zHosts;
        }

        public String getDocker() {
            return docker;
        }
    }

    // clean command
    @Parameters(commandDescription = "Clean zookeeper namespace and remove containers for undeployed application.")
    public class CleanCmdOpt {

        @Parameter(names = {"-h", "--help"}, description = "Help for this command", help = true)
        private boolean help;

        @Parameter(description = "<name> Name of the undeployed application to perfom cleaning.", required = true)
        private List<String> args;

        @Parameter(names = {"-c", "--conf"}, description = "<program conf> Path of .properties file with program's configuration.", required = false)
        private String conf;

        @Parameter(names = {"-t", "--timeout"}, description = "<session timeout> Zookeeper client session timeout.", required = false)
        private int zTimeout;

        @Parameter(names = {"-z", "--zookeeper"}, description = "<hosts> Zookeeper host list (comma seperated host:port pairs--no space).", required = false)
        private String zHosts;

        @Parameter(names = {"-d", "--docker"}, description = "<docker uri> Docker daemon uri.", required = false)
        private String docker;

        // Getters
        public boolean isHelp() {
            return this.help;
        }

        public List<String> getArgs() {
            return args;
        }

        public String getConf() {
            return conf;
        }

        public int getzTimeout() {
            return zTimeout;
        }

        public String getzHosts() {
            return zHosts;
        }

        public String getDocker() {
            return docker;
        }
    }
}
