package demo.processors;

import demo.BatchTemplate;
import demo.geocoders.Geocoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ContactGeocodingProcessor
        extends AbstractGeocodingProcessor {

    @Autowired
    public ContactGeocodingProcessor(BatchTemplate batchTemplate, JdbcTemplate jdbcTemplate, Geocoder googleGeocoder) {
        super(batchTemplate, jdbcTemplate, googleGeocoder);
    }

    @Override
    public String selectSql() {
        return "select * from sfdc_contact where batch_id = ? ";
    }

    @Override
    public RowMapper<AbstractGeocodingProcessor.Address> addressRowMapper() {
        return new RowMapper<AbstractGeocodingProcessor.Address>() {
            @Override
            public AbstractGeocodingProcessor.Address mapRow(ResultSet resultSet, int i) throws SQLException {
                return new AbstractGeocodingProcessor.Address(
                        resultSet.getString("mailing_street"),
                        resultSet.getString("mailing_city"),
                        resultSet.getString("mailing_state"),
                        resultSet.getString("mailing_postal_code"),
                        resultSet.getString("mailing_country"),
                        resultSet.getDouble("longitude"),
                        resultSet.getDouble("latitude")
                );
            }
        };
    }
}
