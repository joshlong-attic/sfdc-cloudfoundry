package demo.processors;

import demo.GeolocationService;
import demo.SfdcBatchTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
class SfdcLeadGeolocationProcessor extends AbstractGeolocationProcessor {

    @Autowired
    public SfdcLeadGeolocationProcessor(SfdcBatchTemplate sfdcBatchTemplate, JdbcTemplate jdbcTemplate, GeolocationService geolocationService) {
        super(sfdcBatchTemplate, jdbcTemplate, geolocationService);
    }


    @Override
    public String selectSql() {
        return "select * from sfdc_lead  where batch_id = ? " ;
    }

    @Override
    public RowMapper<Address> addressRowMapper() {
        return new RowMapper<Address>() {
            @Override
            public Address mapRow(ResultSet resultSet, int i) throws SQLException {
                return new Address(resultSet.getString("street"),
                        resultSet.getString("city"),
                        resultSet.getString("state"),
                        resultSet.getString("postal_code"),
                        resultSet.getString("country"));
            }
        };
    }
}
