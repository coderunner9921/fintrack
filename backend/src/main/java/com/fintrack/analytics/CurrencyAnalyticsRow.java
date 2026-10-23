
package com.fintrack.analytics;

import java.math.BigDecimal;

public interface CurrencyAnalyticsRow {

    String getCurrency();

    BigDecimal getIncomeTotal();

    BigDecimal getExpenseTotal();

    Long getIncomeCount();

    Long getExpenseCount();
}
