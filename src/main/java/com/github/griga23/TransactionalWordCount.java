package com.github.griga23;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singleton;


public class TransactionalWordCount {

    public static void main(String[] args) throws IOException {

        // read properties file
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "Please provide: the path to an environment configuration file");
        }

        // initialize properties
        final Properties props = loadProperties(args[0]);
        final String inputTopic = props.getProperty("input.topic.name");
        final String outputTopic = props.getProperty("output.topic.name");

        // create Consumer and subscribe to Kafka input topic
        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
        consumer.subscribe(singleton(inputTopic));

        // create Producer
        KafkaProducer<String, String> producer = new KafkaProducer<String, String>(props);

        // init transactions for this Producer with predefined Transactions ID
        producer.initTransactions();

        try {

            // endless loop to read from input Kafka topic
            while (true) {

                // read all new records from input Kafka topic
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(5000));

                // for each partition in the new records list
                records.partitions().forEach((TopicPartition partition) -> {
                    // get all new records for this partition
                    List<ConsumerRecord<String, String>> partitionedRecords = records.records(partition);

                    // for all new records calculate word occurrence in the message and store result in the map
                    Map<String, Integer> wordCountMap = partitionedRecords
                            .stream()
                            .flatMap(record -> Stream.of(record.value().split(" ")))
                            .map(word -> Event.of(word, 1))
                            .collect(Collectors.toMap(Event::getKey, Event::getValue, Integer::sum));

                    // mark beginning of the transaction
                    producer.beginTransaction();

                    // send word occurrence to output Kafka topic
                    wordCountMap.forEach((key, value) ->
                            producer.send(new ProducerRecord<String, String>(outputTopic, key, value.toString())));

                    // calculate the new latest consumer offset for the input Kafka topic for the current partition
                    long offset = partitionedRecords.get(partitionedRecords.size() - 1).offset();
                    Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
                    offsetsToCommit.put(partition, new OffsetAndMetadata(offset + 1));

                    // send Consumer offsets to the input Kafka topic
                    producer.sendOffsetsToTransaction(offsetsToCommit, consumer.groupMetadata());

                    // commit transaction
                    producer.commitTransaction();
                });

            }

        } catch (KafkaException e) {
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