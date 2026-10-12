type ParamValue = string | number | boolean | null | undefined;

/** Query parameters without empty values, so unset filters are omitted instead of being sent as `key=`. */
export function compactParams(params: Record<string, ParamValue>): Record<string, string | number | boolean> {
  const result: Record<string, string | number | boolean> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') result[key] = value;
  }
  return result;
}
