package com.example.trades.kafka;

import com.example.trades.model.CanonicalTrade;
import com.example.trades.model.PlatformTrade;
import com.example.trades.model.TradeInstruction;
import com.example.trades.service.TradeTransformationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaListenerTest {

    @Mock
    private TradeTransformationService transformationService;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private KafkaListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        listener = new KafkaListener(transformationService, kafkaTemplate, "instructions.outbound");
    }

    @Test
    void listen_transformsAndPublishesToKafka() throws Exception {

        Map<String, Object> raw = new HashMap<>();
        raw.put("instructionId", "ID-1");
        raw.put("account_number", "123456789");
        raw.put("security_id", "ABC123");
        raw.put("trade_type", "Buy");
        raw.put("quantity", 100);
        raw.put("price", 10.5);

        String message = objectMapper.writeValueAsString(raw);

        TradeInstruction canonical = new TradeInstruction();
        canonical.setInstructionId("ID-1");
        canonical.setAccountNumberMasked("XXXXX6789");
        canonical.setSecurityId("ABC123");
        canonical.setTradeTypeCode("B");
        canonical.setQuantity(100.0);
        canonical.setPrice(10.5);
        canonical.setTradeTimestamp(LocalDateTime.now());

        when(transformationService.toCanonical(any())).thenReturn(canonical);

        CanonicalTrade ct = new CanonicalTrade();
        ct.setAccount("XXXXX6789");
        ct.setSecurity("ABC123");
        ct.setType("B");
        ct.setAmount(100.0);
        ct.setTimeStamp("2025-11-19T14:33:01Z");

        PlatformTrade platformTrade = new PlatformTrade();
        platformTrade.setPlatform_id("ID-1");
        platformTrade.setTrade(ct);

        when(transformationService.toAccountingJson(canonical))
                .thenReturn(platformTrade);

        SettableListenableFuture<SendResult<Object, Object>> future =
                new SettableListenableFuture<>();
        future.set(null);

        when(kafkaTemplate.send(anyString(), any(), any()))
                .thenReturn(((ListenableFuture) future).completable());

        listener.listen(message);

        verify(kafkaTemplate, times(1))
                .send(eq("instructions.outbound"), eq("ID-1"), anyString());
    }
}