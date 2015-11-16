/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.freelabs.maestro.core.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import java.io.UnsupportedEncodingException;
import net.freelabs.maestro.core.generated.Container;

/**
 *
 * Serializes and de-serializes object to byte arrays
 */
public class JsonSerializer {
    // set the default encoding to be used
    private static final String ENCODING = "UTF-8";
    
    /**
     * Create json from container.
     * @param con the container from which the json will be created.
     * @return the json.
     * @throws JsonProcessingException if there is an exception during processing.
     */
    public static String toJson(Container con) throws JsonProcessingException{
        ObjectMapper objMapper = new ObjectMapper();
        objMapper.configure(FAIL_ON_EMPTY_BEANS, false);
        
        // create json from container and save it to string 
        return objMapper.writeValueAsString(con);
    }

    /**
     * Serialize a string to byte array based on the default encoding.
     * @param json the json to serialize.
     * @return a byte array of the serialized string.
     * @throws UnsupportedEncodingException if the encoding is not supported.
     */
    public static byte[] serialize(String json) throws UnsupportedEncodingException {
        // convert string to bytes with default encoding
        return json.getBytes(ENCODING);
    }

    public static String deserialize(byte[] data) throws UnsupportedEncodingException {
        String jsonString = new String(data, ENCODING);
        
        return jsonString;
    }

}
