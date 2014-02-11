package demo;

import org.springframework.util.Assert;

/**
 * Meant to represent a record from SFDC (e.g., a contact or a lead)
 *
 * @author Josh Long (josh@joshlong.com)
 */
class SfdcPerson {
    private String firstName, lastName, street, email, city, state, postalCode, batchId, recordType;
    private double latitude, longitude;

    public SfdcPerson(String firstName, String lastName, String street, String email, String city, String state, String postalCode, String batchId, String recordType, double latitude, double longitude) {
        this.street = street;
        this.firstName = firstName;
        this.lastName = lastName;

        this.email = email;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.batchId = batchId;
        this.latitude = latitude;
        this.longitude = longitude;

        Assert.hasText(recordType);
        this.recordType = recordType.toLowerCase();

        Assert.isTrue(this.recordType.equalsIgnoreCase("lead") ||
                this.recordType.equalsIgnoreCase("contact"));
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getStreet() {
        return street;
    }

    public String getEmail() {
        return email;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getBatchId() {
        return batchId;
    }

    public String getRecordType() {
        return recordType;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
