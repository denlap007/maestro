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
package main;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import java.io.IOException;

/**
 *
 * Class that contains the main method.
 */
public class TheMain {

    public static void main(String[] args) throws IOException, ProcessingException {
        //Create a json preprocessor
        JsonPreprocessor jproc = new JsonPreprocessor();
        
        //Initialize the preprocessor with a JSON schema
        jproc.initJsonSchema();
        
        //Load a JSON file to validate against the loaded JSON schema 
        jproc.loadJson();
        
        //Validate loaded JSON file against loaded JSON schema
        jproc.validateJson();

    }

}
