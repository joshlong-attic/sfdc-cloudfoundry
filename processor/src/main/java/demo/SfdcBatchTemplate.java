package demo;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class SfdcBatchTemplate {

    private ThreadLocal<String> batchIdThreadLocal = new ThreadLocal<>();

    public String requiredCurrentBatchId() {
        Assert.notNull(this.batchIdThreadLocal.get(), "something is wrong, there's no batchId");
        return this.batchIdThreadLocal.get();
    }

    public void doInBatch(String batchId, BatchCallback batchCallback) {
        this.batchIdThreadLocal.set(batchId);
        try {
            batchCallback.doInBatch(batchId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            this.batchIdThreadLocal.remove();
        }
    }

    public static interface BatchCallback {
        void doInBatch(String batchId) throws Exception;
    }
}
