import { request } from '@/utils/request';
export type EntityId = string | number;
export interface ModerationItem {
    id: string;
    contentType: string;
    contentId: string;
    title: string;
    description?: string;
    submitterId: string;
    submitterName: string;
    status: number;
    reviewerId?: string | number;
    reviewerName?: string;
    reviewTime?: string;
    reviewReason?: string;
    createTime: string;
    policyId?: string | number;
    reauditFromId?: string | number;
    reapplyAvailableTime?: string;
    canModify?: number;
}
export interface ModerationPolicy {
    id: string;
    policyCode: string;
    policyName: string;
    policyContent: string;
    affectScope: string;
    reauditRequired: number;
    effectiveTime: string;
    createTime: string;
}
export interface PolicyUpdateDTO {
    policyCode: string;
    policyName: string;
    policyContent: string;
    affectScope: string;
    reauditRequired: boolean;
}
export function updatePolicy(data: PolicyUpdateDTO) {
    return request<void>({
        url: '/admin/moderation-policy/update',
        method: 'POST',
        data
    });
}
export function getPolicyList() {
    return request<ModerationPolicy[]>({
        url: '/admin/moderation-policy/list',
        method: 'GET'
    });
}
export function getPolicyByCode(code: string) {
    return request<ModerationPolicy>({
        url: `/admin/moderation-policy/${code}`,
        method: 'GET'
    });
}
export interface ModerationAppeal {
    id: string;
    moderationId: string;
    userId: string;
    appealReason: string;
    appealContent: string;
    attachments?: string;
    attachmentAssetIds?: string[];
    status: number;
    reviewerId?: string | number;
    decisionReason?: string;
    submitTime: string;
    processTime?: string;
}
export interface AppealSubmitDTO {
    moderationId: string;
    appealReason: string;
    appealContent: string;
    attachments?: string[];
    attachmentAssetIds?: string[];
}
export function submitAppeal(data: AppealSubmitDTO) {
    const payload = {
        ...data,
        attachments: undefined
    };
    return request<EntityId>({
        url: '/moderation/appeal/submit',
        method: 'POST',
        data: payload
    });
}
export function processAppeal(appealId: string, decision: number, decisionReason: string) {
    return request<void>({
        url: `/moderation/appeal/process/${appealId}`,
        method: 'POST',
        params: { decision, decisionReason }
    });
}
export function getMyAppealList(params: {
    page: number;
    size: number;
    status?: number;
}) {
    return request<{
        records: ModerationAppeal[];
        total: number;
    }>({
        url: '/appeal/my',
        method: 'POST',
        data: {
            page: params.page,
            size: params.size,
            appealStatus: params.status === undefined ? undefined : String(params.status)
        }
    });
}
export function getAllAppealList(params: {
    page: number;
    size: number;
    status?: number;
}) {
    return request<{
        records: ModerationAppeal[];
        total: number;
    }>({
        url: '/admin/appeal/list',
        method: 'POST',
        data: {
            page: params.page,
            size: params.size,
            appealStatus: params.status === undefined ? undefined : String(params.status)
        }
    });
}
export function canAppeal(moderationId: string) {
    return request<boolean>({
        url: `/moderation/appeal/can-appeal/${moderationId}`,
        method: 'GET'
    });
}
export interface UserViolation {
    id: string;
    userId: string;
    violationType: string;
    violationLevel: number;
    contentType: string;
    contentId: string;
    description: string;
    penaltyType?: string;
    penaltyValue?: string;
    isResolved: number;
    createTime: string;
}
export function getMyViolations(params: {
    page: number;
    size: number;
}) {
    return request<{
        records: UserViolation[];
        total: number;
    }>({
        url: '/violation/my',
        method: 'GET',
        params
    });
}
export function getAllViolations(params: {
    page: number;
    size: number;
    userId?: string | number;
    violationLevel?: number;
}) {
    return request<{
        records: UserViolation[];
        total: number;
    }>({
        url: '/admin/violation/list',
        method: 'GET',
        params
    });
}
export function getReauditList(params: {
    page: number;
    size: number;
}) {
    return request<{
        records: ModerationItem[];
        total: number;
    }>({
        url: '/moderation/reaudit/list',
        method: 'GET',
        params
    });
}
export function processReaudit(moderationId: string, status: number, reason: string) {
    return request<void>({
        url: '/moderation/reaudit/process',
        method: 'POST',
        data: { moderationId, status, reason }
    });
}
export interface ModerationRecord {
    id: string;
    targetType: string;
    targetId: string;
    submitterId: string;
    submitterSource: string;
    assignedModeratorId?: string | number;
    assignedTime?: string;
    moderatorOnlineStatus?: number;
    priority: number;
    status: 'pending' | 'in_progress' | 'approved' | 'rejected' | 'skipped' | string;
    reviewerId?: string | number;
    reviewTime?: string;
    reviewResult?: 'approved' | 'rejected' | string;
    reviewReason?: string;
    createTime: string;
    updateTime: string;
}
export interface ModeratorDetail {
    id: string | number;
    username?: string;
    nickname?: string;
    avatar?: string;
    isOnline?: number;
    moderatorStatus?: string;
    currentTaskCount?: number;
    hasQuota?: boolean;
}
export interface WorkStatus {
    isWorkTime: boolean;
    onlineModerators: Array<string | number>;
    activeModerators: Array<string | number>;
    onlineModeratorDetails?: ModeratorDetail[];
    activeModeratorDetails?: ModeratorDetail[];
    onlineModeratorCount?: number;
    activeModeratorCount?: number;
    moderatorLoads: Record<string, number>;
}
export function getWorkStatus() {
    return request<WorkStatus>({
        url: '/moderation/work-status',
        method: 'GET'
    });
}
export function getOnlineModerators() {
    return request<EntityId[]>({
        url: '/moderation/online-moderators',
        method: 'GET'
    });
}
export function getActiveModerators() {
    return request<EntityId[]>({
        url: '/moderation/active-moderators',
        method: 'GET'
    });
}
export function getModeratorLoads() {
    return request<Record<string, number>>({
        url: '/moderation/moderator-loads',
        method: 'GET'
    });
}
export function userOffline() {
    return request<void>({
        url: '/moderation/offline',
        method: 'POST'
    });
}
export function createModerationRecord(data: {
    targetType: string;
    targetId: string;
    submitterId: string;
    submitterSource: string;
    priority?: number;
}) {
    return request<EntityId>({
        url: '/moderation/create',
        method: 'POST',
        params: data
    });
}
export function assignModerator(recordId: string | number, moderatorId?: string | number) {
    return request<EntityId>({
        url: `/moderation/assign/${recordId}`,
        method: 'POST',
        params: moderatorId === undefined ? undefined : { moderatorId }
    });
}
export function startReview(recordId: string | number) {
    return request<void>({
        url: `/moderation/start/${recordId}`,
        method: 'POST'
    });
}
export function approveReview(recordId: string | number, reason?: string) {
    return request<void>({
        url: `/moderation/approve/${recordId}`,
        method: 'POST',
        params: { reason }
    });
}
export function rejectReview(recordId: string | number, reason: string) {
    return request<void>({
        url: `/moderation/reject/${recordId}`,
        method: 'POST',
        params: { reason }
    });
}
export function skipReview(recordId: string | number, reason: string) {
    return request<void>({
        url: `/moderation/skip/${recordId}`,
        method: 'POST',
        params: { reason }
    });
}
export function getPendingAssignments(params?: {
    targetType?: string;
    submitterSource?: string;
    page?: number;
    size?: number;
}) {
    return request<{
        records: ModerationRecord[];
        total: number;
        current: number;
        pages: number;
        size: number;
    }>({
        url: '/moderation/pending-assignments',
        method: 'GET',
        params
    });
}
export function getMyTasks(moderatorId: string | number, params?: {
    status?: string;
    targetType?: string;
    page?: number;
    size?: number;
}) {
    return request<{
        records: ModerationRecord[];
        total: number;
        current: number;
        pages: number;
        size: number;
    }>({
        url: `/moderation/my-tasks/${moderatorId}`,
        method: 'GET',
        params
    });
}
export function getModeratorStats(moderatorId?: string | number) {
    return request<{
        totalTasks: number;
        pendingTasks: number;
        completedTasks: number;
        approvedTasks: number;
        rejectedTasks: number;
        skippedTasks: number;
        avgProcessTime: number;
    }>({
        url: moderatorId === undefined ? '/moderation/stats/global' : `/moderation/stats/${moderatorId}`,
        method: 'GET'
    });
}
export function getGlobalStats() {
    return request<{
        totalRecords: number;
        pendingRecords: number;
        inProgressRecords: number;
        completedRecords: number;
        todayCompleted: number;
        onlineModerators: number;
        activeModerators: number;
    }>({
        url: '/moderation/stats/global',
        method: 'GET'
    });
}
export function hasQuota(moderatorId: string | number) {
    return request<boolean>({
        url: `/moderation/has-quota/${moderatorId}`,
        method: 'GET'
    });
}
