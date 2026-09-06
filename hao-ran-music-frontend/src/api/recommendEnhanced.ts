import { request } from '@/utils/request';
export interface RecommendedSongVO {
    id: string;
    name: string;
    artistNames: string;
    artistId: string;
    albumName: string;
    albumId: string;
    cover: string;
    duration: number;
    urlStandard?: string;
    urlHigh?: string;
    urlLossless?: string;
    reason?: string;
    source?: string;
    sourceName?: string;
    confidence?: number;
    relatedSong?: {
        id: string;
        name: string;
        cover: string;
        artistName: string;
    };
    tags?: string[];
    score?: number;
    isNew?: number;
    isHot?: number;
    isFavorite?: boolean;
}
export function getPersonalRecommendWithReason(limit = 10) {
    return request<RecommendedSongVO[]>({
        url: '/recommend/personal/reason',
        method: 'GET',
        params: { limit }
    });
}
export function getDailyDiscoveryWithReason(limit = 10) {
    return request<RecommendedSongVO[]>({
        url: '/recommend/discovery/reason',
        method: 'GET',
        params: { limit }
    });
}
export function getSimilarRecommendWithReason(songId: string | number, limit = 10) {
    return request<RecommendedSongVO[]>({
        url: `/recommend/similar/${songId}/reason`,
        method: 'GET',
        params: { limit }
    });
}
export const recommendEnhancedApi = {
    getPersonalRecommendWithReason,
    getDailyDiscoveryWithReason,
    getSimilarRecommendWithReason
};
