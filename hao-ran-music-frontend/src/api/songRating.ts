import { request } from '@/utils/request';
export interface SongRatingVO {
    songId: string;
    songName: string;
    artistName: string;
    coverUrl: string;
    averageRating: number;
    totalRatings: number;
    ratingDistribution: Record<number, number>;
    userRating?: number;
}
export interface SongRatingDTO {
    songId: string;
    rating: number;
    comment?: string;
}
export interface UserRating {
    id: string;
    userId: string;
    songId: string;
    rating: number;
    comment: string;
    createTime: string;
    songInfo: {
        id: string;
        name: string;
        artistName: string;
        coverUrl: string;
    };
}
export function rateSong(dto: SongRatingDTO) {
    return request<boolean>({
        url: '/song/rating',
        method: 'POST',
        data: dto
    });
}
export function getSongRating(songId: string) {
    return request<SongRatingVO>({
        url: `/song/rating/${songId}`,
        method: 'GET'
    });
}
export function deleteRating(songId: string) {
    return request<void>({
        url: `/song/rating/${songId}`,
        method: 'DELETE'
    });
}
export function getUserRatings() {
    return request<{
        ratings: UserRating[];
        total: number;
    }>({
        url: '/song/rating/user/list',
        method: 'GET'
    });
}
export const songRatingApi = {
    rateSong,
    getSongRating,
    deleteRating,
    getUserRatings
};
