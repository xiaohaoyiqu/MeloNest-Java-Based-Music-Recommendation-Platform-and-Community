import { request } from '@/utils/request';
export type WorkType = number;
export type WorkStatus = 0 | 1 | 2;
export type WorkLanguage = number;
export type SubscribePeriod = 0 | 1 | 3 | 12;
export type UploadType = 1 | 2 | 3;
export type AudioQuality = 1 | 2 | 3 | 4 | 5;
export interface CreatorWork {
    id?: string | number;
    userId?: string;
    creatorId?: string;
    workName: string;
    workType: WorkType;
    coverUrl: string;
    fileUrl: string;
    duration?: number;
    fileSize?: number;
    language: WorkLanguage;
    description: string;
    tags?: string;
    lyric?: string;
    showRealName: boolean;
    status?: WorkStatus;
    songId?: string | number;
    playCount?: number;
    likeCount?: number;
    shareCount?: number;
    downloadCount?: number;
    reviewReason?: string;
    reviewerId?: string | number;
    reviewTime?: string;
    createTime?: string;
    updateTime?: string;
    allowDownload?: number;
    allowComment?: number;
    allowShare?: number;
    isPaid?: number;
    price?: number;
    subscribePeriod?: SubscribePeriod;
    uploadType?: UploadType;
    fileUrls?: string;
    zipFileUrl?: string;
    detectedQuality?: AudioQuality;
    audioDuration?: number;
    bitrate?: number;
    sampleRate?: number;
    audioFormat?: string;
}
export interface WorksPageParams {
    current: number;
    size: number;
    status?: WorkStatus;
    userId?: string | number;
}
export interface WorksPageResult {
    records: CreatorWork[];
    total: number;
    current: number;
    pages: number;
    size: number;
}
export function submitCreatorWork(workData: Partial<CreatorWork>) {
    return request<number>({
        url: '/creator/work/submit',
        method: 'POST',
        data: workData
    });
}
export function getCreatorProfile() {
    return request<any>({
        url: '/creator/my',
        method: 'GET'
    });
}
export function getMyWorks(status?: WorkStatus, page = 1, size = 20) {
    return request<WorksPageResult>({
        url: '/creator/work/my',
        method: 'GET',
        params: { status, page, size }
    });
}
export function getWorkDetail(workId: string | number) {
    return request<CreatorWork>({
        url: `/creator/work/${workId}`,
        method: 'GET'
    });
}
export function updateWork(workId: string | number, workData: Partial<CreatorWork>) {
    return request<boolean>({
        url: `/creator/work/${workId}`,
        method: 'PUT',
        data: workData
    });
}
export function deleteWork(workId: string | number) {
    return request<boolean>({
        url: `/creator/work/${workId}`,
        method: 'DELETE'
    });
}
export function policyUpdate(workId: string | number, reason: string) {
    return request<boolean>({
        url: `/creator/work/${workId}/policy-update`,
        method: 'POST',
        params: { reason }
    });
}
export function getPendingWorkCount() {
    return request<number>({
        url: '/creator/work/pending-count',
        method: 'GET'
    });
}
export function pageWorks(params: WorksPageParams) {
    return request<WorksPageResult>({
        url: '/creator/work/page',
        method: 'GET',
        params
    });
}
export function reviewWork(workId: string | number, status: WorkStatus, reviewReason?: string) {
    return request<boolean>({
        url: `/creator/work/${workId}/review`,
        method: 'POST',
        params: { status, reviewReason }
    });
}
export const creatorWorkApi = {
    submitCreatorWork,
    getCreatorProfile,
    getMyWorks,
    getWorkDetail,
    updateWork,
    deleteWork,
    policyUpdate,
    getPendingWorkCount,
    pageWorks,
    reviewWork
};
