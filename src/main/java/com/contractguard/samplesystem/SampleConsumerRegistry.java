package com.contractguard.samplesystem;

import com.contractguard.consumeranalysis.ConsumerDefinition;
import com.contractguard.consumeranalysis.ConsumerRegistry;
import com.contractguard.consumeranalysis.ConsumerSourceFile;
import com.contractguard.consumeranalysis.ConsumerSourceType;
import com.contractguard.consumeranalysis.JavaSourceBundle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Loads consumers from the built-in sample bundles on the classpath.
 *
 * A bundle is a directory under {@code samples/} containing {@code consumers/consumers.json};
 * adding a sample is a directory drop with no code change.
 */
@Component
public class SampleConsumerRegistry implements ConsumerRegistry {

    private static final Logger log = LoggerFactory.getLogger(SampleConsumerRegistry.class);
    private static final String MANIFEST_PATTERN = "classpath*:samples/*/consumers/consumers.json";

    private final List<ConsumerDefinition> consumers;

    public SampleConsumerRegistry(ObjectMapper objectMapper) {
        this.consumers = loadAll(objectMapper);
        log.info("Loaded {} sample consumer(s)", consumers.size());
    }

    @Override
    public List<ConsumerDefinition> findByConsumedSchema(java.util.UUID projectId, String schemaFullName) {
        return consumers.stream()
                .filter(consumer -> consumer.consumesSchema().equals(schemaFullName))
                .sorted(Comparator.comparing(ConsumerDefinition::name))
                .toList();
    }

    private static List<ConsumerDefinition> loadAll(ObjectMapper objectMapper) {
        List<ConsumerDefinition> loaded = new ArrayList<>();
        try {
            Resource[] manifests = new PathMatchingResourcePatternResolver()
                    .getResources(MANIFEST_PATTERN);
            for (Resource manifest : manifests) {
                loaded.addAll(readManifest(objectMapper, manifest));
            }
        } catch (IOException e) {
            // A missing or broken sample bundle must not stop the application from starting.
            log.warn("Could not scan sample consumer bundles: {}", e.getMessage());
        }
        loaded.sort(Comparator.comparing(ConsumerDefinition::name));
        return List.copyOf(loaded);
    }

    private static List<ConsumerDefinition> readManifest(ObjectMapper objectMapper, Resource manifest) {
        List<ConsumerDefinition> loaded = new ArrayList<>();
        try (InputStream stream = manifest.getInputStream()) {
            JsonNode root = objectMapper.readTree(stream);
            for (JsonNode node : root.path("consumers")) {
                String name = node.path("name").asText();
                List<ConsumerSourceFile> files = new ArrayList<>();
                for (JsonNode path : node.path("sourceFiles")) {
                    readSource(manifest, path.asText()).ifPresent(files::add);
                }
                // Bundled samples have no registry row; their revision is the hash of the
                // bundled content, so provenance still identifies exactly what was analysed.
                String revision = files.isEmpty()
                        ? "empty"
                        : JavaSourceBundle.from(files.stream()
                                .map(f -> new JavaSourceBundle.UploadedFile(
                                        f.path(), f.content().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                                .toList()).revisionHash();
                loaded.add(new ConsumerDefinition(
                        name,
                        node.path("description").asText(null),
                        node.path("consumesSchema").asText(),
                        ConsumerSourceType.BUILT_IN_SAMPLE,
                        null,
                        revision,
                        files));
            }
        } catch (IOException e) {
            log.warn("Could not read sample consumer manifest {}: {}", manifest, e.getMessage());
        }
        return loaded;
    }

    private static java.util.Optional<ConsumerSourceFile> readSource(Resource manifest, String relativePath) {
        try {
            Resource source = manifest.createRelative(relativePath);
            if (!source.exists()) {
                log.warn("Sample consumer source {} does not exist", relativePath);
                return java.util.Optional.empty();
            }
            try (InputStream stream = source.getInputStream()) {
                return java.util.Optional.of(new ConsumerSourceFile(
                        relativePath, new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            log.warn("Could not read sample consumer source {}: {}", relativePath, e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
