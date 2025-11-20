package com.example.trades.model;

public class PlatformTrade {

    private String platform_id;
    private CanonicalTrade trade;

    // getters + setters

    public String getPlatform_id() {
        return platform_id;
    }

    public void setPlatform_id(String platform_id) {
        this.platform_id = platform_id;
    }

    public CanonicalTrade getTrade() {
        return trade;
    }

    public void setTrade(CanonicalTrade trade) {
        this.trade = trade;
    }
}