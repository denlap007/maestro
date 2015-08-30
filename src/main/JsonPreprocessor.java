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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.sun.codemodel.JCodeModel;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsonschema2pojo.SchemaMapper;

/**
 *
 * Class to do JSON pre-processing.
 */
public final class JsonPreprocessor {

    /**
     * Default Class constructor.
     */
    public JsonPreprocessor() {
        factory = JsonSchemaFactory.byDefault();
        report = null;
    }

    /**
     * The main validator provider.
     */
    private final JsonSchemaFactory factory;
    /**
     * The report after validating json against json schema.
     */
    private ProcessingReport report;

    /**
     * Validate json file against json schema.
     *
     * @param json the json file to validate. Valid Object types: String,
     * Reader, URL, File.
     * @param schema the json schema to validate against. Valid Object types:
     * String, Reader, URL, File.
     * @return true, if json file is valid against json schema.
     * @throws ProcessingException
     * @throws java.io.IOException
     */
    public Boolean validateJsonToSchema(Object json, Object schema) throws ProcessingException, IOException {
        //Load json as JsonNode resource
        JsonNode theJson = loadJsonFile(json);
        //Load schema as JsonNode resource
        JsonNode theSchema = loadJsonSchema(schema);

        //Get a single-schema instance validator. It is a JsonValidator 
        //initialized with a single JSON Schema
        JsonSchema schemaValidator = factory.getJsonSchema(theSchema);

        //Get the report of validation
        report = schemaValidator.validate(theJson);

        return report.isSuccess();
    }

    /**
     * Get the report of validating json file against json schema.
     *
     * @return the validation report.
     * @throws IOException
     * @throws com.github.fge.jsonschema.core.exceptions.ProcessingException
     */
    public ProcessingReport getReport() throws IOException, ProcessingException {
        return report;
    }

    /**
     * Load a json file as {@link JsonNode} resource. The resource to load the
     * json file from is passed as an Object and may be one of the following
     * instances: String, File, Reader, URL.
     *
     * @param obj the json file resource may be one of instances: String, File,
     * Reader, URL.
     * @return a {@link JsonNode} resource.
     * @throws IOException
     */
    public JsonNode loadJsonFile(Object obj) throws IOException {
        return loadJsonResource(obj);
    }

    /**
     * Load a default json schema as {@link JsonNode} resource. The resource to
     * load the json schema from is passed as an Object and may be one of the
     * following instances: String, File, Reader, URL.
     *
     * @param obj the json schema resource may be one of instances of: String,
     * File, Reader, URL
     * @return a {@link JsonNode} resource.
     * @throws java.io.IOException
     * @throws com.github.fge.jsonschema.core.exceptions.ProcessingException
     */
    public JsonNode loadJsonSchema(Object obj) throws IOException, ProcessingException {
        return loadJsonResource(obj);
    }

    /**
     * Load one resource as a {@link JsonNode} from an object. The object may
     * be: String, File, Reader, URL.
     *
     * @param obj the obj to load as a json resource
     * @return a {@link JsonNode} resource.
     */
    public JsonNode loadJsonResource(Object obj) {
        try {
            if (obj instanceof java.lang.String) {
                return JsonLoader.fromPath((String) obj);
            } else if (obj instanceof java.io.File) {
                return JsonLoader.fromFile((File) obj);
            } else if (obj instanceof java.io.Reader) {
                return JsonLoader.fromReader((Reader) obj);
            } else if (obj instanceof java.net.URL) {
                return JsonLoader.fromURL((URL) obj);
            } else {
                return null;
            }
        } catch (JsonParseException ex) {
            System.err.println("---> [ERROR] INVALID SYNTAX. Unable to load resource. Exiting...");
            System.exit(1);
        } catch (IOException ex) {
            Logger.getLogger(JsonPreprocessor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * Load one resource as a {@link JsonNode} from a file on the local
     * filesystem.
     *
     * @param jsonFilePath the file path of the json resource.
     * @return a {@link JsonNode} resource.
     * @throws IOException resource not found
     */
    public JsonNode loadResource(String jsonFilePath) throws IOException {
        return JsonLoader.fromPath(jsonFilePath);
    }

    /**
     * Load one resource as a {@link JsonNode} from a {@link File} object
     *
     * @param jsonFile the json file object as json resource.
     * @return a {@link JsonNode} resource.
     * @throws IOException
     */
    public JsonNode loadResource(File jsonFile) throws IOException {
        return JsonLoader.fromFile(jsonFile);
    }

    /**
     * Load one resource as a {@link JsonNode} from a {@link Reader} object.
     *
     * @param reader the reader object as json resource.
     * @return a {@link JsonNode} resource.
     * @throws IOException
     */
    public JsonNode loadResource(Reader reader) throws IOException {
        return JsonLoader.fromReader(reader);
    }

    /**
     * Load one resource as a {@link JsonNode} from a {@link URL}.
     *
     * @param url the url as json resource.
     * @return a {@link JsonNode} resource.
     * @throws IOException
     */
    public JsonNode loadResource(URL url) throws IOException {
        return JsonLoader.fromURL(url);
    }

    /**
     * Generate a new Java Class from json schema
     *
     * @param sourceFilePath the file path of the json schema to be used as
     * input
     * @param className the name of the new Java Class to be generated
     * @param packageName the target package that should be used for generated
     * types
     * @param outputFilePath the file path of the generated Java Class
     */
    public void generateClass(String sourceFilePath, String className, String packageName, String outputFilePath) {
        //The java code-generation context that should be used to generated new types
        JCodeModel codeModel = new JCodeModel();

        try {
            //Create a Url resource
            URL sourceUrl = new File(sourceFilePath).toURI().toURL();

            //Read a schema and adds generated types to the given code model.
            new SchemaMapper().generate(codeModel, className, packageName, sourceUrl);

            codeModel.build(new File(outputFilePath));
        } catch (MalformedURLException ex) {
            System.err.println("---> [ERROR]: This is not a corrent URL form. Exiting");
            System.exit(1);
        } catch (IOException ex) {
            Logger.getLogger(JsonPreprocessor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}

