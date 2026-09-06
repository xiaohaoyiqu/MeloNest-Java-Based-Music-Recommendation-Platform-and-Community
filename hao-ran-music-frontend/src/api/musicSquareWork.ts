import { request } from '@/utils/request';
export type WorkType = 1 | 2 | 3 | 4;
export type WorkStatus = 0 | 1 | 2;
export type UploadType = 1 | 2 | 3;
export type AudioQuality = 1 | 2 | 3 | 4 | 5;
export interface MusicSquareWork {
    id?: string | number;
    userId: string;
    userName?: string;
    workType: WorkType;
    title: string;
    description?: string;
    coverUrl?: string;
    tags?: string;
    audioUrl?: string;
    audioQuality?: AudioQuality;
    audioSize?: number;
    audioDuration?: number;
    audioBitrate?: number;
    audioSampleRate?: number;
    audioFormat?: string;
    videoUrl?: string;
    videoQuality?: string;
    videoSize?: number;
    videoDuration?: number;
    videoFormat?: string;
    lyricContent?: string;
    lyricFileUrl?: string;
    hasTranslation?: number;
    status: WorkStatus;
    reviewerId?: string | number;
    reviewTime?: string;
    reviewReason?: string;
    viewCount?: number;
    likeCount?: number;
    commentCount?: number;
    shareCount?: number;
    relatedSongId?: string | number;
    relatedMvId?: string | number;
    uploadType?: UploadType;
    fileUrls?: string;
    zipFileUrl?: string;
    createTime?: string;
    updateTime?: string;
    publishTime?: string;
    isLiked?: boolean;
}
export interface SubmitWorkDTO {
    workType: WorkType;
    title: string;
    description?: string;
    coverUrl?: string;
    tags?: string;
    audioUrl?: string;
    videoUrl?: string;
    lyricContent?: string;
    lyricFileUrl?: string;
    uploadType?: UploadType;
    fileUrls?: string;
    zipFileUrl?: string;
}
export interface PageParams {
    page: number;
    size: number;
    workType?: WorkType;
    status?: WorkStatus;
}
export interface PageResponse<T> {
    records: T[];
    total: number;
    current: number;
    pages: number;
    size: number;
}
export function submitWork(data: SubmitWorkDTO) {
    return request<number>({
        url: '/music-square/work/submit',
        method: 'POST',
        data: new URLSearchParams(Object.entries(data) as any).toString(),
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        }
    });
}
export function updateWork(id: string, data: Partial<SubmitWorkDTO>) {
    return request<boolean>({
        url: `/music-square/work/${id}`,
        method: 'PUT',
        data: new URLSearchParams(Object.entries(data) as any).toString(),
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        }
    });
}
export function getWorkList(params: PageParams) {
    return request<PageResponse<MusicSquareWork>>({
        url: '/music-square/work/list',
        method: 'GET',
        params
    });
}
export function getMyWorks(page = 1, size = 20) {
    return request<PageResponse<MusicSquareWork>>({
        url: '/music-square/work/my',
        method: 'GET',
        params: { page, size }
    });
}
export function getWorkDetail(id: string) {
    return request<MusicSquareWork>({
        url: `/music-square/work/${id}`,
        method: 'GET'
    });
}
export function deleteWork(id: string) {
    return request<boolean>({
        url: `/music-square/work/${id}`,
        method: 'DELETE'
    });
}
export function likeWork(id: string) {
    return request<boolean>({
        url: `/music-square/work/${id}/like`,
        method: 'POST'
    });
}
export function unlikeWork(id: string) {
    return request<boolean>({
        url: `/music-square/work/${id}/like`,
        method: 'DELETE'
    });
}
export function detectAudio(audioUrl: string) {
    return request<{
        qualityLevel: number;
        qualityName: string;
        fileSize: number;
        duration: number;
        bitrate: number;
        sampleRate: number;
        format: string;
    }>({
        url: '/music-square/work/detect/audio',
        method: 'GET',
        params: { audioUrl }
    });
}
export function detectVideo(videoUrl: string) {
    return request<{
        fileSize: number;
        duration: number;
        quality: string;
        format: string;
        width: number;
        height: number;
    }>({
        url: '/music-square/work/detect/video',
        method: 'GET',
        params: { videoUrl }
    });
}
export function getPendingCount() {
    return request<number>({
        url: '/music-square/work/pending/count',
        method: 'GET'
    });
}
export function reviewWork(id: string, status: WorkStatus, reviewReason?: string) {
    return request<boolean>({
        url: `/music-square/work/${id}/review`,
        method: 'POST',
        params: { status, reviewReason }
    });
}
export const musicSquareWorkApi = {
    submitWork,
    updateWork,
    getWorkList,
    getMyWorks,
    getWorkDetail,
    deleteWork,
    likeWork,
    unlikeWork,
    detectAudio,
    detectVideo,
    getPendingCount,
    reviewWork
};
export default musicSquareWorkApi;
