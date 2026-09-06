import request from '@/utils/request';
export interface PersonalizedRecommendUser {
    id: string;
    nickname: string;
    avatar?: string;
    introduction?: string;
    fansCount?: number;
    followerCount?: number;
    followingCount?: number;
    isCreator?: boolean;
    isVip?: boolean;
    isFollowing?: boolean;
    recommendReason?: string;
}
export function getPersonalizedRecommendUsers(limit: number = 5) {
    return request<PersonalizedRecommendUser[]>({
        url: '/music-square/recommend-users/personalized',
        method: 'GET',
        params: { limit }
    });
}
export function getPersonalizedTopics(limit: number = 10) {
    return request({
        url: '/music-square/topics/personalized',
        method: 'GET',
        params: { limit }
    });
}
export function getPersonalizedVotedSongs(limit: number = 10) {
    return request({
        url: '/music-square/vote/personalized',
        method: 'GET',
        params: { limit }
    });
}
