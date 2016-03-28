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
package net.freelabs.maestro.core.serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import net.freelabs.maestro.core.generated.BusinessContainer;
import net.freelabs.maestro.core.generated.Container;
import net.freelabs.maestro.core.generated.DataContainer;
import net.freelabs.maestro.core.generated.ObjectFactory;
import net.freelabs.maestro.core.generated.WebContainer;
import net.freelabs.maestro.core.zookeeper.ZkConf;

/**
 *
 * @author Dionysis Lappas <dio@freelabs.net>
 */
public final class JAXBSerializer {

    private static final Unmarshaller unmarshaller = initUnmarshaller();
    private static final Marshaller marshaller = initMarshaller();

    private static Marshaller initMarshaller() {
        Marshaller marshall = null;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class, ZkConf.class);
            marshall = jaxbContext.createMarshaller();
            marshall.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.name());
            marshall.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        } catch (JAXBException ex) {
        }
        return marshall;
    }

    private static Unmarshaller initUnmarshaller() {
        Unmarshaller unmarshall = null;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class, ZkConf.class);
            unmarshall = jaxbContext.createUnmarshaller();
        } catch (JAXBException ex) {
        }
        return unmarshall;
    }

    public static byte[] serialize(Container con) throws JAXBException {
        JAXBElement<Container> jaxbElem = new JAXBElement<>(new QName(Container.class.getSimpleName()), Container.class, con);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        marshaller.marshal(jaxbElem, baos);

        return baos.toByteArray();
    }

    public static byte[] serialize(ZkConf zkConf) throws JAXBException {
        JAXBElement<ZkConf> jaxbElem = new JAXBElement<>(new QName(ZkConf.class.getSimpleName()), ZkConf.class, zkConf);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        marshaller.marshal(jaxbElem, baos);

        return baos.toByteArray();
    }

    public static ZkConf deserializeToZkConf(byte[] data) throws JAXBException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        JAXBElement<ZkConf> jaxbElemUnmar = unmarshaller.unmarshal(new StreamSource(bais), ZkConf.class); 
        return jaxbElemUnmar.getValue();
    }

    public static DataContainer deserializeToDataContainer(byte[] data) throws JAXBException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        JAXBElement<DataContainer> jaxbElemUnmar = unmarshaller.unmarshal(new StreamSource(bais), DataContainer.class);
        return jaxbElemUnmar.getValue();
    }

    public static WebContainer deserializeToWebContainer(byte[] data) throws JAXBException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        JAXBElement<WebContainer> jaxbElemUnmar = unmarshaller.unmarshal(new StreamSource(bais), WebContainer.class);
        return jaxbElemUnmar.getValue();
    }

    public static BusinessContainer deserializeToBusinessContainer(byte[] data) throws JAXBException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        JAXBElement<BusinessContainer> jaxbElemUnmar = unmarshaller.unmarshal(new StreamSource(bais), BusinessContainer.class);
        return jaxbElemUnmar.getValue();
    }

    public static String deserializeToString(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

}
