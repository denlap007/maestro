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

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import java.io.IOException;

/**
 *
 * Class to do JSON pre-processing.
 */
public final class JsonPreprocessor {

    //The default JSON schema to validate json files
    private JsonSchema jsonSchema;
    //A json file
    private JsonNode json;

    /**
     * Validate a json file against a json schema and print a report.
     *
     * @throws com.github.fge.jsonschema.core.exceptions.ProcessingException
     */
    public void validateJson() throws ProcessingException {
        //Create a report object
        ProcessingReport report;

        //Validate given schema and print report
        report = jsonSchema.validate(json);
        System.out.println(report);
    }

    /**
     * Load a json file.
     *
     * @throws IOException
     */
    public void loadJson() throws IOException {
        //Load a json file from disk
        json = loadResource("/home/dio/DIPLWMATIKH/maestro/test_schemas/bad.json");
    }

    /**
     * Load a default json schema to validate json files.
     *
     * @throws java.io.IOException
     * @throws com.github.fge.jsonschema.core.exceptions.ProcessingException
     */
    public void initJsonSchema() throws IOException, ProcessingException {
        //Load the default schema from a file
        JsonNode loadedSchema = loadResource("/home/dio/DIPLWMATIKH/maestro/test_schemas/defaultSchema.json");

        //Create a schema and initialize the jsonSchema with it
        JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        jsonSchema = factory.getJsonSchema(loadedSchema);
    }

    /**
     * Load one resource as a {@link JsonNode}.
     *
     * @param jsonFilePath the path of the json resource
     * @return a JSON document
     * @throws IOException resource not found
     */
    public JsonNode loadResource(String jsonFilePath) throws IOException {
        return JsonLoader.fromPath(jsonFilePath);
    }

}
