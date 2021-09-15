package com.github.griga23;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.stream.Stream;

public class TransactionalMessageProducer {

    private static final String DATA_MESSAGE_1 = "streda streda";
    private static final String DATA_MESSAGE_2 = "patek";

    public static void main(String[] args) throws IOException {

        // read properties file
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "Please provide: the path to an environment configuration file");
        }

        // initialize Kafka producer
        final Properties props = loadProperties(args[0]);
        final String topic = props.getProperty("input.topic.name");
        KafkaProducer<String, String> producer = new KafkaProducer<String, String>(props);

        // init transactions for this Producer with predefined Transactions ID
        producer.initTransactions();
        try{
            // mark beginning of the transaction
            producer.beginTransaction();

            // send messages to Kafka topic
            producer.send(new ProducerRecord<String, String>(topic, null, DATA_MESSAGE_1));
            producer.send(new ProducerRecord<String, String>(topic, null, DATA_MESSAGE_2));

            // mark end of the transaction
            producer.commitTransaction();

        }catch (KafkaException e){
            producer.abortTransaction();
        }

    }

    // load properties from some file
    public static Properties loadProperties(String fileName) throws IOException {
        final Properties envProps = new Properties();
        final FileInputStream input = new FileInputStream(fileName);
        envProps.load(input);
        input.close();

        return envProps;
    }

}
