//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.5-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.02.25 at 10:49:35 PM EET 
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
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;


/**
 * <p>Java class for container complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="container">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}token"/>
 *         &lt;element name="connectWith" type="{}stringList"/>
 *         &lt;element name="dockerImage" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="start" type="{}resources"/>
 *         &lt;element name="stop" type="{}resources" minOccurs="0"/>
 *         &lt;element name="tasks" type="{}tasks" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "container", propOrder = {
    "name",
    "connectWith",
    "dockerImage",
    "start",
    "stop",
    "tasks"
})
@XmlSeeAlso({
    BusinessContainer.class,
    DataContainer.class,
    WebContainer.class
})
public abstract class Container {

    @XmlElement(required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlSchemaType(name = "token")
    protected String name;
    @XmlList
    @XmlElement(required = true)
    protected List<String> connectWith;
    @XmlElement(required = true)
    protected String dockerImage;
    @XmlElement(required = true)
    protected Resources start;
    protected Resources stop;
    protected Tasks tasks;

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the connectWith property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the connectWith property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getConnectWith().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
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
     * Gets the value of the dockerImage property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDockerImage() {
        return dockerImage;
    }

    /**
     * Sets the value of the dockerImage property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDockerImage(String value) {
        this.dockerImage = value;
    }

    /**
     * Gets the value of the start property.
     * 
     * @return
     *     possible object is
     *     {@link Resources }
     *     
     */
    public Resources getStart() {
        return start;
    }

    /**
     * Sets the value of the start property.
     * 
     * @param value
     *     allowed object is
     *     {@link Resources }
     *     
     */
    public void setStart(Resources value) {
        this.start = value;
    }

    /**
     * Gets the value of the stop property.
     * 
     * @return
     *     possible object is
     *     {@link Resources }
     *     
     */
    public Resources getStop() {
        return stop;
    }

    /**
     * Sets the value of the stop property.
     * 
     * @param value
     *     allowed object is
     *     {@link Resources }
     *     
     */
    public void setStop(Resources value) {
        this.stop = value;
    }

    /**
     * Gets the value of the tasks property.
     * 
     * @return
     *     possible object is
     *     {@link Tasks }
     *     
     */
    public Tasks getTasks() {
        return tasks;
    }

    /**
     * Sets the value of the tasks property.
     * 
     * @param value
     *     allowed object is
     *     {@link Tasks }
     *     
     */
    public void setTasks(Tasks value) {
        this.tasks = value;
    }

}
