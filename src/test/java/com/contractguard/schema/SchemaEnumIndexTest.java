package com.contractguard.schema;

import org.apache.avro.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaEnumIndexTest {

    private static Schema parse(String schema) {
        return new Schema.Parser().parse(schema);
    }

    @Test
    @DisplayName("indexes a top-level enum field by its dotted path")
    void indexesTopLevelEnum() {
        Schema schema = parse("""
                {"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[
                  {"name":"status","type":{"type":"enum","name":"OrderStatus",
                     "symbols":["CREATED","PAID"],"default":"CREATED"}}]}
                """);

        Map<String, Schema> enums = SchemaEnumIndex.enumsByPath(schema);

        assertThat(enums).containsOnlyKeys("OrderEvent.status");
        assertThat(enums.get("OrderEvent.status").getEnumDefault()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("indexes enums inside arrays, maps and nullable unions")
    void indexesNestedEnums() {
        Schema schema = parse("""
                {"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[
                  {"name":"items","type":{"type":"array","items":
                     {"type":"record","name":"OrderLine","fields":[
                        {"name":"lineStatus","type":{"type":"enum","name":"LineStatus",
                           "symbols":["PENDING"],"default":"PENDING"}}]}}},
                  {"name":"labels","type":{"type":"map","values":
                     {"type":"enum","name":"LabelKind","symbols":["FRAGILE"]}}},
                  {"name":"channel","type":["null",
                     {"type":"enum","name":"Channel","symbols":["WEB"]}],"default":null}]}
                """);

        assertThat(SchemaEnumIndex.enumsByPath(schema)).containsOnlyKeys(
                "OrderEvent.items[].lineStatus",
                "OrderEvent.labels{}",
                "OrderEvent.channel");
    }

    @Test
    @DisplayName("a schema with no enums yields an empty index")
    void noEnums() {
        assertThat(SchemaEnumIndex.enumsByPath(parse("""
                {"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[
                  {"name":"orderId","type":"string"}]}
                """))).isEmpty();
    }

    @Test
    @DisplayName("a recursive schema terminates")
    void recursiveSchemaTerminates() {
        Schema schema = parse("""
                {"type":"record","name":"Node","namespace":"com.example","fields":[
                  {"name":"kind","type":{"type":"enum","name":"Kind","symbols":["LEAF"]}},
                  {"name":"next","type":["null","Node"],"default":null}]}
                """);

        assertThat(SchemaEnumIndex.enumsByPath(schema)).containsOnlyKeys("Node.kind");
    }

    @Test
    @DisplayName("every ENUM_SYMBOL_ADDED path from the diff resolves in the index")
    void diffPathsResolveAgainstTheIndex() {
        // Pins the two components to one path convention; drift here would silently disable the
        // enum fallback rule.
        Schema v1 = parse(sample("order-v1.avsc"));
        Schema v2 = parse(sample("order-v2.avsc"));

        List<String> enumChangePaths = new SchemaDiffEngine().diff(v1, v2).stream()
                .filter(change -> change.changeType() == SchemaChangeType.ENUM_SYMBOL_ADDED)
                .map(SchemaChange::path)
                .toList();

        assertThat(enumChangePaths).containsExactly("OrderEvent.status");
        assertThat(SchemaEnumIndex.enumsByPath(v1)).containsKeys(enumChangePaths.toArray(String[]::new));
    }

    private static String sample(String fileName) {
        try (var stream = SchemaEnumIndexTest.class.getResourceAsStream("/samples/ecommerce-order/" + fileName)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("sample " + fileName + " is missing", e);
        }
    }
}
