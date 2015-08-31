
package conf;

import javax.annotation.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Example Schema
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "firstName",
    "lastName",
    "age"
})
public class GeneratedClass {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("firstName")
    private String firstName;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("lastName")
    private String lastName;
    /**
     * Age in years
     * 
     */
    @JsonProperty("age")
    private Integer age;

    /**
     * 
     * (Required)
     * 
     * @return
     *     The firstName
     */
    @JsonProperty("firstName")
    public String getFirstName() {
        return firstName;
    }

    /**
     * 
     * (Required)
     * 
     * @param firstName
     *     The firstName
     */
    @JsonProperty("firstName")
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The lastName
     */
    @JsonProperty("lastName")
    public String getLastName() {
        return lastName;
    }

    /**
     * 
     * (Required)
     * 
     * @param lastName
     *     The lastName
     */
    @JsonProperty("lastName")
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Age in years
     * 
     * @return
     *     The age
     */
    @JsonProperty("age")
    public Integer getAge() {
        return age;
    }

    /**
     * Age in years
     * 
     * @param age
     *     The age
     */
    @JsonProperty("age")
    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(firstName).append(lastName).append(age).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof GeneratedClass) == false) {
            return false;
        }
        GeneratedClass rhs = ((GeneratedClass) other);
        return new EqualsBuilder().append(firstName, rhs.firstName).append(lastName, rhs.lastName).append(age, rhs.age).isEquals();
    }

}
