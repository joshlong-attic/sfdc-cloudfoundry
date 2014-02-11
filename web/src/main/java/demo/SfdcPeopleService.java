package demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Josh Long (josh@joshlong.com)
 */
@Service
class SfdcPeopleService {

    @Autowired
    JdbcTemplate jdbcTemplate;

    RowMapper<SfdcPerson> sfdcPersonRowMapper = new RowMapper<SfdcPerson>() {
        @Override
        public SfdcPerson mapRow(ResultSet rs, int rowNum) throws SQLException {
            String street = rs.getString("street"),
                    email = rs.getString("email"),
                    city = rs.getString("city"),
                    state = rs.getString("state"),
                    postalCode = rs.getString("postal_code"),
                    batchId = rs.getString("batch_id"),
                    recordType = rs.getString("record_type");
            Object lat = rs.getObject("latitude"),
                    lon = rs.getObject("longitude");
            double latitude = lat != null ? (Double) lat : -0,
                    longitude = lon != null ? (Double) lon : -0;
            return new SfdcPerson(street, email, city, state, postalCode, batchId, recordType, latitude, longitude);
        }
    };

    List<SfdcPerson> results(String batchId) {
        return this.jdbcTemplate.query("select * from sfdc_directory where batch_id = ?", this.sfdcPersonRowMapper, batchId);
    }

    List<SfdcPerson> geolocated(String batchId) {
        return this.jdbcTemplate.query("select * from sfdc_directory where latitude is not null and longitude is not null and batch_id = ? ", this.sfdcPersonRowMapper, batchId);
    }

}
