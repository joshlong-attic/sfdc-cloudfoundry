package demo;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;


public class Client {
    public static void main(String[] args) throws Exception {
        // String uri = System.getenv("CLOUDAMQP_URL");

        String uri = "     amqp://dfbxoafe:6LPEQ28AF7W7cDN8Am19e7BAu2bzKrEd@lemur.cloudamqp.com/dfbxoafe ".trim();


        com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setUri(uri);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare("hello", false, false, false, null);
        String message = "Hello CloudAMQP!";
        channel.basicPublish("", "hello", null, message.getBytes());
        System.out.println(" [x] Sent '" + message + "'");

        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume("hello", true, consumer);

        while (true) {
            QueueingConsumer.Delivery delivery = consumer.nextDelivery();
            message = new String(delivery.getBody());
            System.out.println(" [x] Received '" + message + "'");
        }
    }
}