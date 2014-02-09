package demo.processors;

import com.force.api.ForceApi;
import com.force.api.QueryResult;
import demo.SfdcBatchTemplate;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class SfdcContactProcessor extends AbstractSfdcBatchProcessor {

    private ForceApi forceApi;
    private JdbcTemplate jdbcTemplate;

    @Autowired
    SfdcContactProcessor(SfdcBatchTemplate sfdcBatchTemplate, JdbcTemplate jdbcTemplate, ForceApi forceApi) {
        super(sfdcBatchTemplate);
        this.jdbcTemplate = jdbcTemplate;
        this.forceApi = forceApi;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void doProcessMessage(String batchId, Message msg) {

        QueryResult<Map> res = forceApi.query("SELECT MailingState, MailingCountry, MailingStreet , MailingPostalCode, Email,  Id, FirstName, LastName FROM contact");

        for (Map<String, Object> row : res.getRecords()) {

            String sql =  "INSERT ignore INTO sfdc_contact( mailing_state, mailing_country, mailing_street, mailing_postal_code, email, sfdc_id, first_name, last_name) values( ?, ?, ?, ?, ?, ?, ? ,?) ";

            this.jdbcTemplate.update(sql,
                    row.get("MailingState"),
                    row.get("MailingCountry"),
                    row.get("MailingStreet"),
                    row.get("MailingPostalCode"),
                    row.get("Email"),
                    row.get("Id"),
                    row.get("FirstName"),
                    row.get("LastName"));
        }
    }
}
