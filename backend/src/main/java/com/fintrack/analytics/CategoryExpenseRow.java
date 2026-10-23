package com.fintrack.analytics;

import java.math.BigDecimal;

public interface CategoryExpenseRow {

    String getCurrency();

    String getCategoryName();

    BigDecimal getTotal();
}
