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
package net.freelabs.maestro.broker.tasks;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

/**
 *
 * Class that provides a tool. The tool expands the environment variables 
 * in configuration files. Only environment variables declared in application 
 * schema are expanded. Example: ${WEB_HOST_IP} -> 172.17.0.3
 *
 * <p>
 * The environment variables used in files must be written in the following
 * format: ${ENV_VAR}. No other format will be acceptable and substituted.
 *
 */
public final class SubstEnv implements Task {

    /**
     * The paths of the files declared for environment variable substitution.
     */
    private final List<String> paths;
    /**
     * The environment of the processes that will be used to substitute the
     * values of the environment variables to files.
     */
    private final Map<String, String> env;
    /**
     * A Logger object.
     */
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SubstEnv.class);
    /**
     * The pattern to match for environment variables. Format matched:
     * ${ENV_VAR}
     */
    private final Pattern regex = Pattern.compile("\\$\\{([A-Za-z0-9_]+)\\}");
    /**
     * The charset to use for read/write on files.
     */
    private static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;

    /**
     * Constructor
     *
     * @param paths list of file paths to substitute environment variables.
     * @param env the map of the environment variables declared in application
     * schema.
     */
    public SubstEnv(List<String> paths, Map<String, String> env) {
        this.paths = paths;
        this.env = env;
    }

    @Override
    public void run() {
        // iterate though paths and check that it is a file and exists
        paths.stream().filter((path) -> (isFileOk(path))).forEach((path) -> {
            try {
                // read file and get the content    
                String fileContent = new String(Files.readAllBytes(Paths.get(path)), UTF8_CHARSET);
                // create object to match pattern
                Matcher regexMatcher = regex.matcher(fileContent);
                while (regexMatcher.find()) {
                    // get the name of the environment variable matched, eg: $test->test
                    String envVarName = regexMatcher.group(1);
                    // search the environment for the variable name and get value
                    String envVarValue = env.get(envVarName);
                    // check if there was such variable
                    if (envVarValue != null) {
                        // expand environment variabe
                        String envVar = regexMatcher.group();
                        fileContent = fileContent.replace(envVar, envVarValue);
                    }
                }
                // write updated file to disk
                Files.write(Paths.get(path), fileContent.getBytes(UTF8_CHARSET));
                LOG.info("Updated ENV VARS to file: {}", path);
            } catch (IOException ex) {
                LOG.error("FAILED to read file: {}. {}", path, ex.getMessage());
            }
        });
    }

    /**
     * Checks if the path represents a file and the file exists.
     *
     * @param path the path to check
     * @return true if path represents a file and the file exists.
     */
    private boolean isFileOk(String path) {
        return !Files.isDirectory(Paths.get(path));
    }

}