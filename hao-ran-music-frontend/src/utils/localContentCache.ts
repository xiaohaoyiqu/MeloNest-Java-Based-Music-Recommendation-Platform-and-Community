export const LOCAL_CONTENT_CACHE_KEYS = [
    'discover:curated-carousel:v1',
    'news_discovery_cache',
    'push_notifications_cache'
] as const;
export interface LocalCacheClearResult {
    removed: number;
    failed: number;
}
export function clearLocalContentCache(storage: Storage = localStorage): LocalCacheClearResult {
    let removed = 0;
    let failed = 0;
    LOCAL_CONTENT_CACHE_KEYS.forEach(key => {
        try {
            if (storage.getItem(key) !== null)
                removed += 1;
            storage.removeItem(key);
        }
        catch {
            failed += 1;
        }
    });
    return { removed, failed };
}
export function getLocalContentCacheBytes(storage: Storage = localStorage): number {
    return LOCAL_CONTENT_CACHE_KEYS.reduce((total, key) => {
        try {
            const value = storage.getItem(key);
            return value === null ? total : total + (key.length + value.length) * 2;
        }
        catch {
            return total;
        }
    }, 0);
}
