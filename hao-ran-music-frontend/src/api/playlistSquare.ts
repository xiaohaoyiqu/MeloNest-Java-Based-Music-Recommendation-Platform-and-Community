import { request } from '@/utils/request';
export interface PlaylistVO {
    id: string;
    title: string;
    description: string;
    coverUrl: string;
    creatorId: string;
    creatorName: string;
    creatorAvatar: string;
    songCount: number;
    playCount: number;
    likeCount: number;
    isPublic: boolean;
    tags: string[];
    createTime: string;
    updateTime: string;
}
export interface SquareOverview {
    featured: PlaylistVO[];
    hot: PlaylistVO[];
    latest: PlaylistVO[];
    categories: string[];
}
export function getFeaturedPlaylists(limit = 10) {
    return request<PlaylistVO[]>({
        url: '/playlist-square/featured',
        method: 'GET',
        params: { limit }
    });
}
export function getHotPlaylists(limit = 20) {
    return request<PlaylistVO[]>({
        url: '/playlist-square/hot',
        method: 'GET',
        params: { limit }
    });
}
export function getPlaylistsByCategory(category: string, limit = 20) {
    return request<PlaylistVO[]>({
        url: `/playlist-square/category/${category}`,
        method: 'GET',
        params: { limit }
    });
}
export function getPlaylistCategories() {
    return request<string[]>({
        url: '/playlist-square/categories',
        method: 'GET'
    });
}
export function searchPlaylists(keyword: string, page = 1, size = 20) {
    return request<{
        records: PlaylistVO[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/playlist-square/search',
        method: 'GET',
        params: { keyword, page, size }
    });
}
export function getSquareOverview() {
    return request<SquareOverview>({
        url: '/playlist-square/overview',
        method: 'GET'
    });
}
export function getUserCreatedPlaylists(limit = 20) {
    return request<PlaylistVO[]>({
        url: '/playlist-square/user-created',
        method: 'GET',
        params: { limit }
    });
}
export const playlistSquareApi = {
    getFeaturedPlaylists,
    getHotPlaylists,
    getPlaylistsByCategory,
    getPlaylistCategories,
    searchPlaylists,
    getSquareOverview,
    getUserCreatedPlaylists
};
