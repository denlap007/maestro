//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.5-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.12.10 at 10:04:54 PM EET 
//


package net.freelabs.maestro.core.generated;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for webContainer complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="webContainer"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{}container"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="environment" type="{}webEnvironment"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "webContainer", propOrder = {
    "environment"
})
public class WebContainer
    extends Container
{

    @XmlElement(required = true)
    protected WebEnvironment environment;

    /**
     * Gets the value of the environment property.
     * 
     * @return
     *     possible object is
     *     {@link WebEnvironment }
     *     
     */
    public WebEnvironment getEnvironment() {
        return environment;
    }

    /**
     * Sets the value of the environment property.
     * 
     * @param value
     *     allowed object is
     *     {@link WebEnvironment }
     *     
     */
    public void setEnvironment(WebEnvironment value) {
        this.environment = value;
    }

}
