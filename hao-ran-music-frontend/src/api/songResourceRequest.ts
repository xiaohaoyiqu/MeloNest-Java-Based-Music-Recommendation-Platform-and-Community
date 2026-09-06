import { request } from '@/utils/request';
import { createUploadProgressHandler, reportUploadProgress, type UploadProgressHandler } from '@/utils/uploadProgress';
export interface SongResourceRequestVO {
    id: string;
    userId: string;
    userNickname?: string;
    songName: string;
    artistName: string;
    albumName?: string;
    versionInfo?: string;
    fileUrl?: string;
    detectedQuality?: number;
    qualityName?: string;
    fileSize?: string | number;
    duration?: number;
    bitrate?: number;
    sampleRate?: number;
    format?: string;
    sourceDescription?: string;
    remark?: string;
    status: 'pending' | 'processing' | 'completed' | 'rejected';
    statusDesc?: string;
    handlerId?: string | number;
    handlerName?: string;
    handleTime?: string;
    handleResult?: string;
    matchedSongId?: string | number;
    matchedSongName?: string;
    autoSongId?: string | number;
    autoSongName?: string;
    notified: boolean | null;
    createTime: string;
}
export function createRequest(data: {
    songName: string;
    artistName: string;
    albumName?: string;
    versionInfo?: string;
    sourceDescription?: string;
    remark?: string;
}) {
    return request<string | number>({
        url: '/song/resource-request',
        method: 'POST',
        data
    });
}
export function uploadFileAndCreateRequest(data: FormData, onProgress?: UploadProgressHandler) {
    const onUploadProgress = createUploadProgressHandler(onProgress);
    return request<string | number>({
        url: '/song/resource-request/upload',
        method: 'POST',
        data,
        headers: {
            'Content-Type': 'multipart/form-data'
        },
        timeout: 600000,
        ...(onUploadProgress ? { onUploadProgress } : {})
    }).then(response => {
        reportUploadProgress(onProgress, 100);
        return response;
    });
}
export function checkRequested(songName: string, artistName: string) {
    return request<boolean>({
        url: '/song/resource-request/check',
        method: 'GET',
        params: { songName, artistName }
    });
}
export function getTodayCount() {
    return request<number>({
        url: '/song/resource-request/today-count',
        method: 'GET'
    });
}
export function getMyRequests(page: number = 1, size: number = 15, status?: SongResourceRequestVO['status']) {
    return request<{
        records: SongResourceRequestVO[];
        total: number;
        current: number;
        size: number;
        pages: number;
    }>({
        url: '/song/resource-request/my',
        method: 'GET',
        params: { page, size, status }
    });
}
export function getAllRequests(page: number = 1, size: number = 15, status?: string) {
    return request<{
        records: SongResourceRequestVO[];
        total: number;
        current: number;
        size: number;
        pages: number;
    }>({
        url: '/song/resource-request/all',
        method: 'GET',
        params: { page, size, status }
    });
}
export function handleRequest(data: {
    id: string;
    matchedSongId?: string | number;
    status: 'completed' | 'rejected';
    handleResult?: string;
}) {
    return request<string>({
        url: '/song/resource-request/handle',
        method: 'POST',
        data
    });
}
export function getPendingCount() {
    return request<number>({
        url: '/song/resource-request/pending-count',
        method: 'GET'
    });
}
export const songResourceRequestApi = {
    createRequest,
    uploadFileAndCreateRequest,
    checkRequested,
    getTodayCount,
    getMyRequests,
    getAllRequests,
    handleRequest,
    getPendingCount
};
