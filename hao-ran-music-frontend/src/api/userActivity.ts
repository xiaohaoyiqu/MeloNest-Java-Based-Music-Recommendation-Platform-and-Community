import { request } from '@/utils/request';
export interface UserActivityVO {
    id: string;
    activityType: string;
    activityTypeName: string;
    targetType: string;
    targetTypeName: string;
    targetId: string;
    target?: Record<string, any>;
    content?: string;
    createdTime: string;
    timeDescription?: string;
    relatedUserId?: string | number;
    relatedUserName?: string;
    relatedUserAvatar?: string;
}
export interface UserActivityStatsVO {
    postCount: number;
    commentCount: number;
    likeCount: number;
    favoriteCount: number;
    shareCount: number;
}
export interface PageResult<T> {
    records: T[];
    total: number;
    size: number;
    current: number;
    pages: number;
}
export function getUserActivities(params: {
    userId: string;
    type?: 'all' | 'post' | 'comment' | 'like' | 'favorite' | 'share';
    page?: number;
    size?: number;
}) {
    return request<PageResult<UserActivityVO>>({
        url: `/user/${params.userId}/activities`,
        method: 'GET',
        params: {
            type: params.type || 'all',
            page: params.page || 1,
            size: params.size || 20
        }
    });
}
export function getUserActivityStats(userId: string) {
    return request<UserActivityStatsVO>({
        url: `/user/${userId}/activities/stats`,
        method: 'GET'
    });
}
export const userActivityApi = {
    getUserActivities,
    getUserActivityStats
};
