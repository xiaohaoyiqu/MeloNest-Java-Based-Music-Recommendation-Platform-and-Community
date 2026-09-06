export function createIdempotencyKey(prefix: string): string {
    const normalizedPrefix = prefix.trim().toLowerCase();
    if (!/^[a-z][a-z0-9_-]{1,31}$/.test(normalizedPrefix)) {
        throw new Error('幂等键前缀无效');
    }
    if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function') {
        throw new Error('当前浏览器不支持安全订单标识，请升级后重试');
    }
    return `${normalizedPrefix}-${crypto.randomUUID()}`;
}
