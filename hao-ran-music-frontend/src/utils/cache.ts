interface CacheEntry {
    data: any;
    timestamp: number;
    expire: number;
}
const cache = new Map<string, CacheEntry>();
const DEFAULT_EXPIRE = 5 * 60 * 1000;
export function getCached<T>(key: string): T | null {
    const entry = cache.get(key);
    if (!entry)
        return null;
    const now = Date.now();
    if (now - entry.timestamp > entry.expire) {
        cache.delete(key);
        return null;
    }
    return entry.data as T;
}
export function setCached<T>(key: string, data: T, expire: number = DEFAULT_EXPIRE): void {
    cache.set(key, {
        data,
        timestamp: Date.now(),
        expire
    });
}
export function clearCache(pattern?: string): void {
    if (!pattern) {
        cache.clear();
        return;
    }
    for (const key of cache.keys()) {
        if (key.includes(pattern)) {
            cache.delete(key);
        }
    }
}
export async function fetchWithCache<T>(key: string, fetcher: () => Promise<T>, expire: number = DEFAULT_EXPIRE): Promise<T> {
    const cached = getCached<T>(key);
    if (cached !== null) {
        return cached;
    }
    const data = await fetcher();
    setCached(key, data, expire);
    return data;
}
setInterval(() => {
    const now = Date.now();
    for (const [key, entry] of cache.entries()) {
        if (now - entry.timestamp > entry.expire) {
            cache.delete(key);
        }
    }
}, 60 * 1000);
export default {
    getCached,
    setCached,
    clearCache,
    fetchWithCache
};
