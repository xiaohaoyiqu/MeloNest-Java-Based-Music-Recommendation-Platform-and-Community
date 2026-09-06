import { request } from '@/utils/request';
export function cleanupVideoCache() {
    return request({
        url: '/videos/cache/cleanup',
        method: 'POST'
    });
}
export function getVideoTranscodeStatus(postId: string) {
    return request({
        url: `/video-post/status/${postId}`,
        method: 'GET'
    });
}
