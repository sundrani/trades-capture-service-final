package com.example.trades.service;

import com.example.trades.model.CanonicalTrade;
import com.example.trades.model.PlatformTrade;
import com.example.trades.model.TradeInstruction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TradeTransformationServiceTest {

    private final TradeTransformationService service = new TradeTransformationService();

    @Test
    void toCanonical_masksAndNormalizesFields() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("instructionId", "ID-123");
        raw.put("account_number", "123456789");
        raw.put("security_id", "abc123");
        raw.put("trade_type", "Buy");
        raw.put("quantity", "100");
        raw.put("price", "10.5");

        TradeInstruction ti = service.toCanonical(raw);

        assertEquals("ID-123", ti.getInstructionId());
        assertEquals("XXXXX6789", ti.getAccountNumberMasked());
        assertEquals("ABC123", ti.getSecurityId());
        assertEquals("B", ti.getTradeTypeCode());
        assertEquals(100.0, ti.getQuantity());
        assertEquals(10.5, ti.getPrice());
        assertTrue(service.getInMemoryStore().containsKey("ID-123"));
    }

    @Test
    void toCanonical_rejectsInvalidSecurityId() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("instructionId", "ID-1");
        raw.put("security_id", "abc-123");

        assertThrows(IllegalArgumentException.class, () -> service.toCanonical(raw));
    }

    @Test
    void toAccountingJson_mapsFieldsCorrectly() {
        TradeInstruction ti = new TradeInstruction();
        ti.setInstructionId("ID-999");
        ti.setAccountNumberMasked("XXXXX6789");
        ti.setSecurityId("ABC123");
        ti.setTradeTypeCode("B");
        ti.setQuantity(200.0);
        ti.setTradeTimestamp(LocalDateTime.now());

        PlatformTrade response = service.toAccountingJson(ti);
        CanonicalTrade trade = response.getTrade();

        assertEquals("ID-999", response.getPlatform_id());
        assertEquals("XXXXX6789", trade.getAccount());
        assertEquals("ABC123", trade.getSecurity());
        assertEquals("B", trade.getType());
        assertEquals(200.0, trade.getAmount());
        assertNotNull(trade.getTimeStamp()); // ISO timestamp should exist
    }
}
