package com.example.trades.kafka;

import com.example.trades.model.PlatformTrade;
import com.example.trades.model.TradeInstruction;
import com.example.trades.service.TradeTransformationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaListener.class);
    private static final int MAX_RETRIES = 3;

    private final TradeTransformationService transformationService;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String outboundTopic;

    public KafkaListener(TradeTransformationService transformationService,
                         KafkaTemplate<Object, Object> kafkaTemplate,
                         @Value("${app.kafka.outbound-topic:instructions.outbound}") String outboundTopic) {
        this.transformationService = transformationService;
        this.kafkaTemplate = kafkaTemplate;
        this.outboundTopic = outboundTopic;
    }

    @org.springframework.kafka.annotation.KafkaListener(topics = "${app.kafka.inbound-topic:instructions.inbound}", groupId = "trades-capture-service")
    public void listen(String message) throws IOException {

        Map<String, Object> raw = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});

        // Transform to canonical TradeInstruction (also stored in in-memory)
        TradeInstruction canonical = transformationService.toCanonical(raw);

        //  Transform to outbound PlatformTrade wrapper
        PlatformTrade accountingTrade = transformationService.toAccountingJson(canonical);

        //  Serialize to JSON
        String json = objectMapper.writeValueAsString(accountingTrade);

        //  Send to outbound topic with retry
        sendWithRetry(outboundTopic, canonical.getInstructionId(), json);
    }

    /**
     * Sends a message to Kafka with simple retry logic.
       */
    private void sendWithRetry(String topic, String key, String payload) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                attempt++;
                log.info("Sending message to topic='{}', key='{}', attempt={}", topic, key, attempt);

                kafkaTemplate.send(topic, key, payload).get();

                log.info("Successfully sent message to topic='{}', key='{}'", topic, key);
                return; // success, exit method

            } catch (Exception ex) {
                log.warn("Failed to send message to topic='{}', key='{}' on attempt {}/{}",
                        topic, key, attempt, MAX_RETRIES, ex);

                if (attempt >= MAX_RETRIES) {
                    // All retries failed – propagate so error handling
                    log.error("Exhausted retries sending message to topic='{}', key='{}'. Giving up.", topic, key);
                    throw new IllegalStateException("Failed to send Kafka message after retries", ex);
                }


                try {
                    TimeUnit.SECONDS.sleep(attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Retry sleep interrupted. Aborting retries for key='{}'", key);
                    throw new IllegalStateException("Retry interrupted while sending Kafka message", ie);
                }
            }
        }
    }
}