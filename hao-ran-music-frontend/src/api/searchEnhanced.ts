import { request } from '@/utils/request';
export interface SearchSuggestVO {
    songs?: SimpleSongVO[];
    artists?: SimpleArtistVO[];
    albums?: SimpleAlbumVO[];
    playlists?: SimplePlaylistVO[];
    keywords?: string[];
}
export interface SimpleSongVO {
    id: string | number;
    name: string;
    artistNames: string;
    cover: string;
}
export interface SimpleArtistVO {
    id: string | number;
    name: string;
    avatar: string;
}
export interface SimpleAlbumVO {
    id: string | number;
    name: string;
    cover: string;
    artistName: string;
}
export interface SimplePlaylistVO {
    id: string | number;
    name: string;
    cover: string;
    songCount: number;
}
export interface HotSearchVO {
    keyword: string;
    heat: string | number;
    trend: 'up' | 'down' | 'equal';
    rank: number;
}
export function getSuggest(keyword: string, limit = 10) {
    return request<SearchSuggestVO>({
        url: '/search/enhance/suggest',
        method: 'GET',
        params: { keyword, limit }
    });
}
export function getHotSearch(limit = 10) {
    return request<HotSearchVO[]>({
        url: '/search/enhance/hot',
        method: 'GET',
        params: { limit }
    });
}
export function saveSearchHistory(keyword: string) {
    return request<void>({
        url: '/search/enhance/history',
        method: 'POST',
        params: { keyword }
    });
}
export function getSearchHistory() {
    return request<string[]>({
        url: '/search/enhance/history',
        method: 'GET'
    });
}
export function clearSearchHistory() {
    return request<void>({
        url: '/search/enhance/history',
        method: 'DELETE'
    });
}
export const searchEnhancedApi = {
    getSuggest,
    getHotSearch,
    saveSearchHistory,
    getSearchHistory,
    clearSearchHistory
};
