package demo.processors;

import com.force.api.ForceApi;
import com.force.api.QueryResult;
import demo.BatchTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class ContactProcessor extends AbstractBatchProcessor {

    private ForceApi forceApi;
    private JdbcTemplate jdbcTemplate;

    @Autowired
    ContactProcessor(BatchTemplate batchTemplate, JdbcTemplate jdbcTemplate, ForceApi forceApi) {
        super(batchTemplate);
        this.jdbcTemplate = jdbcTemplate;
        this.forceApi = forceApi;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void doProcessMessage(String batchId, Message msg) {
        String q = new String(msg.getBody());
        if (!StringUtils.hasText(q)) {
            return;
        }
        String query = "SELECT Email, MailingState, MailingCountry, MailingCity, MailingStreet , MailingPostalCode, Id, FirstName, LastName FROM contact "
                + " WHERE (  Email LIKE '%@%" + q + "%') and (  MailingCity <> '') and (MailingCity <>',') and (MailingState <> '') and (MailingCountry <> '') ";
        QueryResult<Map> res = forceApi.query(query);

        for (Map<String, Object> row : res.getRecords()) {

            String sql = "INSERT ignore INTO sfdc_contact(batch_id,  " +
                    "email, mailing_state, mailing_country,  mailing_city, mailing_street, " +
                    "mailing_postal_code, sfdc_id, first_name, last_name ) values( ?, ?, ?, ?, ?, ?,?, ?, ? ,?) ";

            this.jdbcTemplate.update(sql,
                    batchId,
                    row.get("Email"),
                    row.get("MailingState"),
                    row.get("MailingCountry"),
                    row.get("MailingCity"),
                    row.get("MailingStreet"),
                    row.get("MailingPostalCode"),
                    row.get("Id"),
                    row.get("FirstName"),
                    row.get("LastName"));
        }
    }
}
