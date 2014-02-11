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
public class LeadGeocodingProcessor
        extends AbstractGeocodingProcessor {

    @Autowired
    public LeadGeocodingProcessor(BatchTemplate batchTemplate,
                                  JdbcTemplate jdbcTemplate,
                                  Geocoder geocoder) {
        super(batchTemplate, jdbcTemplate, geocoder);
    }

    @Override
    public String selectSql() {
        return "select * from sfdc_lead where batch_id = ? ";
    }

    @Override
    public RowMapper<Address> addressRowMapper() {
        return new RowMapper<Address>() {
            @Override
            public Address mapRow(ResultSet resultSet, int i) throws SQLException {
                return new Address(
                        resultSet.getString("street"),
                        resultSet.getString("city"),
                        resultSet.getString("state"),
                        resultSet.getString("postal_code"),
                        resultSet.getString("country"),
                        resultSet.getDouble("longitude"),
                        resultSet.getDouble("latitude"));
            }
        };
    }
}
