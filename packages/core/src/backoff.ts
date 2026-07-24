export function computeBackoff(
  attempt: number,
  baseMs: number,
  maxMs: number,
  random: () => number = Math.random
): number {
  const exponential = baseMs * 2 ** attempt;
  const capped = Math.min(exponential, maxMs);
  const jitter = capped * 0.25 * random();
  return Math.round(capped - capped * 0.25 + jitter);
}
