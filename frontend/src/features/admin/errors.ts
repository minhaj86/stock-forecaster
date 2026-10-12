import { ApiError, toApiError } from '@/api/apiError';

/** Errors of the market-data provider deserve a clearer explanation than the generic backend message. */
const FRIENDLY_MESSAGES: Record<string, string> = {
  MARKET_DATA_PROVIDER_UNAVAILABLE: 'The market-data provider is unavailable right now. Please try again in a few minutes.',
  MARKET_DATA_PROVIDER_RATE_LIMITED: 'Provider rate limit reached. Try again in a minute.',
};

/** The backend error with a friendlier message for provider failures; status, code and reference are kept. */
export function friendlyError(error: unknown): ApiError {
  const apiError = toApiError(error);
  const message = FRIENDLY_MESSAGES[apiError.code];
  return message
    ? new ApiError(apiError.status, apiError.code, message, apiError.fieldErrors, apiError.correlationId, apiError.details)
    : apiError;
}

export const errorMessage = (error: unknown) => friendlyError(error).message;

export const isNotFound = (error: unknown) => toApiError(error).status === 404;
