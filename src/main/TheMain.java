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

        String schema = "/home/dio/THESIS/maestro/test_schemas/defaultSchema4.json";
        String json = "/home/dio/THESIS/maestro/test_schemas/json4.json";

        //Validate json against json schema
        Boolean isValid = jproc.validateJsonToSchema(json, schema);

       if (isValid == true)
            System.out.println("---> [INFO] Json is valid against schema!");
        else{
            System.out.println("---> [ERROR] Json is NOT a valid schema! Exiting");
            System.out.println(jproc.getReport());
            System.exit(1);
        }
        
        

        /*
         //Dynamically create JAVA CLASS
         jproc.generateClass(json, json, json, json);
        
        
        
        
        
         JCodeModel codeModel = new JCodeModel();
        
        

         URL source = new File("/home/dio/THESIS/maestro/test_schemas/defaultSchema3.json").toURI().toURL();

         new SchemaMapper().generate(codeModel, "CreatedClass", "main", source);

         codeModel.build(new File("/home/dio/THESIS/maestro/src/main"));

         System.out.println("New Java Class created successfully");
                
         */
    }

}
