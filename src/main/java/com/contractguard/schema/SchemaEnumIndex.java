package com.contractguard.schema;

import org.apache.avro.Schema;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Indexes every enum in a schema by the same dotted path {@link SchemaDiffEngine} reports, so a
 * diff entry can be resolved back to the enum it describes.
 */
public final class SchemaEnumIndex {

    private SchemaEnumIndex() {
    }

    /** Path to enum schema, in traversal order. */
    public static Map<String, Schema> enumsByPath(Schema root) {
        Map<String, Schema> found = new LinkedHashMap<>();
        walk(root, root.getName(), found, new HashSet<>());
        return found;
    }

    private static void walk(Schema schema, String path, Map<String, Schema> found, Set<String> stack) {
        Schema current = unwrapNullable(schema);
        switch (current.getType()) {
            case ENUM -> found.putIfAbsent(path, current);
            case RECORD -> {
                if (!stack.add(current.getFullName())) {
                    return;
                }
                for (Schema.Field field : current.getFields()) {
                    walk(field.schema(), path + "." + field.name(), found, stack);
                }
                stack.remove(current.getFullName());
            }
            case ARRAY -> walk(current.getElementType(), path + "[]", found, stack);
            case MAP -> walk(current.getValueType(), path + "{}", found, stack);
            default -> { }
        }
    }

    /** Mirrors the diff engine: {@code ["null", X]} is treated as X. */
    private static Schema unwrapNullable(Schema schema) {
        if (schema.getType() != Schema.Type.UNION) {
            return schema;
        }
        var branches = schema.getTypes().stream()
                .filter(branch -> branch.getType() != Schema.Type.NULL)
                .toList();
        return branches.size() == 1 ? branches.get(0) : schema;
    }
}
