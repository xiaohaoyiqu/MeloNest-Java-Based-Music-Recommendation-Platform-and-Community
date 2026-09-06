import { request } from '@/utils/request';
export type VideoPostStatus = 0 | 1 | 2;
export interface VideoPost {
    id: string | number;
    content: string;
    topics?: string;
    thumbnailUrl?: string;
    videoUrl?: string;
    duration?: number;
    status: VideoPostStatus;
    likeCount: number;
    commentCount: number;
    shareCount: number;
    createTime: string;
}
export interface VideoPostStats {
    totalCount: number;
    pendingCount: number;
    publishedCount: number;
    rejectedCount: number;
}
export interface UploadVideoResponse {
    postId: string;
    videoUrl: string;
    thumbnailUrl: string;
    originalSize: number;
    compressedSize: number;
    compressionRatio: number;
}
export function uploadVideo(data: {
    file: File;
    content: string;
    topics?: string;
}) {
    const formData = new FormData();
    formData.append('file', data.file);
    formData.append('content', data.content);
    if (data.topics) {
        formData.append('topics', data.topics);
    }
    return request<UploadVideoResponse>({
        url: '/video-post/upload',
        method: 'POST',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
}
export function getMyPosts(status?: VideoPostStatus | number) {
    return request<VideoPost[]>({
        url: '/video-post/my',
        method: 'GET',
        params: { status }
    });
}
export function getStats() {
    return request<VideoPostStats>({
        url: '/video-post/stats',
        method: 'GET'
    });
}
export function getVideoStatus(postId: string | number) {
    return request<{
        postId: string;
        status: string;
        progress: number;
        videoUrl?: string;
        thumbnailUrl?: string;
    }>({
        url: `/video-post/status/${postId}`,
        method: 'GET'
    });
}
export function deletePost(postId: string | number) {
    return request<boolean>({
        url: `/video-post/${postId}`,
        method: 'DELETE'
    });
}
export function cleanupTempFiles() {
    return request<string>({
        url: '/video-post/cleanup-temp',
        method: 'POST'
    });
}
export const videoPostApi = {
    uploadVideo,
    getMyPosts,
    getStats,
    getVideoStatus,
    deletePost,
    cleanupTempFiles
};
