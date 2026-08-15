package com.contractguard.consumeranalysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds where a Java source file names symbols of a particular Avro enum.
 *
 * Deliberately narrow. There is no symbol resolution, so the analysis never needs the consumer's
 * classpath; attribution to the right enum is done with two conservative signals instead:
 * the file must reference the enum's simple name, and either every label of a switch must be one
 * of the enum's symbols, or the comparison must be qualified as {@code EnumName.SYMBOL}.
 */
@Component
public class JavaEnumUsageScanner {

    /** @param usages usages found; empty when the file could not be parsed */
    record ScanResult(List<EnumUsage> usages, Optional<String> warning) {

        static ScanResult of(List<EnumUsage> usages) {
            return new ScanResult(List.copyOf(usages), Optional.empty());
        }

        static ScanResult failed(String warning) {
            return new ScanResult(List.of(), Optional.of(warning));
        }
    }

    ScanResult scan(ConsumerSourceFile file, String enumSimpleName, Set<String> knownSymbols) {
        ParseResult<CompilationUnit> parsed;
        try {
            parsed = new JavaParser().parse(file.content());
        } catch (RuntimeException e) {
            return ScanResult.failed(file.path() + ": could not be parsed (" + e.getClass().getSimpleName() + ")");
        }
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            return ScanResult.failed(file.path() + ": is not valid Java source and was skipped");
        }

        CompilationUnit unit = parsed.getResult().get();
        if (!referencesType(unit, enumSimpleName)) {
            return ScanResult.of(List.of());
        }

        String[] lines = file.content().split("\n", -1);
        List<EnumUsage> usages = new ArrayList<>();
        collectSwitchUsages(unit, knownSymbols, lines, usages);
        collectComparisonUsages(unit, enumSimpleName, knownSymbols, lines, usages);
        return ScanResult.of(usages);
    }

    private static boolean referencesType(CompilationUnit unit, String enumSimpleName) {
        boolean imported = unit.getImports().stream()
                .anyMatch(imported_ -> imported_.getNameAsString().equals(enumSimpleName)
                        || imported_.getNameAsString().endsWith("." + enumSimpleName));
        if (imported) {
            return true;
        }
        boolean asType = unit.findAll(ClassOrInterfaceType.class).stream()
                .anyMatch(type -> type.getNameAsString().equals(enumSimpleName));
        return asType || unit.findAll(FieldAccessExpr.class).stream()
                .anyMatch(access -> access.getScope().toString().equals(enumSimpleName));
    }

    private static void collectSwitchUsages(CompilationUnit unit, Set<String> knownSymbols,
                                            String[] lines, List<EnumUsage> usages) {
        List<NodeList<SwitchEntry>> switches = new ArrayList<>();
        unit.findAll(SwitchStmt.class).forEach(node -> switches.add(node.getEntries()));
        unit.findAll(SwitchExpr.class).forEach(node -> switches.add(node.getEntries()));

        for (NodeList<SwitchEntry> entries : switches) {
            Set<String> labels = new LinkedHashSet<>();
            boolean allLabelsKnown = true;
            for (SwitchEntry entry : entries) {
                for (Expression label : entry.getLabels()) {
                    String symbol = symbolName(label);
                    if (symbol == null || !knownSymbols.contains(symbol)) {
                        allLabelsKnown = false;
                    } else {
                        labels.add(symbol);
                    }
                }
            }
            // A switch over some other enum that happens to share a constant name is not ours.
            if (!allLabelsKnown || labels.isEmpty()) {
                continue;
            }
            for (SwitchEntry entry : entries) {
                for (Expression label : entry.getLabels()) {
                    String symbol = symbolName(label);
                    int line = label.getBegin().map(pos -> pos.line).orElse(0);
                    usages.add(new EnumUsage(symbol, EnumUsageKind.SWITCH_CASE, line, sourceLine(lines, line)));
                }
            }
        }
    }

    private static void collectComparisonUsages(CompilationUnit unit, String enumSimpleName,
                                                Set<String> knownSymbols, String[] lines,
                                                List<EnumUsage> usages) {
        for (BinaryExpr comparison : unit.findAll(BinaryExpr.class)) {
            if (comparison.getOperator() != BinaryExpr.Operator.EQUALS
                    && comparison.getOperator() != BinaryExpr.Operator.NOT_EQUALS) {
                continue;
            }
            for (Expression side : List.of(comparison.getLeft(), comparison.getRight())) {
                String symbol = qualifiedSymbol(side, enumSimpleName);
                if (symbol != null && knownSymbols.contains(symbol)) {
                    int line = side.getBegin().map(pos -> pos.line).orElse(0);
                    usages.add(new EnumUsage(symbol, EnumUsageKind.EQUALITY_COMPARISON, line,
                            sourceLine(lines, line)));
                }
            }
        }
    }

    /** Case labels appear either bare ({@code case CREATED}) or qualified. */
    private static String symbolName(Expression label) {
        if (label instanceof NameExpr name) {
            return name.getNameAsString();
        }
        if (label instanceof FieldAccessExpr access) {
            return access.getNameAsString();
        }
        return null;
    }

    /** Only {@code EnumName.SYMBOL} counts; a bare name in a comparison is too ambiguous. */
    private static String qualifiedSymbol(Expression expression, String enumSimpleName) {
        if (expression instanceof FieldAccessExpr access
                && access.getScope().toString().equals(enumSimpleName)) {
            return access.getNameAsString();
        }
        return null;
    }

    private static String sourceLine(String[] lines, int line) {
        if (line < 1 || line > lines.length) {
            return "";
        }
        return lines[line - 1].trim();
    }
}
