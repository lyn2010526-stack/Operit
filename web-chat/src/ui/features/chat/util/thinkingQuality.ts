const GPT56_MODELS = new Set([
  'gpt-5.6',
  'gpt-5.6-sol',
  'gpt-5.6-terra',
  'gpt-5.6-luna'
]);

const OPENAI_PROVIDER_TYPES = new Set([
  'OPENAI',
  'OPENAI_GENERIC',
  'OPENAI_RESPONSES',
  'OPENAI_RESPONSES_GENERIC'
]);

export const MIN_THINKING_QUALITY_LEVEL = 1;
export const LEGACY_MAX_THINKING_QUALITY_LEVEL = 4;
export const GPT56_MAX_THINKING_QUALITY_LEVEL = 5;

export function getMaxThinkingQualityLevel(
  providerType: string | null | undefined,
  modelName: string | null | undefined
): number {
  const normalizedProviderType = providerType?.trim().toUpperCase() ?? '';
  const normalizedModelName = modelName?.trim().toLowerCase() ?? '';

  return OPENAI_PROVIDER_TYPES.has(normalizedProviderType) && GPT56_MODELS.has(normalizedModelName)
    ? GPT56_MAX_THINKING_QUALITY_LEVEL
    : LEGACY_MAX_THINKING_QUALITY_LEVEL;
}

export function clampThinkingQualityLevel(value: number, maxLevel: number): number {
  return Math.max(MIN_THINKING_QUALITY_LEVEL, Math.min(maxLevel, value));
}
