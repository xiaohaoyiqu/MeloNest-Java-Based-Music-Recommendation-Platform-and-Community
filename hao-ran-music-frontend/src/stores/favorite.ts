import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { getFavoritePlaylist } from '@/api/playlist';
import { favoriteSong, getFavoriteSongs, unfavoriteSong } from '@/api/song';
import { usePlayerStore } from '@/stores/player';
import { useUserStore } from './user';
import type { SongInfo } from '@/types/song';
import { logger as frontendLogger } from '@/utils/logger';
interface FavoriteLikeSong {
    id?: string | number;
    isFavorite?: boolean;
}
interface FavoriteRecord {
    id?: string | number;
    songId?: string | number;
    song?: FavoriteLikeSong;
}
function isAuthError(error: unknown): boolean {
    return Boolean(error
        && typeof error === 'object'
        && ((error as {
            isAuthError?: unknown;
        }).isAuthError === true
            || (error as {
                message?: unknown;
            }).message === 'AUTH_ERROR'));
}
export const useFavoriteStore = defineStore('favorite', () => {
    const favoriteSongIds = ref<Set<string>>(new Set());
    const favoritePlaylistSongs = ref<SongInfo[]>([]);
    const sessionFavoriteStates = ref<Map<string, boolean>>(new Map());
    const pendingFavoriteRequests = new Map<string, Promise<boolean>>();
    const isLoaded = ref(false);
    const isLoading = ref(false);
    let loadPromise: Promise<void> | null = null;
    const normalizeSongId = (songId: string | number): string => String(songId);
    const isFavorite = (songId: string | number): boolean => {
        const normalizedSongId = normalizeSongId(songId);
        if (sessionFavoriteStates.value.has(normalizedSongId)) {
            return sessionFavoriteStates.value.get(normalizedSongId) === true;
        }
        return favoriteSongIds.value.has(normalizedSongId);
    };
    const isSongFavorited = (song?: FavoriteLikeSong | null): boolean => {
        if (!song?.id)
            return false;
        const normalizedSongId = normalizeSongId(song.id);
        if (sessionFavoriteStates.value.has(normalizedSongId)) {
            return sessionFavoriteStates.value.get(normalizedSongId) === true;
        }
        const storeValue = isFavorite(song.id);
        return isLoaded.value ? storeValue : storeValue || song.isFavorite === true;
    };
    const favoriteSongs = computed(() => Array.from(favoriteSongIds.value));
    const favoriteCount = computed(() => favoriteSongIds.value.size);
    const setFavoriteIds = (ids: Set<string>) => {
        const nextIds = new Set(ids);
        sessionFavoriteStates.value.forEach((isFavorited, songId) => {
            if (isFavorited) {
                nextIds.add(songId);
            }
            else {
                nextIds.delete(songId);
            }
        });
        favoriteSongIds.value = nextIds;
    };
    const setSessionFavoriteState = (songId: string | number, isFavorited: boolean) => {
        const normalizedSongId = normalizeSongId(songId);
        const nextStates = new Map(sessionFavoriteStates.value);
        nextStates.set(normalizedSongId, isFavorited);
        sessionFavoriteStates.value = nextStates;
        const nextIds = new Set(favoriteSongIds.value);
        if (isFavorited) {
            nextIds.add(normalizedSongId);
        }
        else {
            nextIds.delete(normalizedSongId);
        }
        favoriteSongIds.value = nextIds;
    };
    const collectSongId = (ids: Set<string>, songId?: string | number | null) => {
        if (songId !== undefined && songId !== null) {
            ids.add(normalizeSongId(songId));
        }
    };
    const loadFavoritePlaylistIds = async (ids: Set<string>): Promise<boolean> => {
        const response = await getFavoritePlaylist();
        if (response.code !== 200) {
            return false;
        }
        favoritePlaylistSongs.value = response.data?.songs || [];
        favoritePlaylistSongs.value.forEach((song: FavoriteLikeSong) => collectSongId(ids, song?.id));
        return true;
    };
    const loadSongLikeFavoriteIds = async (ids: Set<string>): Promise<boolean> => {
        const pageSize = 1000;
        let page = 1;
        let loaded = false;
        while (true) {
            const response = await getFavoriteSongs(page, pageSize);
            if (response.code !== 200 || !response.data) {
                return loaded;
            }
            const records = response.data.records || [];
            records.forEach((record: FavoriteRecord) => {
                collectSongId(ids, record.songId ?? record.song?.id);
            });
            loaded = true;
            const totalPages = response.data.pages || 1;
            if (page >= totalPages || records.length < pageSize) {
                break;
            }
            page += 1;
        }
        return loaded;
    };
    const loadFavorites = (): Promise<void> => {
        if (loadPromise)
            return loadPromise;
        const userStore = useUserStore();
        if (!userStore.isLogin) {
            setFavoriteIds(new Set());
            isLoaded.value = true;
            return Promise.resolve();
        }
        if (isLoaded.value)
            return Promise.resolve();
        const task = (async () => {
            isLoading.value = true;
            try {
                const newIds = new Set<string>();
                let loaded = false;
                try {
                    loaded = await loadFavoritePlaylistIds(newIds) || loaded;
                }
                catch (error) {
                    if (isAuthError(error))
                        return;
                    frontendLogger.capture('warn', '[FavoriteStore] load favorite playlist failed:', error);
                }
                if (!userStore.isLogin)
                    return;
                try {
                    loaded = await loadSongLikeFavoriteIds(newIds) || loaded;
                }
                catch (error) {
                    if (isAuthError(error))
                        return;
                    frontendLogger.capture('warn', '[FavoriteStore] load song_like favorites failed:', error);
                }
                if (loaded && userStore.isLogin) {
                    setFavoriteIds(newIds);
                    isLoaded.value = true;
                }
            }
            catch (error) {
                frontendLogger.capture('error', '[FavoriteStore] loadFavorites failed:', error);
            }
            finally {
                isLoading.value = false;
            }
        })();
        loadPromise = task;
        void task.then(() => {
            if (loadPromise === task)
                loadPromise = null;
        }, () => {
            if (loadPromise === task)
                loadPromise = null;
        });
        return task;
    };
    const addFavorite = (songId: string | number) => {
        setSessionFavoriteState(songId, true);
    };
    const removeFavorite = (songId: string | number) => {
        setSessionFavoriteState(songId, false);
    };
    const addFavorites = (songIds: Array<string | number>) => {
        const nextIds = new Set(favoriteSongIds.value);
        songIds.forEach(songId => nextIds.add(normalizeSongId(songId)));
        setFavoriteIds(nextIds);
    };
    const removeFavorites = (songIds: Array<string | number>) => {
        const nextIds = new Set(favoriteSongIds.value);
        songIds.forEach(songId => nextIds.delete(normalizeSongId(songId)));
        setFavoriteIds(nextIds);
    };
    const clearFavorites = () => {
        sessionFavoriteStates.value = new Map();
        favoritePlaylistSongs.value = [];
        setFavoriteIds(new Set());
        isLoaded.value = false;
    };
    const reset = () => {
        sessionFavoriteStates.value = new Map();
        favoritePlaylistSongs.value = [];
        setFavoriteIds(new Set());
        isLoaded.value = false;
        isLoading.value = false;
    };
    const toggleFavorite = (songId: string | number, isAdd?: boolean): Promise<boolean> => {
        const normalizedSongId = normalizeSongId(songId);
        const pendingRequest = pendingFavoriteRequests.get(normalizedSongId);
        if (pendingRequest)
            return pendingRequest;
        const shouldAdd = isAdd ?? !isFavorite(songId);
        const userStore = useUserStore();
        if (shouldAdd && userStore.isAccountUnavailable) {
            return Promise.reject(new Error(userStore.accountUnavailableMessage || '当前账号无法收藏'));
        }
        const task = (async () => {
            try {
                if (shouldAdd) {
                    await favoriteSong(songId);
                    addFavorite(songId);
                }
                else {
                    await unfavoriteSong(songId);
                    removeFavorite(songId);
                }
                const playerStore = usePlayerStore();
                playerStore.playlist.forEach(item => {
                    if (String(item.song.id) === normalizedSongId)
                        item.song.isFavorite = shouldAdd;
                });
                return shouldAdd;
            }
            catch (error) {
                frontendLogger.capture('error', '[FavoriteStore] toggleFavorite failed:', error);
                throw error;
            }
            finally {
                pendingFavoriteRequests.delete(normalizedSongId);
            }
        })();
        pendingFavoriteRequests.set(normalizedSongId, task);
        return task;
    };
    return {
        favoriteSongIds,
        favoritePlaylistSongs,
        isLoaded,
        isLoading,
        isFavorite,
        isSongFavorited,
        favoriteSongs,
        favoriteCount,
        loadFavorites,
        addFavorite,
        removeFavorite,
        addFavorites,
        removeFavorites,
        clearFavorites,
        reset,
        toggleFavorite
    };
});
