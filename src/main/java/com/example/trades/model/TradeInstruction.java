package com.example.trades.model;

import java.time.LocalDateTime;

public class TradeInstruction {

    private String instructionId;
    private String accountNumberMasked;
    private String securityId;
    private String tradeTypeCode;
    private Double quantity;
    private Double price;
    private LocalDateTime tradeTimestamp;

    public String getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId;
    }

    public String getAccountNumberMasked() {
        return accountNumberMasked;
    }

    public void setAccountNumberMasked(String accountNumberMasked) {
        this.accountNumberMasked = accountNumberMasked;
    }

    public String getSecurityId() {
        return securityId;
    }

    public void setSecurityId(String securityId) {
        this.securityId = securityId;
    }

    public String getTradeTypeCode() {
        return tradeTypeCode;
    }

    public void setTradeTypeCode(String tradeTypeCode) {
        this.tradeTypeCode = tradeTypeCode;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public LocalDateTime getTradeTimestamp() {
        return tradeTimestamp;
    }

    public void setTradeTimestamp(LocalDateTime tradeTimestamp) {
        this.tradeTimestamp = tradeTimestamp;
    }
}
