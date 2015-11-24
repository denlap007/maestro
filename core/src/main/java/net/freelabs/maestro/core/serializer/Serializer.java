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
package net.freelabs.maestro.core.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import net.freelabs.maestro.core.generated.Container;

/**
 *
 * Class that provides methods to do serialization/de-serialization.
 */
@Deprecated
public final class Serializer {

    public static class JsonSerializer {

        // set the default charset to be used
        private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

        /**
         * Create json from container.
         *
         * @param con the container from which the json will be created.
         * @return the json.
         * @throws JsonProcessingException if there is an exception during
         * processing.
         */
        public static String toJson(Container con) throws JsonProcessingException {
            ObjectMapper objMapper = new ObjectMapper();
            objMapper.configure(FAIL_ON_EMPTY_BEANS, false);

            // create json from container and save it to string 
            return objMapper.writeValueAsString(con);
        }

        /**
         * Serialize a string to byte array based on the default encoding.
         *
         * @param str the String to serialize.
         * @return a byte array of the serialized string.
         */
        public static byte[] serialize(String str) {
            // convert string to bytes with default encoding
            return str.getBytes(UTF8_CHARSET);
        }

        /**
         * Deserialize a byte array to string based on the default encoding.
         *
         * @param data the data to deserialize.
         * @return a string of the deserialized byte array.
         */
        public static String deserialize(byte[] data) {
            return (new String(data, UTF8_CHARSET));
        }
    }

    public static class JavaSerializer {

        /**
         * Serialize an Object.
         *
         * @param obj the object to be serialized.
         * @return the bytes representing the object.
         * @throws IOException
         */
        public static byte[] serialize(Object obj) throws IOException {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ObjectOutputStream o = new ObjectOutputStream(b);
            o.writeObject(obj);
            return b.toByteArray();
        }

        /**
         * De-serialize an object.
         *
         * @param bytes the bytes representation of the object.
         * @return the de-serialized object.
         * @throws IOException if an I/O error occurs while reading stream
         * header.
         * @throws java.lang.ClassNotFoundException Class of a serialized object
         * cannot be found.
         */
        public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
            ByteArrayInputStream b = new ByteArrayInputStream(bytes);
            ObjectInputStream o = new ObjectInputStream(b);
            return o.readObject();
        }
    }

}
