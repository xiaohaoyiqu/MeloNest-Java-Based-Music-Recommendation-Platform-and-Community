import { request } from '@/utils/request';
export interface CommentInfo {
    id: string;
    content: string;
    targetType: number;
    targetId: string;
    userId: string;
    username: string;
    userAvatar: string;
    userRole?: string;
    isVip?: boolean;
    isOfficial?: boolean;
    editCount?: number;
    parentId: string;
    replyToUserId?: string;
    replyToUsername?: string;
    likeCount: number;
    replyCount: number;
    isLiked?: boolean;
    createTime: string;
    updateTime?: string;
    replies?: CommentInfo[];
}
export interface CreateCommentRequest {
    targetType: number;
    targetId: string;
    content: string;
    parentId?: string;
    replyToUserId?: string;
}
export interface EditCommentRequest {
    id: string;
    content: string;
}
export interface CommentPageResponse {
    records: CommentInfo[];
    total: number;
    size: number;
    current: number;
    pages: number;
}
export function getComments(targetType: number, targetId: string, page: number = 1, size: number = 20) {
    return request<CommentPageResponse>({
        url: '/comment/page',
        method: 'GET',
        params: { targetType, targetId, pageNum: page, pageSize: size }
    });
}
export function getCommentById(id: string) {
    return request<CommentInfo>({
        url: `/comment/info/${id}`,
        method: 'GET'
    });
}
export function createComment(data: CreateCommentRequest) {
    return request<string>({
        url: '/comment',
        method: 'POST',
        data
    });
}
export function deleteComment(id: string) {
    return request<void>({
        url: `/comment/${id}`,
        method: 'DELETE'
    });
}
export function likeComment(commentId: string) {
    return request<void>({
        url: `/comment/${commentId}/like`,
        method: 'POST'
    });
}
export function unlikeComment(commentId: string) {
    return request<void>({
        url: `/comment/${commentId}/like`,
        method: 'DELETE'
    });
}
export function getCommentReplies(commentId: string, page: number = 1, size: number = 20) {
    return request<CommentPageResponse>({
        url: `/comment/${commentId}/replies`,
        method: 'GET',
        params: { pageNum: page, pageSize: size }
    });
}
export function getHotComments(targetType: number, limit: number = 20) {
    return request<CommentInfo[]>({
        url: '/comment/hot',
        method: 'GET',
        params: { targetType, limit }
    });
}
export function getUserComments(page: number = 1, size: number = 20) {
    return request<CommentPageResponse>({
        url: '/comment/my',
        method: 'GET',
        params: { pageNum: page, pageSize: size }
    });
}
export function editComment(data: EditCommentRequest) {
    return request<void>({
        url: '/comment/edit',
        method: 'PUT',
        data
    });
}
export function getCommentEditHistory(commentId: string) {
    return request<Array<{
        id: string;
        content: string;
        createTime: string;
    }>>({
        url: `/comment/${commentId}/history`,
        method: 'GET'
    });
}
export function reportComment(commentId: string, reason: string, description?: string) {
    return request<void>({
        url: '/comment/report',
        method: 'POST',
        data: { commentId, reason, description }
    });
}
export function getQualityComments(targetType: number, targetId: string, limit: number = 20) {
    return request<CommentInfo[]>({
        url: `/comment/quality/${targetType}/${targetId}`,
        method: 'GET',
        params: { limit }
    });
}
export function getFriendComments(targetType: number, targetId: string, limit: number = 10) {
    return request<CommentInfo[]>({
        url: `/comment/friends/${targetType}/${targetId}`,
        method: 'GET',
        params: { limit }
    });
}
export function getCommentRecommendedUsers(targetType: number, targetId: string, limit: number = 10) {
    return request<Array<{
        id: string;
        nickname: string;
        avatar: string;
        role?: string;
        creditScore?: number;
        fansCount?: number;
    }>>({
        url: `/comment/users/${targetType}/${targetId}`,
        method: 'GET',
        params: { limit }
    });
}
export const commentApi = {
    getComments,
    getCommentById,
    createComment,
    deleteComment,
    likeComment,
    unlikeComment,
    getCommentReplies,
    getHotComments,
    getUserComments,
    editComment,
    getCommentEditHistory,
    reportComment,
    getQualityComments,
    getFriendComments,
    getCommentRecommendedUsers
};
