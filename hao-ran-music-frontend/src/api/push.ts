import { request } from '@/utils/request';
export enum PushType {
    ANNOUNCEMENT = 'announcement',
    ACTIVITY = 'activity',
    NEWSONG = 'newsong',
    TOPIC = 'topic',
    NEWS = 'news'
}
export interface PushItem {
    id: string;
    type: PushType;
    title: string;
    description: string;
    coverUrl?: string;
    link?: string;
    fallbackLink?: string;
    badge?: string;
    priority: number;
    startTime?: string;
    endTime?: string;
    read?: boolean;
    createTime?: string;
}
export function getActivePushNotificationsFromAPI() {
    return request<PushItem[]>({
        url: '/push-notifications/active',
        method: 'GET'
    });
}
export function getPushNotificationsByType(type: string) {
    return request<PushItem[]>({
        url: `/push-notifications/type/${type}`,
        method: 'GET'
    });
}
