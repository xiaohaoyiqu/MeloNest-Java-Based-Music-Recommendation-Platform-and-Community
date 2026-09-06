import { request } from '@/utils/request';
export interface Notification {
    id: string;
    userId: string;
    senderId?: string;
    senderName?: string;
    senderAvatar?: string;
    groupId?: string;
    groupCount?: number;
    groupAmount?: string | number;
    metadata?: string;
    type: string;
    title: string;
    content: string;
    link?: string;
    coverUrl?: string;
    relatedId?: string;
    isRead: boolean;
    createTime: string;
}
export interface NotificationDetail {
    id: string;
    senderId?: string;
    senderName?: string;
    senderAvatar?: string;
    title: string;
    content: string;
    amount?: string | number;
    resourceName?: string;
    resourceType?: string;
    resourceId?: string;
    type: string;
    isRead: boolean;
    link?: string;
    createTime: string;
    metadata?: string;
}
type RawNotification = Omit<Notification, 'id' | 'userId' | 'senderId' | 'relatedId' | 'isRead'> & {
    id: string | number;
    userId: string | number;
    senderId?: string | number | null;
    relatedId?: string | number | null;
    isRead: boolean | number | string;
};
type RawNotificationDetail = Omit<NotificationDetail, 'id' | 'senderId' | 'resourceId' | 'isRead'> & {
    id: string | number;
    senderId?: string | number | null;
    resourceId?: string | number | null;
    isRead: boolean | number | string;
};
function optionalId(value: string | number | null | undefined): string | undefined {
    if (value === null || value === undefined)
        return undefined;
    return String(value);
}
function normalizeNotification(item: RawNotification): Notification {
    return {
        ...item,
        id: String(item.id),
        userId: String(item.userId),
        senderId: optionalId(item.senderId),
        relatedId: optionalId(item.relatedId),
        groupAmount: item.groupAmount ?? undefined,
        isRead: item.isRead === true || item.isRead === 1 || item.isRead === '1'
    };
}
function normalizeNotificationDetail(item: RawNotificationDetail): NotificationDetail {
    return {
        ...item,
        id: String(item.id),
        senderId: optionalId(item.senderId),
        resourceId: optionalId(item.resourceId),
        isRead: item.isRead === true || item.isRead === 1 || item.isRead === '1',
        amount: item.amount ?? undefined
    };
}
export interface NotificationBroadcastTask {
    taskId: string;
    operatorId?: string | number;
    title: string;
    content: string;
    link?: string;
    cursorUserId?: string | number;
    totalUsers: number;
    successCount: number;
    failCount: number;
    batchCount: number;
    status: 'pending' | 'running' | 'success' | 'failed' | string;
    attemptCount: number;
    maxAttempts: number;
    errorMessage?: string;
    nextRetryTime?: string;
    startedAt?: string;
    completedAt?: string;
    createdAt?: string;
    updatedAt?: string;
}
export interface NotificationBroadcastStatusRow {
    status: string;
    taskCount: number;
    totalUsers: number;
    successCount: number;
    failCount: number;
}
export interface NotificationBroadcastStatus {
    statuses: NotificationBroadcastStatusRow[];
    maxAttempts: number;
    batchSize: number;
}
export interface NotificationBroadcastRequest {
    title: string;
    content: string;
    link?: string;
    coverUrl?: string;
}
export const submitBroadcast = (data: NotificationBroadcastRequest) => {
    return request<NotificationBroadcastTask>({
        url: '/admin/notification/broadcast',
        method: 'POST',
        data
    });
};
export const getBroadcastTaskStatus = () => {
    return request<NotificationBroadcastStatus>({
        url: '/admin/notification/broadcast/tasks/status',
        method: 'GET'
    });
};
export const getBroadcastTask = (taskId: string) => {
    return request<NotificationBroadcastTask>({
        url: `/admin/notification/broadcast/tasks/${taskId}`,
        method: 'GET'
    });
};
export const retryBroadcastTask = (taskId: string) => {
    return request<NotificationBroadcastTask>({
        url: `/admin/notification/broadcast/tasks/${taskId}/retry`,
        method: 'POST'
    });
};
export const getUnreadCount = () => {
    return request<number>({
        url: '/notifications/unread-count',
        method: 'GET',
        timeout: 5000
    });
};
export const getNotificationList = async (params: {
    current?: number;
    size?: number;
    type?: string;
    isRead?: boolean;
}) => {
    const response = await request<{
        records: RawNotification[];
        total: number;
        current: number;
        size: number;
        pages: number;
    }>({
        url: '/notifications/list',
        method: 'GET',
        params: {
            page: params.current,
            size: params.size,
            type: params.type,
            isRead: params.isRead
        }
    });
    return {
        ...response,
        data: {
            ...response.data,
            records: (response.data?.records || []).map(normalizeNotification)
        }
    };
};
export const getLatestNotifications = async (limit = 10) => {
    const response = await request<RawNotification[]>({
        url: '/notifications/latest',
        method: 'GET',
        params: { limit }
    });
    return {
        ...response,
        data: (response.data || []).map(normalizeNotification)
    };
};
export const markAsRead = (id: string) => {
    return request<void>({
        url: `/notifications/read/${id}`,
        method: 'POST'
    });
};
export const markAllAsRead = () => {
    return request<number>({
        url: '/notifications/read-all',
        method: 'POST'
    });
};
export const deleteNotification = (id: string) => {
    return request<void>({
        url: `/notifications/${id}`,
        method: 'DELETE'
    });
};
export const deleteReadNotifications = () => {
    return request<number>({
        url: '/notifications/delete-read',
        method: 'DELETE'
    });
};
export const getNotificationGroupDetails = async (groupId: string) => {
    const response = await request<RawNotificationDetail[]>({
        url: `/notifications/group/${groupId}`,
        method: 'GET'
    });
    return {
        ...response,
        data: (response.data || []).map(normalizeNotificationDetail)
    };
};
