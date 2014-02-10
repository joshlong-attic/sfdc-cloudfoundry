package demo.processors;

import com.force.api.ForceApi;
import com.force.api.QueryResult;
import demo.SfdcBatchTemplate;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
        String q = new String( msg.getBody()) ;
        if(!StringUtils.hasText( q)){
          return;
        }
        String query = "  SELECT Email, MailingState, MailingCountry, MailingStreet , MailingPostalCode, Email,  Id, FirstName, LastName FROM contact " ;
        query  = query  +   " WHERE (Company LIKE '%" + q+
                "%' or Email LIKE '%@%" + q +
                "%') and (  City <> '') and (City <>',') and (State <> '') and (Country <> '') " ;
        QueryResult<Map> res = forceApi.query(
                 query);

        for (Map<String, Object> row : res.getRecords()) {

            String sql =  "INSERT ignore INTO sfdc_contact( email, mailing_state, mailing_country, mailing_street, mailing_postal_code, email, sfdc_id, first_name, last_name) values( ?, ?, ?, ?, ?, ?, ? ,?) ";

            this.jdbcTemplate.update(sql,
                    row.get("Email"),
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
