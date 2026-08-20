import type { AnalysisRun, AnalysisRunSummary, Project, RolloutPlan } from '../api/types';

export const projects: Project[] = [
  { id: 'p1', name: 'E-commerce Orders', description: 'Order lifecycle events', createdAt: '2026-08-15T10:00:00Z' },
  { id: 'p2', name: 'Payments', description: null, createdAt: '2026-08-14T10:00:00Z' },
];

/** Shaped exactly like the real v1 -> v2 sample response. */
export const analysisRun: AnalysisRun = {
  analysisId: 'a1',
  status: 'COMPLETED',
  projectId: 'p1',
  sourceSchemaVersionId: 's1',
  targetSchemaVersionId: 's2',
  sourceVersion: 1,
  targetVersion: 2,
  compatibility: {
    backward: {
      mode: 'BACKWARD',
      status: 'PASS',
      summary: 'The target schema can read data written with the source schema.',
      issues: [],
    },
    forward: {
      mode: 'FORWARD',
      status: 'FAIL',
      summary: 'The source schema cannot read data written with the target schema. 1 incompatibility found.',
      issues: [
        {
          issueType: 'TYPE_MISMATCH',
          path: 'OrderEvent.customerEmail',
          reason: 'The type at OrderEvent.customerEmail cannot be resolved between the two schemas.',
        },
      ],
    },
    full: {
      mode: 'FULL',
      status: 'FAIL',
      summary: 'FULL requires both directions; FORWARD failed.',
      issues: [],
    },
  },
  consumerAnalysis: {
    consumerCount: 3,
    sourceTypes: ['BUILT_IN_SAMPLE'],
    consumers: [
      {
        name: 'order-analytics-service',
        sourceType: 'BUILT_IN_SAMPLE',
        sourceFiles: ['order-analytics-service/FulfilmentMetricsCollector.java'],
      },
      {
        name: 'order-notification-service',
        sourceType: 'BUILT_IN_SAMPLE',
        sourceFiles: ['order-notification-service/OrderStatusHandler.java'],
      },
      {
        name: 'order-returns-service',
        sourceType: 'BUILT_IN_SAMPLE',
        sourceFiles: ['order-returns-service/ReturnsProcessor.java'],
      },
    ],
  },
  operationalRisk: {
    overallSeverity: 'HIGH',
    findingCount: 2,
    findings: [
      {
        ruleId: 'ENUM_SEMANTIC_FALLBACK_RISK',
        severity: 'HIGH',
        consumer: 'order-notification-service',
        schemaPath: 'OrderEvent.status',
        attributes: {
          enumName: 'OrderStatus',
          newSymbol: 'RETURNED',
          fallbackSymbol: 'CREATED',
          usageKind: 'SWITCH_CASE',
        },
        evidence: {
          sourceFile: 'OrderStatusHandler.java',
          filePath: 'order-notification-service/OrderStatusHandler.java',
          line: 20,
          snippet: 'case CREATED -> sendNewOrderNotification(order);',
        },
        reason: "The proposed schema adds 'RETURNED' to OrderEvent.status.",
      },
      {
        // Same rule and consumer, a second distinct source location — mirrors the real sample.
        ruleId: 'ENUM_SEMANTIC_FALLBACK_RISK',
        severity: 'HIGH',
        consumer: 'order-notification-service',
        schemaPath: 'OrderEvent.status',
        attributes: {
          enumName: 'OrderStatus',
          newSymbol: 'RETURNED',
          fallbackSymbol: 'CREATED',
          usageKind: 'EQUALITY_COMPARISON',
        },
        evidence: {
          sourceFile: 'OrderStatusHandler.java',
          filePath: 'order-notification-service/OrderStatusHandler.java',
          line: 34,
          snippet: 'return order.getStatus() == OrderStatus.CREATED;',
        },
        reason: "The proposed schema adds 'RETURNED' to OrderEvent.status.",
      },
    ],
  },
  failureCode: null,
  failureMessage: null,
  createdAt: '2026-08-15T12:00:00Z',
  startedAt: '2026-08-15T12:00:00Z',
  completedAt: '2026-08-15T12:00:01Z',
};

export const rolloutPlan: RolloutPlan = {
  analysisId: 'a1',
  strategy: 'CONSUMER_FIRST',
  summary: 'One affected consumer should be updated before producers use the target schema.',
  steps: [
    {
      order: 1,
      action: 'UPDATE_CONSUMER',
      target: 'order-notification-service',
      reason: "order-notification-service may interpret 'RETURNED' as 'CREATED'.",
    },
    { order: 2, action: 'VERIFY_CONSUMER_DEPLOYMENT', target: 'order-notification-service', reason: null },
    { order: 3, action: 'DEPLOY_SCHEMA', target: 'schema version 2', reason: null },
  ],
  limitations: ['Guidance is based only on the rules currently implemented by ContractGuard.'],
};

export const history: AnalysisRunSummary[] = [
  {
    analysisId: 'a1',
    status: 'COMPLETED',
    sourceVersion: 1,
    targetVersion: 2,
    compatibility: { backward: 'PASS', forward: 'FAIL', full: 'FAIL' },
    highestSeverity: 'HIGH',
    findingCount: 1,
    createdAt: '2026-08-15T12:00:00Z',
    completedAt: '2026-08-15T12:00:01Z',
  },
];
