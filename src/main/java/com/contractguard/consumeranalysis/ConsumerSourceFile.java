package com.contractguard.consumeranalysis;

/** One Java source file belonging to a consumer. */
public record ConsumerSourceFile(String path, String content) {

    public String fileName() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
