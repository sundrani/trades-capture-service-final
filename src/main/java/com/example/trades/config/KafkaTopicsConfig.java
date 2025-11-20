package com.example.trades.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicsConfig {

    @Value("${app.kafka.inbound-topic:instructions.inbound}")
    private String inboundTopic;

    @Value("${app.kafka.outbound-topic:instructions.outbound}")
    private String outboundTopic;

    @Bean
    public NewTopic inboundTopic() {
        return new NewTopic(inboundTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic outboundTopic() {
        return new NewTopic(outboundTopic, 1, (short) 1);
    }
}
