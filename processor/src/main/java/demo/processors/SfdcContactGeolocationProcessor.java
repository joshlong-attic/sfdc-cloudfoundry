package demo.processors;

import demo.geocoders.Geocoder;
import demo.geocoders.GoogleGeocoder;
import demo.SfdcBatchTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
class SfdcContactGeolocationProcessor
        extends AbstractGeolocationProcessor {


    @Autowired
    public SfdcContactGeolocationProcessor(SfdcBatchTemplate sfdcBatchTemplate, JdbcTemplate jdbcTemplate,   Geocoder googleGeocoder) {
        super(sfdcBatchTemplate, jdbcTemplate, googleGeocoder);
    }



    @Override
    public String selectSql() {
        return "select * from sfdc_contact where batch_id = ? " ;
    }

    @Override
    public RowMapper<AbstractGeolocationProcessor.Address> addressRowMapper() {
        return new RowMapper<AbstractGeolocationProcessor.Address>() {
            @Override
            public AbstractGeolocationProcessor.Address mapRow(ResultSet resultSet, int i) throws SQLException {
                return new AbstractGeolocationProcessor.Address(
                        resultSet.getString("mailing_street"),
                        resultSet.getString("mailing_city"),
                        resultSet.getString("mailing_state"),
                        resultSet.getString("mailing_postal_code"),
                        resultSet.getString("mailing_country"));
            }
        };
    }
}
