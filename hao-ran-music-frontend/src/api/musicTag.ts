import { request } from '@/utils/request';
export interface MusicTagVO {
    id: string;
    name: string;
    category: string;
    categoryName?: string;
    icon?: string;
    color?: string;
    description?: string;
    useCount?: number;
    sortOrder?: number;
    isHot?: boolean;
}
export interface SongPageResult {
    records: {
        id: string;
        name: string;
        artistNames: string;
        albumName: string;
        cover: string;
        duration: number;
    }[];
    total: number;
    current: number;
    size: number;
    pages: number;
}
export function getMusicTagList(category?: string) {
    return request<MusicTagVO[]>({
        url: '/music-tag/list',
        method: 'GET'
    });
}
export function getHotMusicTags(limit = 10) {
    return request<MusicTagVO[]>({
        url: '/music-tag/hot',
        method: 'GET',
        params: { limit }
    });
}
export function searchSongsByTags(tagIds: Array<string | number>, page = 1, size = 20) {
    return request<SongPageResult>({
        url: '/music-tag/songs',
        method: 'GET',
        params: { tagIds: Array.isArray(tagIds) ? tagIds.join(',') : tagIds, page, size }
    });
}
export function getSongTags(songId: string) {
    return request<MusicTagVO[]>({
        url: `/music-tag/song/${songId}`,
        method: 'GET'
    });
}
export function addSongTag(songId: string, tagId: string) {
    return request<void>({
        url: `/music-tag/song/${songId}`,
        method: 'POST',
        params: { tagId }
    });
}
export function removeSongTag(songId: string, tagId: string) {
    return request<void>({
        url: `/music-tag/song/${songId}/tag/${tagId}`,
        method: 'DELETE'
    });
}
export function getUserTagPreference() {
    return request<MusicTagVO[]>({
        url: '/music-tag/user/preference',
        method: 'GET'
    });
}
export function recommendByTags(limit = 10) {
    return request<any[]>({
        url: '/music-tag/recommend',
        method: 'GET',
        params: { limit }
    });
}
export const musicTagApi = {
    getMusicTagList,
    getHotMusicTags,
    searchSongsByTags,
    getSongTags,
    addSongTag,
    removeSongTag,
    getUserTagPreference,
    recommendByTags
};
