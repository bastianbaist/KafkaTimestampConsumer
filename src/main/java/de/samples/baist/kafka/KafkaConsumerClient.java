package de.samples.baist.kafka;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component
public class KafkaConsumerClient implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerClient.class);

    final static Duration timeout = Duration.ofMinutes(10);
    public static String TOPIC = "test-consumer";

    private KafkaConsumer consumer;
    private static final Properties props = new Properties();

    {
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-linesplit-rec");

        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092, localhost:9093");
        props.put("group.id", "test-consome-id");
        //props.put("key.serializer", Serdes.String().getClass());
        //props.put("value.serializer", Serdes.String().getClass());

        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    }

    private static void parseProperties(String[] optionalProps) {
        for (int i = 0; i < CollectionUtils.size(optionalProps); ++i) {
            final String currentArg = optionalProps[i];
            switch (currentArg) {
                case "bootstrap.servers":
                    props.put(currentArg, optionalProps[++i]);
                    break;
                case "listen.topic":
                    TOPIC = optionalProps[++i];
                    break;
                default:
                    break;
            }
        }
        final String brokerServer = System.getenv("bootstrap.servers");
        if(null != brokerServer){
            props.put("bootstrap.servers", brokerServer);
        }
        TOPIC = StringUtils.defaultIfEmpty(System.getenv("listen.topic"), TOPIC);
    }

    public KafkaConsumerClient() {
    }

    @Override
    public void run(String... args) throws Exception {
        parseProperties(args);
        this.consumer = new KafkaConsumer(props);

        LOG.info("Listening to Topic {} received configured broker server {} ", TOPIC, props.getProperty("bootstrap.servers"));
        AtomicBoolean ab = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                ab.set(false);
            }
        });
        consumer.subscribe(Arrays.asList(TOPIC));


        while (ab.get()) {
            final List<String> records = poll();
            final Date received = new Date();
            long nowMillis = received.getTime();
            final List<String> timeStrings = records.stream()
                    .map(Long::parseLong)
                    .map(m -> formatMillisTimeToReadableString(nowMillis - m))
                    .collect(Collectors.toList());

            timeStrings.forEach(s -> LOG.info("A Message recieved at {} needed {} ms to reach us.", received, s));
        }
    }

    private static String formatMillisTimeToReadableString(long millis) {
        final String diff = String.format("%02d:%02d:%02d.%03d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)), // The change is in this line
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)),
                millis - (TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(millis))));
        LOG.debug("Converted long of millis ({}) into a string ({}).", millis, diff);
        return diff;
    }


    private List<String> poll() {
        List<String> result = new ArrayList<>();

        ConsumerRecords<String, String> records = consumer.poll(timeout);

        Iterable<ConsumerRecord<String, String>> r = records.records(TOPIC);
        r.forEach(s -> result.add((s.value())));
        return result;
    }
}
