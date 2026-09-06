import { request } from '@/utils/request';
export interface RecommendVO {
    songs: RecommendSong[];
    type: string;
    title: string;
    timestamp: number;
}
export interface RecommendSong {
    id: string;
    name: string;
    artistNames: string;
    artistId: string;
    albumName: string;
    albumId: string;
    cover: string;
    duration: number;
    urlStandard: string;
    urlHigh: string;
    reason?: string;
    isNew?: boolean;
    isHot?: boolean;
    score?: number;
}
export function getDailyRecommend() {
    return request<RecommendVO>({
        url: '/recommend/daily',
        method: 'GET'
    });
}
export function getPersonalRecommend(limit: number = 20) {
    return request<RecommendVO>({
        url: '/recommend/personal',
        method: 'GET',
        params: { limit }
    });
}
export function getDiscoverRecommend(limit: number = 20) {
    return request<RecommendVO>({
        url: '/recommend/discover',
        method: 'GET',
        params: { limit }
    });
}
export function getSimilarSongs(songId: string | number, limit: number = 10) {
    return request<RecommendVO>({
        url: `/recommend/song/${songId}/similar`,
        method: 'GET',
        params: { limit }
    });
}
export function getArtistRecommend(artistId: string | number, limit: number = 10) {
    return request<RecommendVO>({
        url: `/recommend/artist/${artistId}`,
        method: 'GET',
        params: { limit }
    });
}
export function refreshRecommendProfile() {
    return request<void>({
        url: '/recommend/refresh',
        method: 'POST'
    });
}
export function getUserPreferenceTags() {
    return request<string[]>({
        url: '/recommend/preferences/tags',
        method: 'GET'
    });
}
export function recordUserAction(actionType: 'play' | 'like' | 'collect' | 'share', targetId: string | number, targetType: number = 1) {
    return request<void>({
        url: '/recommend/action',
        method: 'POST',
        params: { actionType, targetId, targetType }
    });
}
