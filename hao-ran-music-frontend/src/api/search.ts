import { request } from '@/utils/request';
export interface SongSimple {
    id: string | number;
    name: string;
    artistNames?: string;
    artist?: string;
    album?: string;
    albumName?: string;
    duration: number;
    mainType?: string;
    cover?: string;
    url?: string;
    isFavorite?: boolean;
    urlStandard?: string;
    urlHigh?: string;
    urlLossless?: string;
    versionType?: string | number;
    versionName?: string;
    language?: string;
}
export interface SearchResultVO {
    songs?: SongSimple[];
    albums?: AlbumSimple[];
    artists?: ArtistSimple[];
    playlists?: PlaylistSimple[];
    mvs?: MVSimple[];
    users?: UserSimple[];
    keyword: string;
    page?: number;
    size?: number;
    total?: number;
    pages?: number;
    timestamp?: number;
}
export type SongSearchField = 'all' | 'title' | 'artist' | 'album';
export interface AlbumSimple {
    id: string | number;
    name: string;
    cover?: string;
    artistNames?: string;
    artistName?: string;
    artistId?: string | number;
    songCount?: number;
    publishDate?: string;
    releaseDate?: string;
    language?: string;
}
export interface ArtistSimple {
    id: string | number;
    name: string;
    avatar: string;
    description?: string;
    songCount: number;
    albumCount: number;
    followerCount: number;
}
export interface PlaylistSimple {
    id: string | number;
    name: string;
    cover?: string;
    description?: string;
    songCount: number;
    playCount: number;
    favoriteCount: number;
    creatorName: string;
    creatorId: string | number;
}
export interface MVSimple {
    id: string | number;
    name: string;
    cover?: string;
    artistNames?: string;
    artistName?: string;
    artistId?: string | number;
    duration: number;
    playCount?: string | number;
    publishDate?: string;
    publishTime?: string;
}
export interface UserSimple {
    id: string | number;
    username: string;
    nickname: string;
    avatar: string;
    signature?: string;
    followerCount: number;
    followingCount?: number;
    isCreator?: boolean;
    isVip?: boolean;
}
export function search(keyword: string) {
    return request<SearchResultVO>({
        url: '/search',
        method: 'GET',
        params: { keyword }
    });
}
export function searchSongs(keyword: string, page: number = 1, size: number = 20, options?: {
    signal?: AbortSignal;
    field?: SongSearchField;
}) {
    return request<SearchResultVO>({
        url: '/search/songs',
        method: 'GET',
        params: { keyword, page, size, field: options?.field || 'all' },
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export function searchAlbums(keyword: string, page: number = 1, size: number = 20, options?: {
    signal?: AbortSignal;
}) {
    return request<SearchResultVO>({
        url: '/search/albums',
        method: 'GET',
        params: { keyword, page, size },
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export function searchArtists(keyword: string, page: number = 1, size: number = 20, options?: {
    signal?: AbortSignal;
}) {
    return request<SearchResultVO>({
        url: '/search/artists',
        method: 'GET',
        params: { keyword, page, size },
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export function searchPlaylists(keyword: string, page: number = 1, size: number = 20, options?: {
    signal?: AbortSignal;
}) {
    return request<SearchResultVO>({
        url: '/search/playlists',
        method: 'GET',
        params: { keyword, page, size },
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export function searchMvs(keyword: string, page: number = 1, size: number = 20, options?: {
    signal?: AbortSignal;
}) {
    return request<SearchResultVO>({
        url: '/search/mvs',
        method: 'GET',
        params: { keyword, page, size },
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export function searchUsers(keyword: string, page: number = 1, size: number = 20, options?: {
    signal?: AbortSignal;
}) {
    return request<SearchResultVO>({
        url: '/search/users',
        method: 'GET',
        params: { keyword, page, size },
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export interface HotSearchItem {
    keyword: string;
    heat?: string | number | null;
    rank: number;
    trend: 'up' | 'down' | 'equal' | 'new';
}
export function getHotSearch(limit: number = 10) {
    return request<HotSearchItem[]>({
        url: '/search/enhance/hot',
        method: 'GET',
        params: { limit }
    });
}
export function getHotKeywords(limit: number = 10) {
    return request<string[]>({
        url: '/search/hot-keywords',
        method: 'GET',
        params: { limit }
    });
}
export function getSearchHistory(limit: number = 10) {
    return request<string[]>({
        url: '/search/history',
        method: 'GET',
        params: { limit }
    });
}
export function clearSearchHistory() {
    return request<void>({
        url: '/search/history',
        method: 'DELETE',
        params: {}
    });
}
export function getSearchSuggestions(keyword: string, limit: number = 10) {
    return request<string[]>({
        url: '/search/suggest',
        method: 'GET',
        params: { keyword, limit }
    });
}
export function correctSearch(input: string) {
    return request<string>({
        url: '/search/correct',
        method: 'GET',
        params: { input }
    });
}
export type HotSearchVO = HotSearchItem;
export interface SearchSuggestVO {
    keywords?: string[];
    songs?: SongSimple[];
    artists?: ArtistSimple[];
    albums?: AlbumSimple[];
}
export function getSuggest(keyword: string, limit: number = 10): Promise<SearchSuggestVO> {
    return getSearchSuggestions(keyword, limit).then(response => ({
        keywords: response.data || []
    }));
}
