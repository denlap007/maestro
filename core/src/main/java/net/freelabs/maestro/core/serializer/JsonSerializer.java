/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.freelabs.maestro.core.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.Charset;
import net.freelabs.maestro.core.generated.Container;

/**
 *
 * Serializes and de-serializes object to byte arrays
 */
public class JsonSerializer {
    // set the default encoding to be used
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /**
     * Create json from container.
     * @param con the container from which the json will be created.
     * @return the json.
     * @throws JsonProcessingException if there is an exception during processing.
     */
    public static String toJson(Container con) throws JsonProcessingException{
        MAPPER.configure(FAIL_ON_EMPTY_BEANS, false);
        
        // create json from container and save it to string 
        return MAPPER.writeValueAsString(con);
    }
    
 
    public static String addNewKV(String json, String key, String value) throws JsonProcessingException, IOException {
        ObjectNode objNode = (ObjectNode) MAPPER.readTree(json);
        objNode.put(key, value);
        return MAPPER.writeValueAsString(objNode);
    }

    /**
     * Serialize a string to byte array based on the default encoding.
     * @param json the json to serialize.
     * @return a byte array of the serialized string.
     */
    public static byte[] serialize(String json) {
        // convert string to bytes with default encoding
        return json.getBytes(UTF8_CHARSET);
    }

    public static String deserialize(byte[] data) {
        String jsonString = new String(data, UTF8_CHARSET);
        
        return jsonString;
    }

}
