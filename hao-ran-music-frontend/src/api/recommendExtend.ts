import { request } from '@/utils/request';
export function getPersonalizedHotSongs(params: {
    limit?: number;
}) {
    return request({
        url: '/ranking/personal/songs',
        method: 'GET',
        params
    });
}
export function getPersonalizedCreators(params: {
    limit?: number;
}) {
    return request({
        url: '/ranking/personal/creators',
        method: 'GET',
        params
    });
}
export function getPersonalizedPlaylists(params: {
    limit?: number;
}) {
    return request({
        url: '/ranking/personal/playlists',
        method: 'GET',
        params
    });
}
export function getPersonalizedPlaylistIds(params: {
    limit?: number;
}) {
    return request<Array<string | number>>({
        url: '/recommend/playlists',
        method: 'GET',
        params
    });
}
export function getPersonalizedAlbumIds(params: {
    limit?: number;
}) {
    return request<Array<string | number>>({
        url: '/recommend/albums',
        method: 'GET',
        params
    });
}
export function getPersonalizedAlbums(params: {
    limit?: number;
}) {
    return request({
        url: '/recommend/albums',
        method: 'GET',
        params
    });
}
export function getPersonalizedMVs(params: {
    limit?: number;
}) {
    return request({
        url: '/recommend/mvs',
        method: 'GET',
        params
    });
}
export function getRecommendedTopics(params: {
    limit?: number;
}) {
    return request({
        url: '/recommend/topics',
        method: 'GET',
        params
    });
}
export function getTopicsByGenre(params: {
    genre: string;
    limit?: number;
}) {
    return request({
        url: '/recommend/topics/genre',
        method: 'GET',
        params
    });
}
export interface HybridRecommendResponse {
    type: string;
    source?: string;
    sourceName?: string;
    reason?: string;
    modelVersion?: string;
    modelBacked?: boolean;
    fallbackReason?: string;
    songs: HybridSongVO[];
}
export interface RecommendedHybridSongVO extends HybridSongVO {
    reason?: string;
    source?: string;
    sourceName?: string;
    confidence?: number;
    tags?: string[];
}
export interface HybridSongVO {
    id: string | number;
    name: string;
    artistNames: string;
    artistId: string | number;
    albumName: string;
    albumId: string | number;
    duration: number;
    mainType: string;
    cover: string;
    favoriteCount?: number;
    playCount?: number;
    isNew?: number;
    isHot?: number;
    urlStandard?: string;
    urlHigh?: string;
    urlLossless?: string;
    sizeStandard?: number;
    sizeHigh?: number;
    sizeLossless?: number;
    language?: string;
    versionType?: string;
    versionName?: string;
    isFavorite?: boolean;
}
export function getHybridRecommend(params: {
    limit?: number;
}) {
    return request<HybridRecommendResponse>({
        url: '/hybrid/recommend',
        method: 'GET',
        params
    });
}
export function getHybridRecommendWithReason(params: {
    limit?: number;
}) {
    return request<RecommendedHybridSongVO[]>({
        url: '/hybrid/recommend-with-reason',
        method: 'GET',
        params
    });
}
export function getColdStartRecommend(params: {
    limit?: number;
}) {
    return request<HybridRecommendResponse>({
        url: '/hybrid/cold-start',
        method: 'GET',
        params
    });
}
export function getDiscoveryRecommend(params: {
    limit?: number;
}) {
    return request<HybridRecommendResponse>({
        url: '/hybrid/discovery',
        method: 'GET',
        params
    });
}
export function getMoodBasedRecommend(params: {
    mood: string;
    limit?: number;
}) {
    return request<HybridRecommendResponse>({
        url: `/hybrid/mood/${params.mood}`,
        method: 'GET',
        params: {
            limit: params.limit
        }
    });
}
export function getRecommendWeights() {
    return request<Record<string, number>>({
        url: '/hybrid/weights',
        method: 'GET'
    });
}
export function updateRecommendWeights(weights: {
    collaborative?: number;
    content?: number;
    popularity?: number;
    social?: number;
}) {
    return request<void>({
        url: '/hybrid/weights',
        method: 'POST',
        data: weights
    });
}
export function refreshHybridRecommendProfile(userId: string | number) {
    return request<void>({
        url: `/hybrid/refresh-profile/${userId}`,
        method: 'POST'
    });
}
export type MusicGenre = 'Pop' | 'Rock' | 'Folk' | 'Electronic' | 'HipHop' | 'Classical' | 'Jazz' | 'Country';
export interface PersonalizedSongsResponse {
    songs: Array<{
        id: string;
        name: string;
        artistNames: string;
        artistId: string;
        albumName: string;
        cover: string;
        duration: number;
        playCount: number;
        score: number;
    }>;
    total: number;
    preferenceGenres: string[];
}
export interface PersonalizedCreatorsResponse {
    creators: Array<{
        userId: string;
        creatorId: string | null;
        creatorName: string;
        avatar: string;
        fansCount: number;
        description: string | null;
        creatorType: string;
        score: number;
        isFollowing: boolean;
        isOfficial: boolean;
    }>;
    total: number;
}
export interface RecommendTopicResponse {
    id: string;
    name: string;
    description: string | null;
    cover: string | null;
    category: string | null;
    postCount: number;
    followerCount: number;
    isHot: boolean;
    recommendReason: string;
}
export interface UserPortraitResponse {
    userId: string;
    lastUpdateTime: string;
    musicPreference: {
        favoriteGenres: string[];
        favoriteLanguages: string[];
        genreDistribution: Record<string, number>;
        languageDistribution: Record<string, number>;
    };
    interestTags: string[];
    activePeriods: {
        morning: number;
        afternoon: number;
        evening: number;
        night: number;
    };
    behaviorSummary: {
        playCount: number;
        likeCount: number;
        commentCount: number;
        followingCount: number;
        followerCount: number;
    };
}
