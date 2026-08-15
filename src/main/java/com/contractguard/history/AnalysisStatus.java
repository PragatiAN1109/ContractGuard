package com.contractguard.history;

/** Lifecycle of an analysis run. Retained for a later asynchronous executor. */
public enum AnalysisStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
