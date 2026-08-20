package com.contractguard.consumeranalysis;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Merges bundled sample consumers with the user's registered uploads.
 *
 * A registered upload wins over a bundled sample of the same service name, so a user can supply
 * their own version of a demo service without editing the classpath.
 */
@Component
@Primary
public class CompositeConsumerRegistry implements ConsumerRegistry {

    private final PersistedConsumerRegistry uploaded;
    private final List<ConsumerRegistry> bundled;

    public CompositeConsumerRegistry(PersistedConsumerRegistry uploaded,
                                     List<ConsumerRegistry> allRegistries) {
        this.uploaded = uploaded;
        this.bundled = allRegistries.stream()
                .filter(registry -> !(registry instanceof PersistedConsumerRegistry)
                        && !(registry instanceof CompositeConsumerRegistry))
                .toList();
    }

    @Override
    public List<ConsumerDefinition> findByConsumedSchema(UUID projectId, String schemaFullName) {
        Map<String, ConsumerDefinition> byName = new LinkedHashMap<>();
        for (ConsumerRegistry registry : bundled) {
            registry.findByConsumedSchema(projectId, schemaFullName)
                    .forEach(consumer -> byName.putIfAbsent(consumer.name(), consumer));
        }
        // Registered uploads override same-named bundled samples.
        uploaded.findByConsumedSchema(projectId, schemaFullName)
                .forEach(consumer -> byName.put(consumer.name(), consumer));

        List<ConsumerDefinition> merged = new ArrayList<>(byName.values());
        merged.sort(Comparator.comparing(ConsumerDefinition::name));
        return List.copyOf(merged);
    }
}
