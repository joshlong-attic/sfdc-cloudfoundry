package demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
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
@Component
public class GeolocationService {

    private static Logger log = Logger.getLogger("GeolocationService");
    private RestTemplate restTemplate;
    private String urlPath = "http://maps.googleapis.com/maps/api/geocode/json?address={address}&sensor=false";

    public GeolocationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        Assert.notNull(this.restTemplate, "the restTemplate must not be null!");
    }

    public GeolocationService() {
        this(new RestTemplate());
    }

    public static void main(String args[]) throws Throwable {
        GeolocationService geolocationService = new GeolocationService();
        LatLong home = geolocationService.geocode("875 Howard St., San Francisco, CA, 94109");
        LatLong zip = geolocationService.geocode("94109");
        LatLong city = geolocationService.geocode("San Francisco, CA");
        for (LatLong latLong : Arrays.asList(home, zip, city)) {
            log.debug(latLong.toString());
        }
    }


    public LatLong geocode(String address) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("address", address);
            ResponseEntity<JsonNode> jsonNodeResponseEntity = this.restTemplate.getForEntity(this.urlPath, JsonNode.class, vars);
            JsonNode body = jsonNodeResponseEntity.getBody();
            if (jsonNodeResponseEntity.getStatusCode().equals(HttpStatus.OK)
                    && body.path("status").textValue().equalsIgnoreCase("OK")) {
                if (body.path("results").size() > 0) {
                    String formattedAddress = body.path("results").get(0).get("formatted_address").textValue();
                    DoubleNode lngNode = (DoubleNode) body.path("results").get(0).path("geometry").path("location").get("lng");
                    DoubleNode latNode = (DoubleNode) body.path("results").get(0).path("geometry").path("location").get("lat");
                    log.debug(String.format("formatted address: %s", formattedAddress));
                    return new LatLong(latNode.doubleValue(), lngNode.doubleValue());
                }
            }
        } catch (Exception ex) {
            log.debug("exception when processing address '" + address + "'",ex);
        }
        return null;
    }


    public static class LatLong {
        private double latitude, longitude;

        public LatLong(double latitude, double longitude) {

            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override
        public String toString() {
            return " {" +
                    "latitude :" + latitude +
                    ", longitude : " + longitude +
                    '}';
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }
}
