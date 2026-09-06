import { ref } from 'vue';
import { request } from '@/utils/request';
import { logger as frontendLogger } from '@/utils/logger';
export interface ExternalContent {
    id?: string | number;
    contentType: 'game' | 'anime' | 'music';
    externalId?: string;
    title: string;
    description?: string;
    thumbnail?: string;
    images?: string[] | string;
    externalUrl?: string;
    category?: string;
    platform?: string;
    releaseDate?: string;
    rating?: number;
    status?: number;
    priority?: number;
    createdBy?: string | number;
    createTime?: string;
    updateTime?: string;
}
export type ExternalContentCreateRequest = Omit<Pick<ExternalContent, 'contentType' | 'externalId' | 'title' | 'description' | 'thumbnail' | 'images' | 'externalUrl' | 'category' | 'platform' | 'releaseDate' | 'rating'>, 'images'> & {
    images?: string[];
};
export interface ExternalContentPage {
    records: ExternalContent[];
    total: number;
    current: number;
    pages: number;
    size: number;
}
export interface ExternalContentCreateResult {
    id: string | number;
    created: boolean;
}
export interface FreeGame {
    id: string | number;
    title: string;
    thumbnail: string;
    short_description: string;
    game_url: string;
    genre: string;
    platform: string;
    release_date: string;
}
export interface AnimeCatalogItem {
    mal_id: number;
    title: string;
    title_english?: string;
    title_japanese?: string;
    images: {
        jpg: {
            image_url: string;
        };
        webp: {
            image_url: string;
        };
    };
    synopsis: string;
    score: number;
    genres: {
        name: string;
    }[];
    episodes: number;
    status: string;
    url: string;
    release_date?: string;
    source?: string;
}
export interface ITunesTrack {
    trackId: string | number;
    trackName: string;
    artistName: string;
    collectionName: string;
    artworkUrl100: string;
    previewUrl?: string;
    trackViewUrl: string;
    releaseDate?: string;
    primaryGenreName?: string;
}
function parseProxyPayload(payload: unknown): unknown {
    if (typeof payload !== 'string') {
        return payload;
    }
    if (!payload) {
        return null;
    }
    try {
        return JSON.parse(payload);
    }
    catch (error) {
        frontendLogger.capture('error', '[External] Failed to parse proxy payload:', error);
        return null;
    }
}
function extractArray<T>(payload: unknown, key?: string): T[] {
    const parsed = parseProxyPayload(payload);
    const source = key && parsed && typeof parsed === 'object'
        ? (parsed as Record<string, unknown>)[key]
        : parsed;
    return Array.isArray(source) ? source as T[] : [];
}
export const FreeGameAPI = {
    async getGames(limit = 24): Promise<FreeGame[]> {
        try {
            const response = await request<string | FreeGame[]>({
                url: '/external/games',
                method: 'GET'
            });
            return extractArray<FreeGame>(response.data).slice(0, limit);
        }
        catch (error) {
            frontendLogger.capture('error', '[External] Failed to load games:', error);
            throw error;
        }
    },
    async getGamesByCategory(category: string, limit = 24): Promise<FreeGame[]> {
        try {
            const response = await request<string | FreeGame[]>({
                url: '/external/games',
                method: 'GET',
                params: { category }
            });
            return extractArray<FreeGame>(response.data).slice(0, limit);
        }
        catch (error) {
            frontendLogger.capture('error', '[External] Failed to load games by category:', error);
            throw error;
        }
    }
};
export const AnimeCatalogAPI = {
    async searchAnime(keyword: string, limit = 24): Promise<AnimeCatalogItem[]> {
        try {
            const response = await request<string | {
                data: AnimeCatalogItem[];
            }>({
                url: '/external/anime',
                method: 'GET',
                params: { q: keyword, limit }
            });
            return extractArray<AnimeCatalogItem>(response.data, 'data').slice(0, Math.max(1, Math.min(limit, 25)));
        }
        catch (error) {
            frontendLogger.capture('error', '[External] Failed to search anime:', error);
            throw error;
        }
    },
    async getSeasonalAnime(limit = 24): Promise<AnimeCatalogItem[]> {
        try {
            const response = await request<string | {
                data: AnimeCatalogItem[];
            }>({
                url: '/external/anime/seasonal/now',
                method: 'GET',
                params: { limit }
            });
            return extractArray<AnimeCatalogItem>(response.data, 'data').slice(0, Math.max(1, Math.min(limit, 25)));
        }
        catch (error) {
            frontendLogger.capture('error', '[External] Failed to load seasonal anime:', error);
            throw error;
        }
    }
};
export const ITunesMusicAPI = {
    async search(keyword: string, limit = 30): Promise<ITunesTrack[]> {
        try {
            const response = await request<string | {
                results: ITunesTrack[];
            }>({
                url: '/external/itunes/search',
                method: 'GET',
                params: {
                    term: keyword,
                    media: 'music',
                    entity: 'song',
                    limit
                }
            });
            return extractArray<ITunesTrack>(response.data, 'results').slice(0, Math.max(1, Math.min(limit, 30)));
        }
        catch (error) {
            frontendLogger.capture('error', '[External] Failed to search music:', error);
            throw error;
        }
    },
    async getTrending(limit = 20): Promise<ITunesTrack[]> {
        return this.search('top songs', limit);
    }
};
export async function searchGames(keyword: string): Promise<FreeGame[]> {
    try {
        const response = await request<string | FreeGame[]>({
            url: '/external-content/search/games',
            method: 'GET',
            params: { keyword }
        });
        return extractArray<FreeGame>(response.data).slice(0, 30);
    }
    catch (error) {
        frontendLogger.capture('error', '[External] Failed to search games:', error);
        throw error;
    }
}
export async function searchAnimeCatalog(keyword: string, limit = 20): Promise<AnimeCatalogItem[]> {
    const safeLimit = Math.max(1, Math.min(limit, 20));
    const response = await request<string | {
        data: AnimeCatalogItem[];
    }>({
        url: '/external/anime',
        method: 'GET',
        params: { q: keyword, limit: safeLimit }
    });
    return extractArray<AnimeCatalogItem>(response.data, 'data').slice(0, safeLimit);
}
export async function searchMusicCatalog(keyword: string, limit = 30): Promise<ITunesTrack[]> {
    const safeLimit = Math.max(1, Math.min(limit, 30));
    const response = await request<string | {
        results: ITunesTrack[];
    }>({
        url: '/external/itunes/search',
        method: 'GET',
        params: {
            term: keyword,
            media: 'music',
            entity: 'song',
            limit: safeLimit
        }
    });
    return extractArray<ITunesTrack>(response.data, 'results').slice(0, safeLimit);
}
export function addExternalContent(content: ExternalContentCreateRequest) {
    return request<ExternalContentCreateResult>({
        url: '/external-content/add',
        method: 'POST',
        data: content
    });
}
export function getContentPage(params: {
    pageNum: number;
    pageSize: number;
    contentType?: string;
    status?: number;
}) {
    return request<ExternalContentPage>({
        url: '/external-content/page',
        method: 'GET',
        params
    });
}
export function updateContentStatus(id: string | number, status: number) {
    return request<void>({
        url: '/external-content/status',
        method: 'PUT',
        params: { id, status }
    });
}
export function deleteContent(id: string | number) {
    return request<void>({
        url: `/external-content/${id}`,
        method: 'DELETE'
    });
}
export function getPublishedGames(limit = 10) {
    return request<ExternalContent[]>({
        url: '/external-content/list/game',
        method: 'GET',
        params: { limit }
    });
}
export function getPublishedAnime(limit = 10) {
    return request<ExternalContent[]>({
        url: '/external-content/list/anime',
        method: 'GET',
        params: { limit }
    });
}
export function getPublishedMusic(limit = 10) {
    return request<ExternalContent[]>({
        url: '/external-content/list/music',
        method: 'GET',
        params: { limit }
    });
}
export function getRecommendContent(limit = 20) {
    return request<ExternalContent[]>({
        url: '/external-content/recommend',
        method: 'GET',
        params: { limit }
    });
}
export function useExternalContentAdmin() {
    const loading = ref(false);
    const searchResults = ref<FreeGame[]>([]);
    const searchGameList = async (keyword: string) => {
        loading.value = true;
        try {
            searchResults.value = await searchGames(keyword);
        }
        finally {
            loading.value = false;
        }
    };
    const addGame = async (game: FreeGame) => {
        const content: ExternalContentCreateRequest = {
            contentType: 'game',
            externalId: String(game.id),
            title: game.title,
            description: game.short_description,
            thumbnail: game.thumbnail,
            externalUrl: game.game_url,
            category: game.genre,
            platform: game.platform,
            releaseDate: game.release_date
        };
        return addExternalContent(content);
    };
    return {
        loading,
        searchResults,
        searchGames: searchGameList,
        addGame
    };
}
export function useRecommendContent() {
    const loading = ref(false);
    const games = ref<ExternalContent[]>([]);
    const anime = ref<ExternalContent[]>([]);
    const all = ref<ExternalContent[]>([]);
    const loadGames = async (limit = 10) => {
        loading.value = true;
        try {
            const response = await getPublishedGames(limit);
            games.value = response.data || [];
        }
        finally {
            loading.value = false;
        }
    };
    const loadAnime = async (limit = 10) => {
        loading.value = true;
        try {
            const response = await getPublishedAnime(limit);
            anime.value = response.data || [];
        }
        finally {
            loading.value = false;
        }
    };
    const loadAll = async (limit = 20) => {
        loading.value = true;
        try {
            const response = await getRecommendContent(limit);
            all.value = response.data || [];
        }
        finally {
            loading.value = false;
        }
    };
    return {
        loading,
        games,
        anime,
        all,
        loadGames,
        loadAnime,
        loadAll
    };
}
