package demo.processors;

import com.force.api.ForceApi;
import demo.SfdcBatchTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SfdcLeadProcessor extends AbstractSfdcBatchProcessor {

    private ForceApi forceApi;
    private JdbcTemplate jdbcTemplate;

    @Autowired
    SfdcLeadProcessor(SfdcBatchTemplate sfdcBatchTemplate) {
        super(sfdcBatchTemplate);
    }

    @Override
    public void doProcessMessage(String batchId, Message msg) {

    }
}
