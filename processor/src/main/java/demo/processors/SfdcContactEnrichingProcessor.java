package demo.processors;

import com.force.api.ForceApi;
import com.force.api.QueryResult;
import demo.SfdcBatchTemplate;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
class SfdcContactEnrichingProcessor extends AbstractSfdcBatchProcessor {

    private ForceApi forceApi;
    private Logger logger = Logger.getLogger(getClass());

    @Autowired
    SfdcContactEnrichingProcessor(SfdcBatchTemplate sfdcBatchTemplate, ForceApi forceApi) {
        super(sfdcBatchTemplate);
        this.forceApi = forceApi;
    }

    List<Contact> contacts() {
        QueryResult<Contact> res = forceApi.query(
                "SELECT MailingState, MailingCountry, MailingStreet , MailingPostalCode, Email,  Id, FirstName, LastName FROM contact", Contact.class);
        List<Contact> contacts = new ArrayList<>();
        for (Contact c : res.getRecords())
            if (StringUtils.hasText(c.getMailingStreet()))
                contacts.add(c);
        return contacts;
    }

    @Override
    public void doProcessMessage(String batchId, Message msg) {
        for (Contact c : contacts())
            logger.debug(c.toString());
    }
}
