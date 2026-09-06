export type ExactCount = string | number;
export type ExactCountInput = ExactCount | null | undefined;
const TEN_THOUSAND = 10000n;
const HUNDRED_MILLION = 100000000n;
function toNonNegativeBigInt(value: ExactCountInput): bigint {
    if (typeof value === 'string') {
        const text = value.trim();
        return /^\d+$/.test(text) ? BigInt(text) : 0n;
    }
    return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
        ? BigInt(value)
        : 0n;
}
function formatCompact(value: bigint, divisor: bigint, suffix: string): string {
    const tenths = (value * 10n + divisor / 2n) / divisor;
    return `${tenths / 10n}.${tenths % 10n}${suffix}`;
}
export function formatExactCount(value: ExactCountInput): string {
    const count = toNonNegativeBigInt(value);
    if (count >= HUNDRED_MILLION)
        return formatCompact(count, HUNDRED_MILLION, '亿');
    if (count >= TEN_THOUSAND)
        return formatCompact(count, TEN_THOUSAND, '万');
    return count.toString();
}
export function adjustExactCount(value: ExactCountInput, delta: number): string {
    const adjustment = Number.isSafeInteger(delta) ? BigInt(delta) : 0n;
    const adjusted = toNonNegativeBigInt(value) + adjustment;
    return (adjusted < 0n ? 0n : adjusted).toString();
}
export function isExactCountGreaterThan(value: ExactCountInput, baseline: ExactCountInput): boolean {
    return toNonNegativeBigInt(value) > toNonNegativeBigInt(baseline);
}
