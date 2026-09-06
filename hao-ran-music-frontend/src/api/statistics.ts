import { request } from '@/utils/request';
export interface AuditOverview {
    totalAudits: number;
    pendingAudits: number;
    approvedAudits: number;
    rejectedAudits: number;
    avgProcessTime: number;
    todayAudits: number;
}
export interface ModeratorWorkStats {
    moderatorId: string;
    moderatorName: string;
    totalAudits: number;
    approvedCount: number;
    rejectedCount: number;
    avgProcessTime: number;
}
export interface AuditTypeStats {
    byType: Record<string, number>;
    byStatus: Record<string, number>;
    totalTypes: number;
}
export interface RealtimeAuditStatus {
    pendingCount: number;
    processingCount: number;
    completedToday: number;
    avgProcessTime: number;
}
export interface AuditTrendPoint {
    date: string;
    totalCount: number;
    approvedCount: number;
    rejectedCount: number;
    avgProcessTime: number;
}
export interface PlatformOverview {
    totalUsers: number;
    publicUsers: number;
    restrictedUsers: number;
    todayNewUsers: number;
    totalSongs: number;
    totalMvs: number;
    totalPlays: number;
    todayPlays: number;
    totalComments: number;
    totalFavorites: number;
    vipUsers: number;
    todayRevenue: number;
}
export interface ContentStatistics {
    songCount: number;
    albumCount: number;
    mvCount: number;
    playlistCount: number;
    creatorCount: number;
}
export interface InteractionStatistics {
    todayComments: number;
    todayLikes: number;
    todayFavorites: number;
    todayShares: number;
}
export interface HotSong {
    id: string | number;
    name: string;
    artistName: string;
    cover: string;
    playCount: number;
    rank: number;
}
export interface HotCreator {
    id: string | number;
    nickname: string;
    avatar: string;
    fansCount: number;
    workCount: number;
    totalPlays: number;
    rank: number;
}
export interface UserGrowthPoint {
    date: string;
    count: number;
    growthRate: number;
}
export function getAuditOverview(startTime?: string, endTime?: string, moderatorId?: string | number) {
    return request<AuditOverview>({
        url: '/statistics/audit/overview',
        method: 'GET',
        params: { startTime, endTime, moderatorId }
    });
}
export function getModeratorRanking(startTime?: string, endTime?: string, moderatorId?: string | number) {
    return request<ModeratorWorkStats[]>({
        url: '/statistics/audit/moderator-ranking',
        method: 'GET',
        params: { startTime, endTime, moderatorId }
    });
}
export function getAuditTypeStats(startTime?: string, endTime?: string, moderatorId?: string | number) {
    return request<AuditTypeStats>({
        url: '/statistics/audit/type-stats',
        method: 'GET',
        params: { startTime, endTime, moderatorId }
    });
}
export function getAuditTrend(days = 30, moderatorId?: string | number) {
    return request<AuditTrendPoint[]>({
        url: '/statistics/audit/trend',
        method: 'GET',
        params: { days, moderatorId }
    });
}
export function getRealtimeStatus(moderatorId?: string | number) {
    return request<RealtimeAuditStatus>({
        url: '/statistics/audit/realtime',
        method: 'GET',
        params: { moderatorId }
    });
}
export function getPlatformOverview() {
    return request<PlatformOverview>({
        url: '/statistics/platform/overview',
        method: 'GET'
    });
}
export function getUserGrowthTrend(startDate?: string, endDate?: string) {
    return request<UserGrowthPoint[]>({
        url: '/statistics/user/trend',
        method: 'GET',
        params: { startDate, endDate, interval: 'day' }
    });
}
export function getContentOverview() {
    return request<ContentStatistics>({
        url: '/statistics/content/overview',
        method: 'GET'
    });
}
export function getInteractionToday() {
    return request<InteractionStatistics>({
        url: '/statistics/interaction/today',
        method: 'GET'
    });
}
export function getHotSongs(typeOrLimit?: string | number, limit = 10, userId?: string | number) {
    const params: Record<string, string | number> = {};
    if (typeof typeOrLimit === 'number') {
        params.limit = typeOrLimit;
    }
    else {
        if (typeOrLimit)
            params.type = typeOrLimit;
        params.limit = limit;
    }
    if (userId !== undefined)
        params.userId = userId;
    return request<any[]>({
        url: '/statistics/content/hot-songs',
        method: 'GET',
        params
    });
}
export function getHotCreators(limit = 10) {
    return request<HotCreator[]>({
        url: '/statistics/content/hot-creators',
        method: 'GET',
        params: { limit }
    });
}
export const statisticsApi = {
    getAuditOverview,
    getModeratorRanking,
    getAuditTypeStats,
    getAuditTrend,
    getRealtimeStatus,
    getPlatformOverview,
    getUserGrowthTrend,
    getContentOverview,
    getInteractionToday,
    getHotSongs,
    getHotCreators
};
