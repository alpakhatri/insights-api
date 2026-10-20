package com.retailmedia.insights.repository;

import com.retailmedia.insights.domain.DayStatus;

/** The two ClickHouse tables that hold campaign metrics. Table names are fixed here, never taken from input. */
public enum MetricsSource {

    /** Rebuilt nightly by the Spark reconciliation job from S3. Exact and final. */
    RECONCILED("insights.metrics_reconciled", DayStatus.FINAL),

    /** Written continuously by the stream processor. Fresh and preliminary; kept for a few days only. */
    REALTIME("insights.metrics_realtime", DayStatus.PRELIMINARY);

    private final String table;
    private final DayStatus status;

    MetricsSource(String table, DayStatus status) {
        this.table = table;
        this.status = status;
    }

    public String table() {
        return table;
    }

    public DayStatus status() {
        return status;
    }
}
