package demo.processors;

import demo.GeolocationService;
import demo.SfdcBatchTemplate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.jdbc.core.*;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;


public abstract class AbstractGeolocationProcessor
        extends AbstractSfdcBatchProcessor {

    private GeolocationService geolocationService;
    private JdbcTemplate jdbcTemplate;

    public void persistGelocationResult(ResultSet resultSet, AbstractGeolocationProcessor.Address address, GeolocationService.LatLong latLong) throws SQLException {
        resultSet.updateDouble("latitude", latLong.getLatitude());
        resultSet.updateDouble("longitude", latLong.getLongitude());
        resultSet.updateRow();
    }

    public AbstractGeolocationProcessor(SfdcBatchTemplate sfdcBatchTemplate, JdbcTemplate jdbcTemplate, GeolocationService geolocationService) {
        super(sfdcBatchTemplate);
        this.geolocationService = geolocationService;
        this.jdbcTemplate = jdbcTemplate;

    }

    protected GeolocationService getGeolocationService() {
        return geolocationService;
    }

    protected JdbcTemplate getJdbcTemplate() {
        return this.jdbcTemplate;
    }

    @Override
    public void doProcessMessage(String batchId, Message msg) {
        final RowMapper<Address> addressRowMapper = addressRowMapper();
        PreparedStatementCreatorFactory preparedStatementCreatorFactory = new PreparedStatementCreatorFactory(selectSql());
        preparedStatementCreatorFactory.setUpdatableResults(true);
        preparedStatementCreatorFactory.addParameter( new SqlParameter("batchId", Types.VARCHAR));
        PreparedStatementCreator preparedStatementCreator = preparedStatementCreatorFactory.newPreparedStatementCreator(new Object[]{batchId});
        RowCallbackHandler rowCallbackHandler =
                new RowCallbackHandler() {
                    int offset = 0;

                    @Override
                    public void processRow(ResultSet resultSet) throws SQLException {
                        Address address = addressRowMapper.mapRow(resultSet, offset);
                        offset += 1;
                        GeolocationService.LatLong latLong = geocode(address);
                        if (null != latLong)
                            persistGelocationResult(resultSet, address, latLong);
                    }
                };

        getJdbcTemplate().query(preparedStatementCreator, rowCallbackHandler);
    }


    public abstract String selectSql();


    public GeolocationService.LatLong geocode(Address address) {
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
        boolean hasZipCode = !StringUtils.isEmpty(zipcode);

        // fall through if its not empty
        boolean hasStreet = !StringUtils.isEmpty(addy);

        GeolocationService.LatLong latLong = null;

        if (hasAllFields) {
            latLong = this.geolocationService.geocode(String.format("%s, %s, %s, %s", addy, city, state, zipcode));
        }
        if (latLong == null && hasCityAndState) {
            latLong = this.geolocationService.geocode(String.format("%s, %s", city, state));
        }
        if (latLong == null && hasZipCode) {
            latLong = this.geolocationService.geocode(zipcode + "");
        }
        if( latLong == null && hasStreet){
            latLong = this.geolocationService.geocode( addy) ;
        }
        return latLong;
    }

    public abstract RowMapper<Address> addressRowMapper();

    public static class Address {

        private String city, postalCode, state, country, address;

        public Address(String address, String city, String state, String postalCode, String country) {
            this.city = city;
            this.state = state;
            this.country = country;
            this.address = address;
            this.postalCode = postalCode;
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
