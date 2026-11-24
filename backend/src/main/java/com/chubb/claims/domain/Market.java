package com.chubb.claims.domain;

/** The six APAC markets. Each settles in its own currency; exposure is never summed across currencies. */
public enum Market {
    SG("SGD"), HK("HKD"), MY("MYR"), TH("THB"), ID("IDR"), VN("VND");

    private final String currency;

    Market(String currency) {
        this.currency = currency;
    }

    public String currency() {
        return currency;
    }
}
