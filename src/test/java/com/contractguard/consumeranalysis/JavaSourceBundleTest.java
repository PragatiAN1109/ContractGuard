package com.contractguard.consumeranalysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaSourceBundleTest {

    private static JavaSourceBundle.UploadedFile java(String name, String body) {
        return new JavaSourceBundle.UploadedFile(name, body.getBytes(StandardCharsets.UTF_8));
    }

    private static JavaSourceBundle.UploadedFile zip(String... nameThenBody) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < nameThenBody.length; i += 2) {
                zip.putNextEntry(new ZipEntry(nameThenBody[i]));
                zip.write(nameThenBody[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new JavaSourceBundle.UploadedFile("bundle.zip", out.toByteArray());
    }

    @Test
    @DisplayName("loose .java files are accepted and sorted by path")
    void acceptsLooseJavaFiles() {
        JavaSourceBundle bundle = JavaSourceBundle.from(List.of(
                java("b/B.java", "class B {}"), java("a/A.java", "class A {}")));

        assertThat(bundle.files()).extracting(ConsumerSourceFile::path)
                .containsExactly("a/A.java", "b/B.java");
        assertThat(bundle.revisionHash()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("a .zip is expanded and non-Java entries are ignored")
    void expandsZipAndIgnoresOtherEntries() {
        JavaSourceBundle bundle = JavaSourceBundle.from(List.of(
                zip("svc/Handler.java", "class Handler {}", "README.md", "ignore me")));

        assertThat(bundle.files()).extracting(ConsumerSourceFile::path)
                .containsExactly("svc/Handler.java");
    }

    @Test
    @DisplayName("the revision hash is stable across upload order")
    void hashIsOrderIndependent() {
        String a = JavaSourceBundle.from(List.of(
                java("a/A.java", "class A {}"), java("b/B.java", "class B {}"))).revisionHash();
        String b = JavaSourceBundle.from(List.of(
                java("b/B.java", "class B {}"), java("a/A.java", "class A {}"))).revisionHash();

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("different content produces a different revision")
    void differentContentDiffersInHash() {
        assertThat(JavaSourceBundle.from(List.of(java("A.java", "class A {}"))).revisionHash())
                .isNotEqualTo(JavaSourceBundle.from(List.of(java("A.java", "class A2 {}"))).revisionHash());
    }

    @Test
    @DisplayName("zip-slip paths are rejected rather than sanitized")
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> JavaSourceBundle.from(List.of(zip("../../evil/Evil.java", "class Evil {}"))))
                .isInstanceOf(InvalidSourceBundleException.class)
                .hasMessageContaining("Unsafe path");

        assertThatThrownBy(() -> JavaSourceBundle.from(List.of(java("/etc/Evil.java", "class Evil {}"))))
                .isInstanceOf(InvalidSourceBundleException.class)
                .hasMessageContaining("Unsafe path");
    }

    @Test
    @DisplayName("non-Java uploads are rejected")
    void rejectsNonJavaFile() {
        assertThatThrownBy(() -> JavaSourceBundle.from(List.of(java("notes.txt", "hello"))))
                .isInstanceOf(InvalidSourceBundleException.class)
                .hasMessageContaining("Only .java files");
    }

    @Test
    @DisplayName("an empty upload is rejected")
    void rejectsEmptyUpload() {
        assertThatThrownBy(() -> JavaSourceBundle.from(List.of()))
                .isInstanceOf(InvalidSourceBundleException.class);
        assertThatThrownBy(() -> JavaSourceBundle.from(List.of(zip("README.md", "no java here"))))
                .isInstanceOf(InvalidSourceBundleException.class)
                .hasMessageContaining("no .java files");
    }

    @Test
    @DisplayName("a corrupt archive produces a controlled error")
    void rejectsCorruptZip() {
        assertThatThrownBy(() -> JavaSourceBundle.from(List.of(
                new JavaSourceBundle.UploadedFile("bundle.zip", "not a zip at all".getBytes()))))
                .isInstanceOf(InvalidSourceBundleException.class);
    }

    @Test
    @DisplayName("an oversized file is rejected")
    void rejectsOversizedFile() {
        String huge = "x".repeat(JavaSourceBundle.MAX_FILE_BYTES + 1);
        assertThatThrownBy(() -> JavaSourceBundle.from(List.of(java("Big.java", huge))))
                .isInstanceOf(InvalidSourceBundleException.class)
                .hasMessageContaining("per-file limit");
    }

    @Test
    @DisplayName("too many files is rejected")
    void rejectsTooManyFiles() {
        List<JavaSourceBundle.UploadedFile> many = new java.util.ArrayList<>();
        for (int i = 0; i <= JavaSourceBundle.MAX_FILES; i++) {
            many.add(java("f" + i + "/F.java", "class F {}"));
        }
        assertThatThrownBy(() -> JavaSourceBundle.from(many))
                .isInstanceOf(InvalidSourceBundleException.class)
                .hasMessageContaining("limit is");
    }

    @Test
    @DisplayName("duplicate paths are rejected")
    void rejectsDuplicatePaths() {
        assertThatThrownBy(() -> JavaSourceBundle.from(List.of(
                java("a/A.java", "class A {}"), java("a/A.java", "class A {}"))))
                .isInstanceOf(InvalidSourceBundleException.class)
                .hasMessageContaining("duplicate file paths");
    }
}
