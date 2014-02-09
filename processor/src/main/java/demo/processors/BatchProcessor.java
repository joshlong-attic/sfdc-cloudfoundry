package demo.processors;

import org.springframework.amqp.core.MessageListener;

interface BatchProcessor extends MessageListener {
}
