package kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Kafka Consumer — New Relic OpenTelemetry Example
 *
 * This is a standard Apache Kafka consumer with zero OpenTelemetry SDK
 * dependencies. All observability — span creation, W3C Trace Context
 * extraction from message headers (linking back to the producer span),
 * and consumer group lag metrics — is handled automatically by the
 * OpenTelemetry Java Agent attached at runtime via -javaagent.
 *
 * See the Dockerfile for how the agent is attached.
 */
public class ConsumerApp {

    private static final Logger log = Logger.getLogger(ConsumerApp.class.getName());

    public static void main(String[] args) {
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String groupId          = System.getenv().getOrDefault("KAFKA_GROUP_ID", "nr-otel-consumer-group");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");

        log.info("Starting Kafka Consumer — bootstrap: " + bootstrapServers + ", group: " + groupId);

        long count = 0;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Arrays.asList("orders", "payments", "shipments"));
            log.info("Subscribed. Waiting for messages...");

            while (true) {
                // The OTel Java Agent intercepts each poll() call and automatically:
                //   1. Extracts W3C Trace Context from each message's headers
                //   2. Creates a consumer span linked to the original producer span
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

                count += records.count();
                if (count > 0 && count % 300 == 0) {
                    log.info("Consumed " + count + " messages total");
                }
            }
        }
    }
}
