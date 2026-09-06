import { request } from '@/utils/request';
export type WorkType = number;
export type WorkStatus = number;
export type WorkLanguage = number;
export interface UserWork {
    id?: string | number;
    userId: string;
    nickname?: string;
    avatar?: string;
    workType: WorkType;
    workName: string;
    coverUrl: string;
    fileUrl: string;
    fileUrls?: string | string[];
    zipFileUrl?: string;
    lyricFileUrl?: string;
    uploadType?: number;
    versionType?: string;
    productionType?: string;
    fileSize?: number;
    duration?: number;
    description: string;
    tags?: string;
    language: WorkLanguage;
    lyric?: string;
    showRealName?: boolean;
    source?: number;
    status: WorkStatus;
    reviewerId?: string | number;
    reviewerName?: string;
    reviewTime?: string;
    reviewReason?: string;
    publishTime?: string;
    songId?: string | number;
    playCount?: number;
    likeCount?: number;
    collectCount?: number;
    shareCount?: number;
    downloadCount?: number;
    virusScanned?: boolean;
    virusScanResult?: string;
    rewardPoints?: number;
    createTime: string;
    updateTime?: string;
    allowDownload?: number;
    allowComment?: number;
    allowShare?: number;
}
export interface WorkStats {
    totalCount: number;
    pendingCount: number;
    publishedCount: number;
    rejectedCount: number;
}
export interface RewardLevel {
    name: string;
    points: number;
    likeRequired: number;
    collectRequired: number;
    playRequired: number;
}
export interface RewardProgress {
    currentLevel: string;
    nextLevel?: string;
    currentPoints: number;
    nextLevelPoints?: number;
    likeCount: number;
    collectCount: number;
    playCount: number;
    progress: {
        like: number;
        collect: number;
        play: number;
    };
    completed: {
        like: boolean;
        collect: boolean;
        play: boolean;
    };
}
export interface RewardProgressResponse {
    workId: string;
    workName: string;
    status: number;
    rewardPoints: number;
    progress: RewardProgress;
}
export interface RewardRules {
    levels: {
        bronze: RewardLevel;
        silver: RewardLevel;
        gold: RewardLevel;
        platinum: RewardLevel;
    };
    description: string;
    note: string;
}
export function submitWork(workData: Partial<UserWork>) {
    return request<number>({
        url: '/user-work/submit',
        method: 'POST',
        data: workData
    });
}
export function getMyWorks(status?: WorkStatus) {
    return request<UserWork[]>({
        url: '/user-work/my',
        method: 'GET',
        params: { status }
    });
}
export function getWorkDetail(workId: string | number) {
    return request<UserWork>({
        url: `/user-work/${workId}`,
        method: 'GET'
    });
}
export function updateWork(workId: string | number, workData: Partial<UserWork>) {
    return request<boolean>({
        url: `/user-work/${workId}`,
        method: 'PUT',
        data: workData
    });
}
export function deleteWork(workId: string | number) {
    return request<boolean>({
        url: `/user-work/${workId}`,
        method: 'DELETE'
    });
}
export function getWorkStats() {
    return request<WorkStats>({
        url: '/user-work/stats',
        method: 'GET'
    });
}
export function getRewardProgress(workId: string | number) {
    return request<RewardProgressResponse>({
        url: `/user-work/${workId}/reward-progress`,
        method: 'GET'
    });
}
export function getRewardRules() {
    return request<RewardRules>({
        url: '/user-work/reward-rules',
        method: 'GET'
    });
}
export const userWorkApi = {
    submitWork,
    getMyWorks,
    getWorkDetail,
    updateWork,
    deleteWork,
    getWorkStats,
    getRewardProgress,
    getRewardRules
};
