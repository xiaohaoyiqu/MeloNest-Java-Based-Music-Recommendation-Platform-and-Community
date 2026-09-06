import { request } from '@/utils/request';
export function submitLyricRequest(data: {
    songId: string;
    songName?: string;
    originalLyric: string;
    correctedLyric: string;
    changeType?: number;
    changeDescription?: string;
    correctionType?: string;
    description?: string;
}) {
    const payload = {
        ...data,
        changeDescription: data.changeDescription ?? data.description,
        changeType: data.changeType
    };
    return request<number>({
        url: '/lyric-request/submit',
        method: 'POST',
        data: payload
    });
}
export function canSubmitLyricRequest(songId: string) {
    return request<boolean>({
        url: `/lyric-request/can-submit/${songId}`,
        method: 'GET'
    });
}
export function getLyricRequests(params: {
    current?: number;
    size?: number;
    status?: number;
}) {
    return request<any>({
        url: '/lyric-request/page',
        method: 'GET',
        params
    });
}
export function getSongLyricRequests(songId: string) {
    return request<any[]>({
        url: `/lyric-request/song/${songId}`,
        method: 'GET'
    });
}
export function reviewLyricRequest(requestId: string, data: {
    status: number;
    reviewReason?: string;
}) {
    return request<boolean>({
        url: `/lyric-request/review/${requestId}`,
        method: 'POST',
        params: data
    });
}
export function applyLyricRequest(requestId: string) {
    return request<boolean>({
        url: `/lyric-request/apply/${requestId}`,
        method: 'POST'
    });
}
export interface LyricRequest {
    id: string;
    songId: string;
    userId: string;
    songName?: string;
    originalLyric: string;
    correctedLyric: string;
    changeType: number;
    changeDescription: string;
    status: number;
    reviewerId?: string | number;
    reviewReason?: string;
    createTime: string;
    updateTime: string;
}
