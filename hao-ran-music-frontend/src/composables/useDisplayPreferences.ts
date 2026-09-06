import { onBeforeUnmount, onMounted, readonly, shallowRef } from 'vue';
export const SHOW_CURATED_CAROUSELS_KEY = 'haoranmusic:display:curated-carousels';
export const AUTOPLAY_CURATED_CAROUSELS_KEY = 'haoranmusic:display:carousel-autoplay';
export const SQUARE_BANNER_DISMISSED_DATE_KEY = 'music_square_banner_closed_date';
export const PUSH_NOTIFICATIONS_DISMISSED_DATE_KEY = 'push_closed_date';
const showCuratedCarousels = shallowRef(true);
const autoplayCuratedCarousels = shallowRef(true);
function readBoolean(key: string, fallback: boolean): boolean {
    try {
        const value = localStorage.getItem(key);
        if (value === 'true')
            return true;
        if (value === 'false')
            return false;
    }
    catch {
    }
    return fallback;
}
function reloadDisplayPreferences() {
    showCuratedCarousels.value = readBoolean(SHOW_CURATED_CAROUSELS_KEY, true);
    autoplayCuratedCarousels.value = readBoolean(AUTOPLAY_CURATED_CAROUSELS_KEY, true);
}
function persistBoolean(key: string, value: boolean): boolean {
    try {
        localStorage.setItem(key, String(value));
        return true;
    }
    catch {
        return false;
    }
}
function setShowCuratedCarousels(value: boolean): boolean {
    showCuratedCarousels.value = value;
    return persistBoolean(SHOW_CURATED_CAROUSELS_KEY, value);
}
function setAutoplayCuratedCarousels(value: boolean): boolean {
    autoplayCuratedCarousels.value = value;
    return persistBoolean(AUTOPLAY_CURATED_CAROUSELS_KEY, value);
}
function resetDisplayPreferences(): boolean {
    try {
        localStorage.removeItem(SHOW_CURATED_CAROUSELS_KEY);
        localStorage.removeItem(AUTOPLAY_CURATED_CAROUSELS_KEY);
        localStorage.removeItem(SQUARE_BANNER_DISMISSED_DATE_KEY);
        localStorage.removeItem(PUSH_NOTIFICATIONS_DISMISSED_DATE_KEY);
        showCuratedCarousels.value = true;
        autoplayCuratedCarousels.value = true;
        return true;
    }
    catch {
        return false;
    }
}
function handleStorageChange(event: StorageEvent) {
    if (event.key === null || event.key === SHOW_CURATED_CAROUSELS_KEY || event.key === AUTOPLAY_CURATED_CAROUSELS_KEY) {
        reloadDisplayPreferences();
    }
}
export function useDisplayPreferences() {
    reloadDisplayPreferences();
    onMounted(() => window.addEventListener('storage', handleStorageChange));
    onBeforeUnmount(() => window.removeEventListener('storage', handleStorageChange));
    return {
        showCuratedCarousels: readonly(showCuratedCarousels),
        autoplayCuratedCarousels: readonly(autoplayCuratedCarousels),
        setShowCuratedCarousels,
        setAutoplayCuratedCarousels,
        resetDisplayPreferences,
        reloadDisplayPreferences
    };
}
