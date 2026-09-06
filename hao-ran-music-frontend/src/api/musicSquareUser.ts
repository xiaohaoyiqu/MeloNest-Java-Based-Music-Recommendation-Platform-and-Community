import { request } from '@/utils/request';
export interface UserPostStats {
    postCount: number;
    likeCount: number;
    commentCount: number;
    shareCount: number;
}
export interface PageResponse<T> {
    records: T[];
    total: number;
    current: number;
    pages: number;
}
export interface PostVO {
    id: string;
    userId: string;
    content: string;
    images: string[];
    createTime: string;
}
export function getUserPosts(userId: string, page: number = 1, size: number = 10) {
    return request<PageResponse<PostVO>>({
        url: `/music-square/posts/user/${userId}`,
        method: 'GET',
        params: { page, size }
    });
}
export function getMyPosts(page: number = 1, size: number = 10) {
    return request<PageResponse<PostVO>>({
        url: '/music-square/posts/my',
        method: 'GET',
        params: { page, size }
    });
}
export function getMyPostsByType(type: string = 'all', page: number = 1, size: number = 10) {
    return request<PageResponse<PostVO>>({
        url: '/music-square/posts/my/type',
        method: 'GET',
        params: { type, page, size }
    });
}
export function getUserPostStats(userId: string) {
    return request<UserPostStats>({
        url: `/music-square/posts/user/${userId}/stats`,
        method: 'GET'
    });
}
export function getMyPostStats() {
    return request<UserPostStats>({
        url: '/music-square/posts/my/stats',
        method: 'GET'
    });
}
export function getPostDetail(id: string) {
    return request<PostVO>({
        url: `/music-square/posts/${id}/detail`,
        method: 'GET'
    });
}
export function batchDeletePosts(postIds: number[]) {
    return request<string>({
        url: '/music-square/posts/batch',
        method: 'DELETE',
        data: postIds
    });
}
export const musicSquareUserApi = {
    getUserPosts,
    getMyPosts,
    getMyPostsByType,
    getUserPostStats,
    getMyPostStats,
    getPostDetail,
    batchDeletePosts
};
