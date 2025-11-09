import { getenv } from '@/src/core/environment';

export const REFERRAL_PROGRAM_FLAG_KEY = 'referral_program_phase_0';
const ENV_OVERRIDE_KEY = 'RAO_REFERRAL_PROGRAM_PHASE0_ENABLED';

export type ReferralFlagSource = 'env' | 'posthog' | 'default';

export interface ReferralFlagState {
  enabled: boolean;
  source: ReferralFlagSource;
}

/**
 * Evaluate the desktop referral flag using the environment override first,
 * followed by an optional PostHog flag value.
 */
export function evaluateReferralFlag(posthogValue?: boolean | string | null): ReferralFlagState {
  const envValue = parseBoolean(getenv(ENV_OVERRIDE_KEY));
  if (envValue !== undefined) {
    return { enabled: envValue, source: 'env' };
  }

  const posthogParsed = parseBoolean(posthogValue);
  if (posthogParsed !== undefined) {
    return { enabled: posthogParsed, source: 'posthog' };
  }

  return { enabled: false, source: 'default' };
}

export function getReferralEnvOverrideKey(): string {
  return ENV_OVERRIDE_KEY;
}

function parseBoolean(value?: string | boolean | number | null): boolean | undefined {
  if (typeof value === 'boolean') {
    return value;
  }

  if (typeof value === 'number') {
    if (Number.isNaN(value)) {
      return undefined;
    }
    return value !== 0;
  }

  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (normalized === '') {
      return undefined;
    }

    if (['1', 'true', 'yes', 'enabled', 'on', 'variant', 'treatment'].includes(normalized)) {
      return true;
    }

    if (['0', 'false', 'no', 'disabled', 'off', 'control'].includes(normalized)) {
      return false;
    }
  }

  return undefined;
}
