//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.5-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.01.10 at 04:27:31 PM EET 
//
package net.freelabs.maestro.core.generated;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * <p>
 * Java class for container complex type.
 *
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 *
 * <pre>
 * &lt;complexType name="container"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}token"/&gt;
 *         &lt;element name="connectWith" type="{}stringList"/&gt;
 *         &lt;element name="confFilePath" type="{}stringList" minOccurs="0"/&gt;
 *         &lt;element name="dockerImage" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="entrypointPath" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="entrypointCmd" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="entrypointArgs" type="{}stringList"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "container", propOrder = {
    "name",
    "connectWith",
    "confFilePath",
    "dockerImage",
    "entrypointPath",
    "entrypointArgs"
})
@XmlSeeAlso({
    DataContainer.class,
    WebContainer.class,
    BusinessContainer.class
})
public class Container {

    @XmlElement(required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlSchemaType(name = "token")
    protected String name;
    @XmlList
    @XmlElement(required = true)
    protected List<String> connectWith;
    @XmlList
    protected List<String> confFilePath;
    @XmlElement(required = true)
    protected String dockerImage;
    @XmlElement(required = true)
    protected String entrypointPath;
    @XmlList
    @XmlElement(required = true)
    protected List<String> entrypointArgs;
    @XmlTransient
    protected String IP;

    public String getIP() {
        return IP;
    }

    public void setIP(String IP) {
        this.IP = IP;
    }

    /**
     * Gets the value of the name property.
     *
     * @return possible object is {@link String }
     *
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     *
     * @param value allowed object is {@link String }
     *
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the connectWith property.
     *
     * <p>
     * This accessor method returns a reference to the live list, not a
     * snapshot. Therefore any modification you make to the returned list will
     * be present inside the JAXB object. This is why there is not a
     * <CODE>set</CODE> method for the connectWith property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getConnectWith().add(newItem);
     * </pre>
     *
     *
     * <p>
     * Objects of the following type(s) are allowed in the list {@link String }
     *
     *
     */
    public List<String> getConnectWith() {
        if (connectWith == null) {
            connectWith = new ArrayList<String>();
        }
        return this.connectWith;
    }

    /**
     * Gets the value of the confFilePath property.
     *
     * <p>
     * This accessor method returns a reference to the live list, not a
     * snapshot. Therefore any modification you make to the returned list will
     * be present inside the JAXB object. This is why there is not a
     * <CODE>set</CODE> method for the confFilePath property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getConfFilePath().add(newItem);
     * </pre>
     *
     *
     * <p>
     * Objects of the following type(s) are allowed in the list {@link String }
     *
     *
     */
    public List<String> getConfFilePath() {
        if (confFilePath == null) {
            confFilePath = new ArrayList<String>();
        }
        return this.confFilePath;
    }

    /**
     * Gets the value of the dockerImage property.
     *
     * @return possible object is {@link String }
     *
     */
    public String getDockerImage() {
        return dockerImage;
    }

    /**
     * Sets the value of the dockerImage property.
     *
     * @param value allowed object is {@link String }
     *
     */
    public void setDockerImage(String value) {
        this.dockerImage = value;
    }

    /**
     * Gets the value of the entrypointPath property.
     *
     * @return possible object is {@link String }
     *
     */
    public String getEntrypointPath() {
        return entrypointPath;
    }

    /**
     * Sets the value of the entrypointPath property.
     *
     * @param value allowed object is {@link String }
     *
     */
    public void setEntrypointPath(String value) {
        this.entrypointPath = value;
    }

    /**
     * Gets the value of the entrypointArgs property.
     *
     * <p>
     * This accessor method returns a reference to the live list, not a
     * snapshot. Therefore any modification you make to the returned list will
     * be present inside the JAXB object. This is why there is not a
     * <CODE>set</CODE> method for the entrypointArgs property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEntrypointArgs().add(newItem);
     * </pre>
     *
     *
     * <p>
     * Objects of the following type(s) are allowed in the list {@link String }
     *
     *
     */
    public List<String> getEntrypointArgs() {
        if (entrypointArgs == null) {
            entrypointArgs = new ArrayList<String>();
        }
        return this.entrypointArgs;
    }

}
