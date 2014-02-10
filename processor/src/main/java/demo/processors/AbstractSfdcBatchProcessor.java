package demo.processors;

import demo.SfdcBatchTemplate;
import org.springframework.amqp.core.Message;

import java.util.Map;


abstract class AbstractSfdcBatchProcessor implements BatchProcessor {

    private SfdcBatchTemplate sfdcBatchTemplate;

    AbstractSfdcBatchProcessor(SfdcBatchTemplate sfdcBatchTemplate) {
        this.sfdcBatchTemplate = sfdcBatchTemplate;
    }

    @Override
    public void onMessage(final Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        String batchId = (String) headers.get("batchId");
        this.sfdcBatchTemplate.doInBatch(batchId, new SfdcBatchTemplate.BatchCallback() {
            @Override
            public void doInBatch(String batchId) throws Exception {
                doProcessMessage(batchId, message);
            }
        });
    }

    public abstract void doProcessMessage(String batchId, Message msg);
}
