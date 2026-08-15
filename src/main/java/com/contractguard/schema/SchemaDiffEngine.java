package com.contractguard.schema;

import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares two Avro schemas and reports the structural differences.
 *
 * The result is deterministic: the same pair of schemas always produces the same changes in the
 * same order, because the output is sorted on its own content rather than on traversal order.
 */
@Component
public class SchemaDiffEngine {

    private static final Comparator<SchemaChange> STABLE_ORDER =
            Comparator.comparing(SchemaChange::path)
                    .thenComparing(change -> change.changeType().name())
                    .thenComparing(change -> String.valueOf(change.oldValue()))
                    .thenComparing(change -> String.valueOf(change.newValue()));

    public List<SchemaChange> diff(Schema source, Schema target) {
        List<SchemaChange> changes = new ArrayList<>();
        diffNamedType(source, target, source.getName(), changes, new HashSet<>());
        changes.sort(STABLE_ORDER);
        return List.copyOf(changes);
    }

    /** Records, enums and fixed types all carry a name and namespace. */
    private void diffNamedType(Schema source, Schema target, String path,
                               List<SchemaChange> changes, Set<String> recursionStack) {
        if (!Objects.equals(source.getName(), target.getName())) {
            changes.add(new SchemaChange(path, SchemaChangeType.RECORD_NAME_CHANGED,
                    source.getName(), target.getName()));
        }
        if (!Objects.equals(source.getNamespace(), target.getNamespace())) {
            changes.add(new SchemaChange(path, SchemaChangeType.NAMESPACE_CHANGED,
                    source.getNamespace(), target.getNamespace()));
        }

        switch (source.getType()) {
            case RECORD -> diffRecordFields(source, target, path, changes, recursionStack);
            case ENUM -> diffEnumSymbols(source, target, path, changes);
            default -> { /* fixed has no further structure to compare */ }
        }
    }

    private void diffRecordFields(Schema source, Schema target, String path,
                                  List<SchemaChange> changes, Set<String> recursionStack) {
        // Guard against recursive schemas. The key is removed again on the way out, so a record
        // reused at two sibling paths is still compared at both.
        String key = source.getFullName() + "->" + target.getFullName();
        if (!recursionStack.add(key)) {
            return;
        }

        for (Schema.Field sourceField : source.getFields()) {
            Schema.Field targetField = target.getField(sourceField.name());
            String fieldPath = path + "." + sourceField.name();
            if (targetField == null) {
                changes.add(new SchemaChange(fieldPath, SchemaChangeType.FIELD_REMOVED,
                        describe(sourceField.schema()), null));
            } else {
                diffField(sourceField, targetField, fieldPath, changes, recursionStack);
            }
        }

        for (Schema.Field targetField : target.getFields()) {
            if (source.getField(targetField.name()) == null) {
                changes.add(new SchemaChange(path + "." + targetField.name(), SchemaChangeType.FIELD_ADDED,
                        null, describe(targetField.schema())));
            }
        }

        recursionStack.remove(key);
    }

    private void diffField(Schema.Field source, Schema.Field target, String path,
                           List<SchemaChange> changes, Set<String> recursionStack) {
        if (isNullable(source.schema()) != isNullable(target.schema())) {
            changes.add(new SchemaChange(path, SchemaChangeType.FIELD_OPTIONALITY_CHANGED,
                    optionality(source.schema()), optionality(target.schema())));
        }

        String sourceDefault = renderDefault(source);
        String targetDefault = renderDefault(target);
        if (!Objects.equals(sourceDefault, targetDefault)) {
            changes.add(new SchemaChange(path, SchemaChangeType.DEFAULT_VALUE_CHANGED,
                    sourceDefault, targetDefault));
        }

        diffType(source.schema(), target.schema(), path, changes, recursionStack);
    }

    private void diffType(Schema source, Schema target, String path,
                          List<SchemaChange> changes, Set<String> recursionStack) {
        Schema sourceType = unwrapNullable(source);
        Schema targetType = unwrapNullable(target);

        if (!signature(sourceType).equals(signature(targetType))) {
            changes.add(new SchemaChange(path, SchemaChangeType.FIELD_TYPE_CHANGED,
                    describe(sourceType), describe(targetType)));
            // The types are unrelated, so descending into them would produce noise.
            return;
        }

        switch (sourceType.getType()) {
            case RECORD, ENUM, FIXED -> diffNamedType(sourceType, targetType, path, changes, recursionStack);
            case ARRAY -> diffType(sourceType.getElementType(), targetType.getElementType(),
                    path + "[]", changes, recursionStack);
            case MAP -> diffType(sourceType.getValueType(), targetType.getValueType(),
                    path + "{}", changes, recursionStack);
            default -> { /* primitives carry no further structure */ }
        }
    }

    private void diffEnumSymbols(Schema source, Schema target, String path, List<SchemaChange> changes) {
        for (String symbol : source.getEnumSymbols()) {
            if (!target.hasEnumSymbol(symbol)) {
                changes.add(new SchemaChange(path, SchemaChangeType.ENUM_SYMBOL_REMOVED, symbol, null));
            }
        }
        for (String symbol : target.getEnumSymbols()) {
            if (!source.hasEnumSymbol(symbol)) {
                changes.add(new SchemaChange(path, SchemaChangeType.ENUM_SYMBOL_ADDED, null, symbol));
            }
        }
    }

    /**
     * What makes two types "the same kind of thing". Named types compare by kind only, so a rename
     * is reported once as RECORD_NAME_CHANGED rather than also as a type change.
     */
    private static String signature(Schema schema) {
        return switch (schema.getType()) {
            case RECORD, ENUM, FIXED -> schema.getType().getName();
            case ARRAY -> "array<" + signature(schema.getElementType()) + ">";
            case MAP -> "map<" + signature(schema.getValueType()) + ">";
            case UNION -> schema.getTypes().stream()
                    .map(SchemaDiffEngine::signature)
                    .collect(Collectors.joining(",", "union<", ">"));
            default -> schema.getType().getName();
        };
    }

    /** Human-readable rendering used for oldValue and newValue. */
    private static String describe(Schema schema) {
        return switch (schema.getType()) {
            case RECORD, ENUM, FIXED -> schema.getFullName();
            case ARRAY -> "array<" + describe(schema.getElementType()) + ">";
            case MAP -> "map<" + describe(schema.getValueType()) + ">";
            case UNION -> schema.getTypes().stream()
                    .map(SchemaDiffEngine::describe)
                    .collect(Collectors.joining(",", "union<", ">"));
            default -> schema.getType().getName();
        };
    }

    private static boolean isNullable(Schema schema) {
        return schema.getType() == Schema.Type.UNION
                && schema.getTypes().stream().anyMatch(branch -> branch.getType() == Schema.Type.NULL);
    }

    private static String optionality(Schema schema) {
        return isNullable(schema) ? "OPTIONAL" : "REQUIRED";
    }

    /** Reduces the common {@code ["null", X]} idiom to X so nullability is compared separately. */
    private static Schema unwrapNullable(Schema schema) {
        if (schema.getType() != Schema.Type.UNION) {
            return schema;
        }
        List<Schema> branches = schema.getTypes().stream()
                .filter(branch -> branch.getType() != Schema.Type.NULL)
                .toList();
        return branches.size() == 1 ? branches.get(0) : schema;
    }

    /** Null when the field declares no default, so "absent" and "default null" stay distinguishable. */
    private static String renderDefault(Schema.Field field) {
        if (!field.hasDefaultValue()) {
            return null;
        }
        Object value = field.defaultVal();
        return value == JsonProperties.NULL_VALUE ? "null" : String.valueOf(value);
    }
}
