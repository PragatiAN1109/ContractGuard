import type { ProblemDetail } from './types';

/** An RFC 9457 problem response, or a transport failure when the backend is unreachable. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(message: string, status: number, problem: ProblemDetail | null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  /** Field-level validation messages, when the backend supplied them. */
  get fieldErrors(): Record<string, string> {
    return this.problem?.errors ?? {};
  }

  static unreachable(): ApiError {
    return new ApiError(
      'Cannot reach the ContractGuard API. Is the backend running?',
      0,
      null,
    );
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    });
  } catch {
    throw ApiError.unreachable();
  }

  if (!response.ok) {
    let problem: ProblemDetail | null = null;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // Not every failure carries a JSON body.
    }
    const message = problem?.detail ?? problem?.title ?? `Request failed with ${response.status}`;
    throw new ApiError(message, response.status, problem);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
};

export const API_BASE = '/api/v1';
