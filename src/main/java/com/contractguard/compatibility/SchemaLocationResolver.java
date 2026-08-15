package com.contractguard.compatibility;

import org.apache.avro.Schema;

/**
 * Turns Avro's incompatibility location into a ContractGuard field path.
 *
 * Avro reports locations as a JSON pointer into the reader schema, such as
 * {@code /fields/0/type/items/fields/0/type}. This walks that pointer to produce the same dotted
 * notation the schema diff uses — {@code OrderEvent.items[].sku} — so both features describe the
 * same place in the same way.
 */
final class SchemaLocationResolver {

    private SchemaLocationResolver() {
    }

    /**
     * @param location Avro's pointer, or null
     * @param reader   the schema the pointer refers to
     * @return a dotted path, or Avro's raw pointer if it cannot be walked, or null if there is none
     */
    static String resolve(String location, Schema reader) {
        if (location == null || location.isBlank()) {
            return null;
        }

        String[] segments = location.split("/");
        StringBuilder path = new StringBuilder(reader.getName());
        Schema current = reader;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                continue;
            }
            switch (segment) {
                case "fields" -> {
                    // The next segment is the field index; step onto the field's own type.
                    if (i + 1 >= segments.length || current.getType() != Schema.Type.RECORD) {
                        return path.toString();
                    }
                    Integer index = asInt(segments[++i]);
                    if (index == null || index < 0 || index >= current.getFields().size()) {
                        return path.toString();
                    }
                    Schema.Field field = current.getFields().get(index);
                    path.append('.').append(field.name());
                    current = field.schema();
                }
                case "items" -> {
                    if (current.getType() != Schema.Type.ARRAY) {
                        return path.toString();
                    }
                    path.append("[]");
                    current = current.getElementType();
                }
                case "values" -> {
                    if (current.getType() != Schema.Type.MAP) {
                        return path.toString();
                    }
                    path.append("{}");
                    current = current.getValueType();
                }
                // "type" is a no-op: stepping onto a field already moved us to its type.
                // "symbols", "name" and "size" identify a property of the current schema, not a
                // deeper location, so the path is already correct.
                case "type", "symbols", "name", "size" -> { }
                default -> {
                    // A bare number here indexes a union branch. Avro sometimes reports the
                    // writer's branch index, which need not exist on the reader, so anything
                    // unresolvable ends the walk at the best path found so far.
                    Integer branch = asInt(segment);
                    if (branch == null || current.getType() != Schema.Type.UNION
                            || branch < 0 || branch >= current.getTypes().size()) {
                        return path.toString();
                    }
                    current = current.getTypes().get(branch);
                }
            }
        }
        return path.toString();
    }

    private static Integer asInt(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
