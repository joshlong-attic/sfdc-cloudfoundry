package demo.processors;

import demo.BatchTemplate;
import demo.geocoders.Geocoder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.jdbc.core.*;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;


public abstract class AbstractGeocodingProcessor
        extends AbstractBatchProcessor {

    private Geocoder googleGeocoder;
    private JdbcTemplate jdbcTemplate;

    public AbstractGeocodingProcessor(BatchTemplate batchTemplate, JdbcTemplate jdbcTemplate, Geocoder googleGeocoder) {
        super(batchTemplate);
        this.googleGeocoder = googleGeocoder;
        this.jdbcTemplate = jdbcTemplate;

    }

    public void persistGelocationResult(ResultSet resultSet, AbstractGeocodingProcessor.Address address, Geocoder.LatLong latLong) throws SQLException {
        resultSet.updateDouble("latitude", latLong.getLatitude());
        resultSet.updateDouble("longitude", latLong.getLongitude());
        resultSet.updateRow();
    }

    protected Geocoder getGeolocationService() {
        return googleGeocoder;
    }

    protected JdbcTemplate getJdbcTemplate() {
        return this.jdbcTemplate;
    }

    @Override
    public void doProcessMessage(String batchId, Message msg) {
        final RowMapper<Address> addressRowMapper = addressRowMapper();
        PreparedStatementCreatorFactory preparedStatementCreatorFactory = new PreparedStatementCreatorFactory(selectSql());
        preparedStatementCreatorFactory.setUpdatableResults(true);
        preparedStatementCreatorFactory.addParameter(new SqlParameter("batchId", Types.VARCHAR));
        PreparedStatementCreator preparedStatementCreator = preparedStatementCreatorFactory.newPreparedStatementCreator(new Object[]{batchId});
        RowCallbackHandler rowCallbackHandler = new RowCallbackHandler() {
            int offset = 0;

            @Override
            public void processRow(ResultSet resultSet) throws SQLException {
                Address address = addressRowMapper.mapRow(resultSet, offset);
                if (address.getLatitude() == null || address.getLongitude() == null) {
                    Geocoder.LatLong latLong = geocode(address);
                    if (null != latLong) {
                        persistGelocationResult(resultSet, address, latLong);
                    }
                }
                offset += 1;
            }
        };

        getJdbcTemplate().query(preparedStatementCreator, rowCallbackHandler);
    }

    public abstract String selectSql();

    public Geocoder.LatLong geocode(Address address) {
        Assert.notNull(address, "the provided address can't be null");

        String addy = address.getAddress(),
                city = address.getCity(),
                state = address.getState(),
                zipcode = address.getPostalCode(),
                country = address.getCountry();

        // do we have enough for a full geolocation?
        boolean hasAllFields =
                !StringUtils.isEmpty(city) && !StringUtils.isEmpty(state) &&
                        !StringUtils.isEmpty(addy) && !StringUtils.isEmpty(zipcode) &&
                        !StringUtils.isEmpty(country);

        // what about city and state?
        boolean hasCityAndState =
                !StringUtils.isEmpty(city) && !StringUtils.isEmpty(state);

        // what about zipcode ?
        boolean hasZipCodeAndCountry = !StringUtils.isEmpty(zipcode) && !StringUtils.isEmpty(country);

        // what about just trying city and country?
        boolean hasCityAndCountry = !StringUtils.isEmpty(city) && !StringUtils.isEmpty(country);

        // fall through if its not empty
        boolean hasStreet = !StringUtils.isEmpty(addy);

        Geocoder.LatLong latLong = null;

        if (hasAllFields) {
            latLong = this.googleGeocoder.geocode(String.format("%s, %s, %s, %s", addy, city, state, zipcode));
        }
        if (latLong == null && hasCityAndState) {
            latLong = this.googleGeocoder.geocode(String.format("%s, %s", city, state));
        }
        if (latLong == null && hasZipCodeAndCountry) {
            latLong = this.googleGeocoder.geocode(String.format("%s, %s", zipcode, country));
        }
        if (latLong == null && hasStreet) {
            latLong = this.googleGeocoder.geocode(addy);
        }
        if (latLong == null && hasCityAndCountry) {
            latLong = this.googleGeocoder.geocode(String.format("%s, %s", city, country));
        }

        return latLong;
    }

    public abstract RowMapper<Address> addressRowMapper();

    public static class Address {

        private String city, postalCode, state, country, address;
        private Double longitude, latitude;

        public Address(String address, String city, String state, String postalCode, String country, Double longitude, Double latitude) {
            this.city = city;
            this.state = state;
            this.country = country;
            this.address = address;
            this.postalCode = postalCode;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public Double getLatitude() {
            return latitude;
        }

        public String getCity() {
            return city;
        }

        public String getState() {
            return state;
        }

        public String getCountry() {
            return country;
        }

        public String getAddress() {
            return address;
        }

        public String getPostalCode() {
            return postalCode;
        }
    }
}
