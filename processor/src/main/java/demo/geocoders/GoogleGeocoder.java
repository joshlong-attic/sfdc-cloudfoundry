package demo.geocoders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple service that delegates to the Google Maps geolocation API
 *
 * @author Josh Long
 */
// @Component
public class GoogleGeocoder implements Geocoder {

    private static Logger log = Logger.getLogger("GoogleGeocoder");
    private RestTemplate restTemplate;
    private String urlPath = "http://maps.googleapis.com/maps/api/geocode/json?address={address}&sensor=false";

    public GoogleGeocoder(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        Assert.notNull(this.restTemplate, "the restTemplate must not be null!");
    }

    public GoogleGeocoder() {
        this(new RestTemplate());
    }

    public static void main(String args[]) throws Throwable {
        GoogleGeocoder googleGeocoder = new GoogleGeocoder();
        Geocoder.LatLong home = googleGeocoder.geocode("875 Howard St., San Francisco, CA, 94109");
        Geocoder.LatLong zip = googleGeocoder.geocode("94109");
        Geocoder.LatLong city = googleGeocoder.geocode("San Francisco, CA");
        for (Geocoder.LatLong latLong : Arrays.asList(home, zip, city)) {
            log.debug(latLong.toString());
        }
    }

    public Geocoder.LatLong geocode(String address) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("address", address);
            ResponseEntity<JsonNode> jsonNodeResponseEntity = this.restTemplate.getForEntity(
                    this.urlPath, JsonNode.class, vars);
            JsonNode body = jsonNodeResponseEntity.getBody();
            if (jsonNodeResponseEntity.getStatusCode().equals(HttpStatus.OK)
                    && body.path("status").textValue().equalsIgnoreCase("OK")) {
                if (body.path("results").size() > 0) {
                    String formattedAddress = body.path("results").get(0).get("formatted_address").textValue();
                    DoubleNode lngNode = (DoubleNode) body.path("results").get(0).path("geometry").path("location").get("lng");
                    DoubleNode latNode = (DoubleNode) body.path("results").get(0).path("geometry").path("location").get("lat");
                    log.debug(String.format("formatted address: %s", formattedAddress));
                    return new Geocoder.LatLong(latNode.doubleValue(), lngNode.doubleValue());
                }
            }
        } catch (Exception ex) {
            log.debug("exception when processing address '" + address + "'", ex);
        }
        return null;
    }


}
