package com.retailmedia.insights.domain;

/** Whether a day's number can still change. */
public enum DayStatus {

    /** Reconciled by the nightly batch job: bots and duplicates removed, late events added. Will not change. */
    FINAL,

    /** From the real-time stream. Fresh, but may move once the day is reconciled. */
    PRELIMINARY
}
