package com.contractguard.consumeranalysis;

/** One place a consumer names an enum symbol. */
record EnumUsage(String symbol, EnumUsageKind kind, int line, String snippet) {
}
