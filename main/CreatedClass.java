
package main;

import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * An Address following the convention of http://microformats.org/wiki/hcard
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "post-office-box",
    "extended-address",
    "street-address",
    "locality",
    "region",
    "postal-code",
    "country-name"
})
public class CreatedClass {

    @JsonProperty("post-office-box")
    private String postOfficeBox;
    @JsonProperty("extended-address")
    private String extendedAddress;
    @JsonProperty("street-address")
    private String streetAddress;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("locality")
    private String locality;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("region")
    private String region;
    @JsonProperty("postal-code")
    private String postalCode;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("country-name")
    private String countryName;

    /**
     * 
     * @return
     *     The postOfficeBox
     */
    @JsonProperty("post-office-box")
    public String getPostOfficeBox() {
        return postOfficeBox;
    }

    /**
     * 
     * @param postOfficeBox
     *     The post-office-box
     */
    @JsonProperty("post-office-box")
    public void setPostOfficeBox(String postOfficeBox) {
        this.postOfficeBox = postOfficeBox;
    }

    /**
     * 
     * @return
     *     The extendedAddress
     */
    @JsonProperty("extended-address")
    public String getExtendedAddress() {
        return extendedAddress;
    }

    /**
     * 
     * @param extendedAddress
     *     The extended-address
     */
    @JsonProperty("extended-address")
    public void setExtendedAddress(String extendedAddress) {
        this.extendedAddress = extendedAddress;
    }

    /**
     * 
     * @return
     *     The streetAddress
     */
    @JsonProperty("street-address")
    public String getStreetAddress() {
        return streetAddress;
    }

    /**
     * 
     * @param streetAddress
     *     The street-address
     */
    @JsonProperty("street-address")
    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The locality
     */
    @JsonProperty("locality")
    public String getLocality() {
        return locality;
    }

    /**
     * 
     * (Required)
     * 
     * @param locality
     *     The locality
     */
    @JsonProperty("locality")
    public void setLocality(String locality) {
        this.locality = locality;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The region
     */
    @JsonProperty("region")
    public String getRegion() {
        return region;
    }

    /**
     * 
     * (Required)
     * 
     * @param region
     *     The region
     */
    @JsonProperty("region")
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * 
     * @return
     *     The postalCode
     */
    @JsonProperty("postal-code")
    public String getPostalCode() {
        return postalCode;
    }

    /**
     * 
     * @param postalCode
     *     The postal-code
     */
    @JsonProperty("postal-code")
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The countryName
     */
    @JsonProperty("country-name")
    public String getCountryName() {
        return countryName;
    }

    /**
     * 
     * (Required)
     * 
     * @param countryName
     *     The country-name
     */
    @JsonProperty("country-name")
    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(postOfficeBox).append(extendedAddress).append(streetAddress).append(locality).append(region).append(postalCode).append(countryName).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof CreatedClass) == false) {
            return false;
        }
        CreatedClass rhs = ((CreatedClass) other);
        return new EqualsBuilder().append(postOfficeBox, rhs.postOfficeBox).append(extendedAddress, rhs.extendedAddress).append(streetAddress, rhs.streetAddress).append(locality, rhs.locality).append(region, rhs.region).append(postalCode, rhs.postalCode).append(countryName, rhs.countryName).isEquals();
    }

}
