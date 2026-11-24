package com.chubb.claims.domain;

import java.math.BigDecimal;

/** JPQL constructor-expression projection used by the exposure aggregate. */
public record ExposureRow(Market market, ClaimType type, ClaimStatus status, String currency,
                          Long count, BigDecimal amount) {
}
