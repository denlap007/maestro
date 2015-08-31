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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 *
 * Class that contains the main method.
 */
public class TheMain {

    public static void main(String[] args) throws IOException, ProcessingException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException {
        // Create a json preprocessor
        JsonPreprocessor jproc = new JsonPreprocessor();

        String schema = "/home/dio/THESIS/maestro/test_schemas/defaultSchema2.json";
        String json = "/home/dio/THESIS/maestro/test_schemas/json2.json";

        // Validate json against json schema
        Boolean isValid = jproc.validateJsonToSchema(json, schema);

        if (isValid == true) {
            System.out.println("---> [INFO] Json is valid against schema!");
        } else {
            System.out.println("---> [ERROR] Json is NOT a valid schema! Exiting");
            System.out.println(jproc.getReport());
            System.exit(1);
        }

        // Dynamically create JAVA CLASS from json 
        ClassGenerator classGen = new ClassGenerator();

        String inputFilePath = "/home/dio/THESIS/maestro/test_schemas/defaultSchema2.json";
        String className = "GeneratedClass";
        String packageName = "conf";
        String outputFilePath = "/home/dio/THESIS/maestro/src";

        String classPath = "/home/dio/THESIS/maestro/src/conf/";

        classGen.generateClass(inputFilePath, className, packageName, outputFilePath);

        // Dynamically load class
        String classToLoadName = packageName + "." + className;

        Object newClass = classGen.loadInstantiateClass(classToLoadName);
       //Class<?> classObj = classGen.loadClass(classToLoadName);
        // Object newClass = classGen.instantiateCLass(classObj);

        // Parse Json from file and load values to class
        ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
        newClass = mapper.readValue(new File("/home/dio/THESIS/maestro/test_schemas/json2.json"), newClass.getClass());
        System.out.println("---> [INFO] Initialized \'" + newClass.getClass() + "\' object with json data! Printing object: \n" + newClass);

    }

}
