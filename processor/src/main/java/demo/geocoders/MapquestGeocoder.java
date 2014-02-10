package demo.geocoders;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapquest compatible geocoder. Generally, works well with US addresses.
 * I have found that it does a poor job sometimes with foreign addresses,
 *
 * @author Josh Long
 */
@Component
public class MapquestGeocoder implements Geocoder {

    private static Logger log = Logger.getLogger("MapquestGeocoder");
    private String urlPath = "http://www.mapquestapi.com/geocoding/v1/address?key={key}&format=json&callback=&location={location}";
    private RestTemplate restTemplate;
    private String key = null;

    @Autowired
    public MapquestGeocoder(@Value("${mapquest.key}") String key) {
        this(key, new RestTemplate());
    }

    public MapquestGeocoder(@Value("${mapquest.key}") String key, RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.key = key;
        Assert.notNull(this.restTemplate, "the restTemplate must not be null!");
        Assert.notNull(this.key, "the license key must not be null!");
    }

    // NB: u must first *decode* whatever key u use for the geocoder.
    public static void main(String[] args) {
        MapquestGeocoder mapquestGeocoder = new MapquestGeocoder(
                "Fmjtd|luur21u7n0,7x=o5-90txdr", new RestTemplate());
        LatLong latLong = mapquestGeocoder.geocode("8010 Shirley Ave, Los Angeles, CA 85283");
        System.out.println(latLong.toString());
    }

    @Override
    public LatLong geocode(String address) {

        Map<String, String> map = new HashMap<>();
        map.put("key", this.key);
        map.put("location", address);

        ResponseEntity<JsonNode> responseEntity = this.restTemplate.getForEntity(this.urlPath, JsonNode.class, map);
        System.out.println(responseEntity.toString());
        JsonNode jsonNode = responseEntity.getBody();

        if (jsonNode.path("results").size() > 0) {
            JsonNode results = jsonNode.path("results").get(0);
            JsonNode locations = results.path("locations").get(0);
            JsonNode latLng = locations.path("latLng");
            double lng = latLng.get("lng").doubleValue(), lat = latLng.get("lat").doubleValue();
            return new LatLong(lat, lng);
        }
        return null;
    }
}
