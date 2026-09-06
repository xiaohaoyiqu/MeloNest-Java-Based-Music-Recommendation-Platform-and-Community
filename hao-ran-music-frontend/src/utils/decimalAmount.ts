export type DecimalAmountInput = string | number | null | undefined;
export function formatDecimalAmount(value: DecimalAmountInput, fractionDigits = 2): string {
    const digits = Number.isInteger(fractionDigits)
        ? Math.max(0, Math.min(fractionDigits, 20))
        : 2;
    const raw = String(value ?? '0').trim();
    const match = raw.match(/^([+-]?)(\d+)(?:\.(\d+))?$/);
    if (!match) {
        const numeric = Number(value);
        return Number.isFinite(numeric)
            ? numeric.toFixed(digits)
            : `0${digits ? `.${'0'.repeat(digits)}` : ''}`;
    }
    const sign = match[1];
    const whole = match[2].replace(/^0+(?=\d)/, '');
    const fraction = match[3] ?? '';
    const retained = fraction.slice(0, digits).padEnd(digits, '0');
    let scaled = BigInt(`${whole}${retained}` || '0');
    if ((fraction[digits] ?? '0') >= '5') {
        scaled += 1n;
    }
    const scaledText = scaled.toString().padStart(digits + 1, '0');
    const formatted = digits === 0
        ? scaledText
        : `${scaledText.slice(0, -digits)}.${scaledText.slice(-digits)}`;
    return sign === '-' && scaled !== 0n ? `-${formatted}` : formatted;
}
