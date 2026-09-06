import { request } from '@/utils/request';
import type { MarketplaceItemVO } from '@/api/musicSquare';
import type { StoreProduct } from '@/api/storeProduct';
export interface PublicCollaborativePlaylist {
    id: string;
    name: string;
    description?: string;
    cover?: string;
    songCount?: number;
    playCount?: number;
}
export interface UserPublicShowcase {
    available: boolean;
    collaborativePlaylists: PublicCollaborativePlaylist[];
    marketplaceItems: MarketplaceItemVO[];
    storeProducts: StoreProduct[];
}
export interface UserProfileVO {
    userId: string;
    userName: string;
    userAvatar: string;
    preferredGenres: string[];
    preferredLanguages: string[];
    preferredMoods: string[];
    totalListens: number;
    totalLikes: number;
    totalComments: number;
    totalShares: number;
    activeHours: number[];
    activeDays: number[];
    avgDailyListenTime: number;
    userSegment: string;
    loyaltyLevel: string;
    churnRisk: number;
    lastUpdateTime: string;
}
export interface UserPreferenceTags {
    genres: Array<{
        name: string;
        score: number;
    }>;
    languages: Array<{
        name: string;
        score: number;
    }>;
    moods: Array<{
        name: string;
        score: number;
    }>;
    artists: Array<{
        id: string;
        name: string;
        score: number;
    }>;
    overallTags: string[];
}
export interface SegmentStats {
    totalUsers: number;
    publicUsers: number;
    restrictedUsers: number;
    segmentStats: Record<string, number>;
}
export function getUserProfile(userId: string) {
    return request<UserProfileVO>({
        url: `/user/profile/${userId}`,
        method: 'GET'
    });
}
export function getUserPreferenceTags(userId: string) {
    return request<UserPreferenceTags>({
        url: `/user/profile/preferences/${userId}`,
        method: 'GET'
    });
}
export function updatePreferences(preferredGenres: string[], preferredLanguages: string[], preferredMoods: string[]) {
    return request<void>({
        url: '/user/profile/preferences/update',
        method: 'POST',
        data: { preferredGenres, preferredLanguages, preferredMoods }
    });
}
export function refreshProfile(userId: string) {
    return request<void>({
        url: `/user/profile/refresh/${userId}`,
        method: 'POST'
    });
}
export function batchRefresh() {
    return request<string>({
        url: '/user/profile/refresh/batch',
        method: 'POST'
    });
}
export function recordBehavior(action: string, targetId?: string | number, metadata?: string) {
    return request<void>({
        url: '/user/profile/behavior/record',
        method: 'POST',
        data: { action, targetId, metadata }
    });
}
export function getSegmentStats() {
    return request<SegmentStats>({
        url: '/user/profile/segment/stats',
        method: 'GET'
    });
}
export function predictChurn(userId: string) {
    return request<number>({
        url: `/user/profile/churn/predict/${userId}`,
        method: 'GET'
    });
}
export function getPublicShowcase(userId: string | number) {
    return request<UserPublicShowcase>({
        url: `/user/profile/public-showcase/${userId}`,
        method: 'GET'
    });
}
export const userProfileApi = {
    getUserProfile,
    getUserPreferenceTags,
    updatePreferences,
    refreshProfile,
    batchRefresh,
    recordBehavior,
    getSegmentStats,
    predictChurn,
    getPublicShowcase
};
