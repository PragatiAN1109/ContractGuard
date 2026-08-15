package com.contractguard.rollout;

import com.contractguard.history.AnalysisRunFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RolloutPlannerTest {

    private final RolloutPlanner planner = new RolloutPlanner();

    @Nested
    @DisplayName("enum fallback risk")
    class EnumFallback {

        @Test
        @DisplayName("one affected consumer produces consumer-first guidance in order")
        void singleConsumerProducesConsumerFirstPlan() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .allModes("PASS", "PASS", "PASS")
                    .enumFallbackFinding("order-notification-service", "OrderEvent.status",
                            "RETURNED", "CREATED")
                    .completed());

            assertThat(plan.strategy()).isEqualTo(RolloutStrategy.CONSUMER_FIRST);
            assertThat(plan.steps()).extracting(RolloutStep::action).containsExactly(
                    RolloutAction.UPDATE_CONSUMER,
                    RolloutAction.VERIFY_CONSUMER_DEPLOYMENT,
                    RolloutAction.DEPLOY_SCHEMA,
                    RolloutAction.BEGIN_PRODUCING);
            assertThat(plan.steps()).extracting(RolloutStep::order).containsExactly(1, 2, 3, 4);

            RolloutStep update = plan.steps().get(0);
            assertThat(update.target()).isEqualTo("order-notification-service");
            assertThat(update.reason())
                    .contains("may interpret 'RETURNED' as 'CREATED'")
                    .contains("OrderEvent.status");
        }

        @Test
        @DisplayName("symbols and consumer names come from stored attributes, not hardcoded values")
        void usesStoredAttributes() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .allModes("PASS", "PASS", "PASS")
                    .enumFallbackFinding("billing-service", "PaymentEvent.state", "DISPUTED", "PENDING")
                    .completed());

            assertThat(plan.steps().get(0).target()).isEqualTo("billing-service");
            assertThat(plan.steps().get(0).reason())
                    .contains("'DISPUTED'").contains("'PENDING'").contains("PaymentEvent.state");
            assertThat(plan.steps()).noneMatch(step -> step.reason().contains("RETURNED"));
        }

        @Test
        @DisplayName("two findings for one consumer collapse into a single update step")
        void duplicateStepsAreRemoved() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .allModes("PASS", "PASS", "PASS")
                    .enumFallbackFinding("order-notification-service", "OrderEvent.status",
                            "RETURNED", "CREATED")
                    .enumFallbackFinding("order-notification-service", "OrderEvent.status",
                            "RETURNED", "CREATED")
                    .completed());

            assertThat(plan.steps())
                    .filteredOn(step -> step.action() == RolloutAction.UPDATE_CONSUMER)
                    .hasSize(1);
            assertThat(plan.steps()).hasSize(4);
        }

        @Test
        @DisplayName("multiple consumers each get steps, ordered by name")
        void multipleConsumersAreOrderedDeterministically() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .allModes("PASS", "PASS", "PASS")
                    .enumFallbackFinding("zeta-service", "OrderEvent.status", "RETURNED", "CREATED")
                    .enumFallbackFinding("alpha-service", "OrderEvent.status", "RETURNED", "CREATED")
                    .completed());

            assertThat(plan.steps())
                    .filteredOn(step -> step.action() == RolloutAction.UPDATE_CONSUMER)
                    .extracting(RolloutStep::target)
                    .containsExactly("alpha-service", "zeta-service");
            assertThat(plan.summary()).contains("2 affected consumers");
        }

        @Test
        @DisplayName("distinct symbols for one consumer are all explained in one step")
        void distinctSymbolsAreMerged() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .allModes("PASS", "PASS", "PASS")
                    .enumFallbackFinding("svc", "OrderEvent.status", "RETURNED", "CREATED")
                    .enumFallbackFinding("svc", "OrderEvent.status", "REFUNDED", "CREATED")
                    .completed());

            assertThat(plan.steps())
                    .filteredOn(step -> step.action() == RolloutAction.UPDATE_CONSUMER)
                    .singleElement()
                    .satisfies(step -> assertThat(step.reason())
                            .contains("'REFUNDED'").contains("'RETURNED'"));
        }

        @Test
        @DisplayName("a finding from another rule does not drive enum guidance")
        void otherRulesAreIgnored() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .allModes("PASS", "PASS", "PASS")
                    .finding("SOME_FUTURE_RULE", "svc", "OrderEvent.x", "A", "B")
                    .completed());

            assertThat(plan.strategy()).isEqualTo(RolloutStrategy.NO_CONSTRAINT_IDENTIFIED);
        }
    }

    @Nested
    @DisplayName("compatibility failures")
    class CompatibilityFailures {

        @Test
        @DisplayName("a BACKWARD failure blocks the rollout and asks for a revision")
        void backwardFailureBlocks() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .compatibility("BACKWARD", "FAIL", "OrderEvent.totalCents")
                    .compatibility("FORWARD", "PASS")
                    .compatibility("FULL", "FAIL")
                    .completed());

            assertThat(plan.strategy()).isEqualTo(RolloutStrategy.BLOCKED_BY_COMPATIBILITY);
            assertThat(plan.steps()).extracting(RolloutStep::action)
                    .containsExactly(RolloutAction.REVISE_SCHEMA, RolloutAction.RE_RUN_ANALYSIS);
            assertThat(plan.steps().get(0).reason())
                    .contains("BACKWARD compatibility fails")
                    .contains("OrderEvent.totalCents")
                    .contains("mixed-version rollout");
            // A blocked plan must not tell anyone to deploy the schema.
            assertThat(plan.steps()).noneMatch(step -> step.action() == RolloutAction.DEPLOY_SCHEMA);
        }

        @Test
        @DisplayName("a FORWARD failure asks for consumers to be upgraded first")
        void forwardFailureIsConsumerFirst() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .compatibility("BACKWARD", "PASS")
                    .compatibility("FORWARD", "FAIL", "OrderEvent.customerEmail")
                    .compatibility("FULL", "FAIL")
                    .completed());

            assertThat(plan.strategy()).isEqualTo(RolloutStrategy.CONSUMER_FIRST);
            assertThat(plan.steps()).extracting(RolloutStep::action)
                    .containsExactly(RolloutAction.UPGRADE_CONSUMERS, RolloutAction.DEPLOY_SCHEMA);
            assertThat(plan.steps().get(0).reason())
                    .contains("FORWARD compatibility fails")
                    .contains("OrderEvent.customerEmail");
            // No enum finding, so nothing to begin producing.
            assertThat(plan.steps()).noneMatch(step -> step.action() == RolloutAction.BEGIN_PRODUCING);
        }

        @Test
        @DisplayName("a compatibility failure and a risk finding together produce both sets of steps")
        void compatibilityFailureAndRiskCombine() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .compatibility("BACKWARD", "PASS")
                    .compatibility("FORWARD", "FAIL", "OrderEvent.customerEmail")
                    .compatibility("FULL", "FAIL")
                    .enumFallbackFinding("order-notification-service", "OrderEvent.status",
                            "RETURNED", "CREATED")
                    .completed());

            assertThat(plan.strategy()).isEqualTo(RolloutStrategy.CONSUMER_FIRST);
            assertThat(plan.steps()).extracting(RolloutStep::action).containsExactly(
                    RolloutAction.UPGRADE_CONSUMERS,
                    RolloutAction.UPDATE_CONSUMER,
                    RolloutAction.VERIFY_CONSUMER_DEPLOYMENT,
                    RolloutAction.DEPLOY_SCHEMA,
                    RolloutAction.BEGIN_PRODUCING);
            assertThat(plan.summary()).contains("FORWARD compatibility also fails");
        }

        @Test
        @DisplayName("BACKWARD failure keeps consumer steps but stays blocked")
        void backwardFailureStillListsConsumers() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .compatibility("BACKWARD", "FAIL", "OrderEvent.totalCents")
                    .compatibility("FORWARD", "FAIL")
                    .compatibility("FULL", "FAIL")
                    .enumFallbackFinding("svc", "OrderEvent.status", "RETURNED", "CREATED")
                    .completed());

            assertThat(plan.strategy()).isEqualTo(RolloutStrategy.BLOCKED_BY_COMPATIBILITY);
            assertThat(plan.steps()).extracting(RolloutStep::action).containsExactly(
                    RolloutAction.REVISE_SCHEMA,
                    RolloutAction.RE_RUN_ANALYSIS,
                    RolloutAction.UPDATE_CONSUMER,
                    RolloutAction.VERIFY_CONSUMER_DEPLOYMENT);
        }
    }

    @Nested
    @DisplayName("nothing detected")
    class NothingDetected {

        @Test
        @DisplayName("a clean analysis states that no rule fired, never that it is safe")
        void cleanAnalysisIsNotDeclaredSafe() {
            RolloutPlan plan = planner.plan(AnalysisRunFixture.analysis()
                    .allModes("PASS", "PASS", "PASS")
                    .completed());

            assertThat(plan.strategy()).isEqualTo(RolloutStrategy.NO_CONSTRAINT_IDENTIFIED);
            assertThat(plan.steps()).isEmpty();
            assertThat(plan.summary()).isEqualTo(
                    "No rollout constraint was identified by the currently implemented ContractGuard rules.");
            assertThat(plan.summary().toLowerCase()).doesNotContain("safe");
        }

        @Test
        @DisplayName("limitations are always stated")
        void limitationsAreAlwaysPresent() {
            List<RolloutPlan> plans = List.of(
                    planner.plan(AnalysisRunFixture.analysis().allModes("PASS", "PASS", "PASS").completed()),
                    planner.plan(AnalysisRunFixture.analysis()
                            .compatibility("BACKWARD", "FAIL").completed()));

            assertThat(plans).allSatisfy(plan -> {
                assertThat(plan.limitations()).hasSize(3);
                assertThat(plan.limitations().get(1)).contains("not proof that the change is safe");
            });
        }
    }

    @Nested
    @DisplayName("determinism")
    class Determinism {

        @Test
        @DisplayName("repeated runs over the same snapshot return an identical plan")
        void repeatedPlansAreIdentical() {
            var run = AnalysisRunFixture.analysis()
                    .compatibility("BACKWARD", "PASS")
                    .compatibility("FORWARD", "FAIL", "OrderEvent.customerEmail")
                    .compatibility("FULL", "FAIL")
                    .enumFallbackFinding("zeta-service", "OrderEvent.status", "RETURNED", "CREATED")
                    .enumFallbackFinding("alpha-service", "OrderEvent.status", "RETURNED", "CREATED")
                    .completed();

            RolloutPlan first = planner.plan(run);
            for (int attempt = 0; attempt < 20; attempt++) {
                assertThat(planner.plan(run)).isEqualTo(first);
            }
        }

        @Test
        @DisplayName("finding order in the snapshot does not change the plan")
        void findingOrderDoesNotMatter() {
            RolloutPlan first = planner.plan(AnalysisRunFixture.analysis()
                    .allModes("PASS", "PASS", "PASS")
                    .enumFallbackFinding("b-service", "OrderEvent.status", "RETURNED", "CREATED")
                    .enumFallbackFinding("a-service", "OrderEvent.status", "RETURNED", "CREATED")
                    .completed());

            RolloutPlan reversed = planner.plan(AnalysisRunFixture.analysis()
                    .allModes("PASS", "PASS", "PASS")
                    .enumFallbackFinding("a-service", "OrderEvent.status", "RETURNED", "CREATED")
                    .enumFallbackFinding("b-service", "OrderEvent.status", "RETURNED", "CREATED")
                    .completed());

            assertThat(reversed).isEqualTo(first);
        }
    }
}
