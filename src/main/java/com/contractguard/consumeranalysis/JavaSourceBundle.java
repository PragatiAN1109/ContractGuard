package com.contractguard.consumeranalysis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A validated set of Java source files plus the hash identifying that exact content.
 *
 * Accepts either loose {@code .java} files or one {@code .zip}. Every limit below exists because
 * this endpoint is reachable by an unauthenticated client on a public demo.
 */
public record JavaSourceBundle(List<ConsumerSourceFile> files, String revisionHash) {

    public static final int MAX_FILES = 200;
    public static final int MAX_FILE_BYTES = 256 * 1024;
    public static final int MAX_TOTAL_BYTES = 2 * 1024 * 1024;
    /** A .zip expanding beyond this multiple of its own size is treated as hostile. */
    private static final int MAX_COMPRESSION_RATIO = 100;

    public JavaSourceBundle {
        files = List.copyOf(files);
    }

    /** @param entries uploaded parts: loose .java files, or exactly one .zip */
    public static JavaSourceBundle from(List<UploadedFile> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new InvalidSourceBundleException("No source files were uploaded");
        }

        List<ConsumerSourceFile> collected = new ArrayList<>();
        for (UploadedFile entry : entries) {
            if (entry.isZip()) {
                collected.addAll(extractZip(entry));
            } else {
                collected.add(readJava(entry.name(), entry.bytes()));
            }
        }

        if (collected.isEmpty()) {
            throw new InvalidSourceBundleException("The upload contained no .java files");
        }
        if (collected.size() > MAX_FILES) {
            throw new InvalidSourceBundleException(
                    "The upload contains " + collected.size() + " Java files; the limit is " + MAX_FILES);
        }
        long total = collected.stream().mapToLong(file -> file.content().length()).sum();
        if (total > MAX_TOTAL_BYTES) {
            throw new InvalidSourceBundleException(
                    "The upload exceeds the " + (MAX_TOTAL_BYTES / 1024) + " KB total source limit");
        }

        List<ConsumerSourceFile> sorted = collected.stream()
                .sorted(Comparator.comparing(ConsumerSourceFile::path))
                .toList();
        if (sorted.stream().map(ConsumerSourceFile::path).distinct().count() != sorted.size()) {
            throw new InvalidSourceBundleException("The upload contains duplicate file paths");
        }
        return new JavaSourceBundle(sorted, hash(sorted));
    }

    private static List<ConsumerSourceFile> extractZip(UploadedFile archive) {
        List<ConsumerSourceFile> files = new ArrayList<>();
        long uncompressed = 0;
        long limit = Math.min((long) MAX_TOTAL_BYTES,
                Math.max(archive.bytes().length, 1L) * MAX_COMPRESSION_RATIO);

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive.bytes()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".java")) {
                    continue;
                }
                byte[] bytes = readAtMost(zip, MAX_FILE_BYTES, entry.getName());
                uncompressed += bytes.length;
                if (uncompressed > limit) {
                    throw new InvalidSourceBundleException(
                            "The archive expands far beyond its compressed size and was rejected");
                }
                files.add(readJava(entry.getName(), bytes));
                if (files.size() > MAX_FILES) {
                    throw new InvalidSourceBundleException(
                            "The archive contains more than " + MAX_FILES + " Java files");
                }
            }
        } catch (IOException e) {
            throw new InvalidSourceBundleException("The archive could not be read as a .zip");
        }
        return files;
    }

    private static ConsumerSourceFile readJava(String rawPath, byte[] bytes) {
        String path = normalizePath(rawPath);
        if (!path.endsWith(".java")) {
            throw new InvalidSourceBundleException("Only .java files are accepted, but got " + path);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new InvalidSourceBundleException(
                    path + " exceeds the " + (MAX_FILE_BYTES / 1024) + " KB per-file limit");
        }
        return new ConsumerSourceFile(path, new String(bytes, StandardCharsets.UTF_8));
    }

    /** Rejects zip-slip and absolute paths rather than trying to sanitize them. */
    private static String normalizePath(String rawPath) {
        String path = rawPath.replace('\\', '/').trim();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.isEmpty()) {
            throw new InvalidSourceBundleException("The upload contains an entry with no name");
        }
        if (path.startsWith("/") || path.contains("..") || path.contains(":")) {
            throw new InvalidSourceBundleException("Unsafe path in upload: " + rawPath);
        }
        return path;
    }

    private static byte[] readAtMost(InputStream stream, int max, String name) throws IOException {
        byte[] bytes = stream.readNBytes(max + 1);
        if (bytes.length > max) {
            throw new InvalidSourceBundleException(
                    name + " exceeds the " + (max / 1024) + " KB per-file limit");
        }
        return bytes;
    }

    /** Stable across upload order and file ordering, so the same content always hashes the same. */
    private static String hash(List<ConsumerSourceFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ConsumerSourceFile file : files) {
                digest.update(file.path().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(file.content().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    /** One uploaded part, decoupled from Spring's MultipartFile so this stays unit-testable. */
    public record UploadedFile(String name, byte[] bytes) {

        boolean isZip() {
            return name != null && name.toLowerCase().endsWith(".zip");
        }
    }
}
