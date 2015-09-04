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
import java.util.ArrayList;

/**
 *
 * Class that contains the main method.
 */
public class TheMainJson {

    public static void main(String[] args) throws IOException, ProcessingException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException {
        // Create a json preprocessor
        JsonPreprocessor jproc = new JsonPreprocessor();

        String schema = "/home/dio/THESIS/maestro/newSchemas/final_schema2.json";
        String json = "/home/dio/THESIS/maestro/newSchemas/test_final_schema.json";

        // Validate json against json schema
        Boolean isValid = jproc.validateJsonToSchema(json, schema);

        if (isValid == true) {
            System.out.println("[INFO] Json is valid against schema!");
        } else {
            System.out.println("[ERROR] Json is NOT a valid schema! Exiting");
            System.out.println(jproc.getReport());
            System.exit(1);
        }

        // Dynamically create JAVA CLASS from json 
        String inputFilePath = json;
        String className = "GeneratedClass";
        String packageName = "conf";
        String outputFilePath = "/home/dio/testClass/source"; 

        ConfProcessor classGen = new ConfProcessor();
        jproc.jsonToClass(inputFilePath, className, packageName, outputFilePath);

        // Compile a .java source file
        String class_path = "/home/dio/testClass/class";
        String source_path = "/home/dio/testClass/source/conf/GeneratedClass.java";

        //classGen.compile(source_path, class_path);
        //classGen.compile2(source_path, class_path);

        // Dynamically load and instantiate classes
        ArrayList<Object> objList = classGen.loadInstantiateClass("/home/dio/testClass/class/conf/");


        /* WORKS FINE but compiled file is on same folder
         int compilationResult = compiler.run(null, null, null, fileToCompile);
            
         if (compilationResult == 0) {
         System.out.println("Compilation is successful");
         } else {
         System.out.println("Compilation Failed");
         }
         */
        Object newClass = null;
        for (Object obj : objList) {
            if (obj.getClass().getSimpleName().equals("GeneratedClass")) {
                newClass = obj;
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        newClass = mapper.readValue(new File(json), newClass.getClass());
        System.out.println("[INFO] main(): Initialized an object of class \'" + newClass.getClass().getSimpleName() + "\'  with json data! Printing object: \n" + newClass);

        // VALID METHOD USED NORMALLY
        //Object newClass = classGen.loadInstantiateClass(classToLoadName);
            /*  LOAD CLASS WITH TWO METHODS
         Class<?> classObj = classGen.loadClass(classToLoadName);
         Object newClass = classGen.instantiateCLass(classObj);
         */
        // Parse Json from file and load values to class
        //ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
        //newClass = mapper.readValue(new File("/home/dio/THESIS/maestro/test_schemas/json2.json"), newClass.getClass());
        //System.out.println("---> [INFO] Initialized \'" + newClass.getClass() + "\' object with json data! Printing object: \n" + newClass);
    }

}
