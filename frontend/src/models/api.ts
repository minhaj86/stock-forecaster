/** Error envelope returned by every backend endpoint. */
export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  code: string;
  message: string;
  path?: string;
  correlationId?: string;
  errors?: FieldViolation[];
  /** Structured, error-specific values, e.g. required and available history. */
  details?: Record<string, unknown>;
}

export interface FieldViolation {
  field: string;
  message: string;
}

export interface Page<T> {
  content: T[];
  page: { number: number; size: number; totalElements: number; totalPages: number };
}
