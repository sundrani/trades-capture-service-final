package com.example.trades.service;

import com.example.trades.model.CanonicalTrade;
import com.example.trades.model.PlatformTrade;
import com.example.trades.model.TradeInstruction;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class TradeTransformationService {

    private static final Pattern SECURITY_ID_PATTERN = Pattern.compile("^[A-Z0-9]+$");

    private final Map<String, TradeInstruction> inMemoryStore = new ConcurrentHashMap<>();

    public TradeInstruction toCanonical(Map<String, Object> raw) {
        TradeInstruction ti = new TradeInstruction();

        String id = (String) raw.getOrDefault("instructionId", UUID.randomUUID().toString());
        ti.setInstructionId(id);

        String accountNumber = (String) raw.getOrDefault("account_number", "");
        ti.setAccountNumberMasked(maskAccountNumber(accountNumber));

        String securityId = ((String) raw.getOrDefault("security_id", "")).toUpperCase();
        if (!securityId.isEmpty() && !SECURITY_ID_PATTERN.matcher(securityId).matches()) {
            throw new IllegalArgumentException("Invalid security_id format");
        }
        ti.setSecurityId(securityId);

        String tradeType = (String) raw.getOrDefault("trade_type", "");
        ti.setTradeTypeCode(normalizeTradeType(tradeType));

        Object qtyObj = raw.get("quantity");
        if (qtyObj != null && !qtyObj.toString().isBlank()) {
            ti.setQuantity(Double.valueOf(qtyObj.toString()));
        }

        Object priceObj = raw.get("price");
        if (priceObj != null && !priceObj.toString().isBlank()) {
            ti.setPrice(Double.valueOf(priceObj.toString()));
        }
        inMemoryStore.put(id, ti);
        return ti;
    }

    public PlatformTrade toAccountingJson(TradeInstruction ti) {
        PlatformTrade response = new PlatformTrade();
        response.setPlatform_id(ti.getInstructionId());

        CanonicalTrade trade = new CanonicalTrade();
        trade.setAccount(ti.getAccountNumberMasked());
        trade.setSecurity(ti.getSecurityId());
        trade.setType(ti.getTradeTypeCode());
        trade.setAmount(ti.getQuantity());


        LocalDateTime ts = ti.getTradeTimestamp();
        if (ts == null) {
            ts = LocalDateTime.now();
            ti.setTradeTimestamp(ts);
        }

        String isoTimestamp = ts.atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);

        trade.setTimeStamp(isoTimestamp);

        response.setTrade(trade);
        return response;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        int visible = 4;
        int maskLength = accountNumber.length() - visible;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maskLength; i++) {
            sb.append('X');
        }
        sb.append(accountNumber.substring(maskLength));
        return sb.toString();
    }

    private String normalizeTradeType(String tradeType) {
        if (tradeType == null) return null;
        String t = tradeType.trim().toLowerCase();
        return switch (t) {
            case "buy", "b" -> "B";
            case "sell", "s" -> "S";
            default -> throw new IllegalArgumentException("Unknown trade_type: " + tradeType);
        };
    }

    public Map<String, TradeInstruction> getInMemoryStore() {
        return inMemoryStore;
    }
}
