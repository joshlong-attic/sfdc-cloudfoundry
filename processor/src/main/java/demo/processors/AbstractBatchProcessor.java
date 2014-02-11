package demo.processors;

import demo.BatchTemplate;
import org.springframework.amqp.core.Message;

import java.util.Map;


public abstract class AbstractBatchProcessor
        implements BatchProcessor {

    private BatchTemplate batchTemplate;

    public AbstractBatchProcessor(BatchTemplate batchTemplate) {
        this.batchTemplate = batchTemplate;
    }

    @Override
    public void onMessage(final Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        String batchId = (String) headers.get("batchId");
        this.batchTemplate.doInBatch(batchId,
                new BatchTemplate.BatchCallback() {
                    @Override
            public void doInBatch(String batchId) throws Exception {
                doProcessMessage(batchId, message);
            }
        });
    }

    public abstract void doProcessMessage(String batchId,   Message msg);
}
