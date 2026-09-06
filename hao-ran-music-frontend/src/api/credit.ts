import { request } from '@/utils/request';
export type CreditType = 'report' | 'login' | 'listen' | 'comment' | 'share' | 'checkin' | 'vip' | 'other';
export type CreditRecordFilter = CreditType | 'positive' | 'negative' | '';
export interface CreditLevel {
    level: string;
    name: string;
    minScore: number;
    maxScore: number;
    color: string;
    privileges: string[];
}
export interface CreditRecord {
    id: string;
    userId: string;
    creditType: string;
    score: number;
    reason: string;
    createTime: string;
    operatorId: string;
}
export interface CreditPeriod {
    periodId: string;
    startDate: string;
    endDate: string;
    isActive: boolean;
}
export interface CreditStatistics {
    totalUsers: number;
    averageScore: number;
    levelDistribution: Record<string, number>;
    todayChanges: number;
    lowCreditUsers: number;
}
export function getUserCredit() {
    return request<number>({
        url: '/credit/my',
        method: 'GET'
    });
}
export function getCreditLevel(score: number) {
    return request<string>({
        url: `/credit/level/${score}`,
        method: 'GET'
    });
}
export function isBelowThreshold() {
    return request<boolean>({
        url: '/credit/check-threshold',
        method: 'GET'
    });
}
export function getCreditRecords(creditType: CreditRecordFilter = '', page = 1, size = 20) {
    return request<{
        records: CreditRecord[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/credit/records',
        method: 'GET',
        params: { creditType, page, size }
    });
}
export function getCurrentPeriod() {
    return request<string>({
        url: '/credit/period',
        method: 'GET'
    });
}
export function getNextResetTime() {
    return request<string>({
        url: '/credit/next-reset',
        method: 'GET'
    });
}
export function adjustCredit(userId: string, creditType: CreditType, score: number, reason: string) {
    return request<boolean>({
        url: '/credit/adjust',
        method: 'POST',
        params: { userId, creditType, score, reason }
    });
}
export function getCreditStatistics() {
    return request<CreditStatistics>({
        url: '/credit/statistics',
        method: 'GET'
    });
}
export function resetAllCredits() {
    return request<boolean>({
        url: '/credit/reset',
        method: 'POST'
    });
}
export const creditApi = {
    getUserCredit,
    getCreditLevel,
    isBelowThreshold,
    getCreditRecords,
    getCurrentPeriod,
    getNextResetTime,
    adjustCredit,
    getCreditStatistics,
    resetAllCredits
};
export default creditApi;
