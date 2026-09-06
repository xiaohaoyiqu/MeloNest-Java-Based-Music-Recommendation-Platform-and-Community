import { request } from '@/utils/request';
export interface AuditLog {
    id: string;
    recordId: string;
    action: string;
    operatorId: string;
    operatorName: string;
    comment: string;
    createTime: string;
}
export interface AuditStatistics {
    totalActions: number;
    byOperator: Record<string, number>;
    byAction: Record<string, number>;
    avgProcessTime: number;
}
export interface ModeratorWorkStats {
    moderatorId: string;
    moderatorName: number;
    totalActions: number;
    approvedCount: number;
    rejectedCount: number;
    avgProcessTime: number;
}
export interface AuditTypeStats {
    byType: Record<string, number>;
    byStatus: Record<string, number>;
    totalTypes: number;
}
export function getRecordLogs(recordId: string) {
    return request<AuditLog[]>({
        url: `/audit-log/record/${recordId}`,
        method: 'GET'
    });
}
export function getOperatorLogs(operatorId: string, startTime?: string, endTime?: string, page = 1, size = 20) {
    return request<{
        records: AuditLog[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: `/audit-log/operator/${operatorId}`,
        method: 'GET',
        params: { startTime, endTime, page, size }
    });
}
export function getAuditStatistics(startTime?: string, endTime?: string) {
    return request<AuditStatistics>({
        url: '/audit-log/statistics',
        method: 'GET',
        params: { startTime, endTime }
    });
}
export function getModeratorWorkStats(startTime?: string, endTime?: string) {
    return request<ModeratorWorkStats[]>({
        url: '/audit-log/moderator-work-stats',
        method: 'GET',
        params: { startTime, endTime }
    });
}
export function getAuditTypeStats(startTime?: string, endTime?: string) {
    return request<AuditTypeStats>({
        url: '/audit-log/type-stats',
        method: 'GET',
        params: { startTime, endTime }
    });
}
export function manualLog(recordId: string, action: string, comment?: string) {
    return request<void>({
        url: '/audit-log/log',
        method: 'POST',
        data: { recordId, action, comment }
    });
}
export function getMyLogs(startTime?: string, endTime?: string, page = 1, size = 20) {
    return request<{
        records: AuditLog[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/audit-log/my-logs',
        method: 'GET',
        params: { startTime, endTime, page, size }
    });
}
export const auditLogApi = {
    getRecordLogs,
    getOperatorLogs,
    getAuditStatistics,
    getModeratorWorkStats,
    getAuditTypeStats,
    manualLog,
    getMyLogs
};
