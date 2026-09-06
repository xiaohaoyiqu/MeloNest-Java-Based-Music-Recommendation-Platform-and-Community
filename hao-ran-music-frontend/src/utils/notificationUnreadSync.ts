export const NOTIFICATION_UNREAD_SYNC_EVENT = 'haoran:notification-unread-sync';
export function publishNotificationUnreadCount(count: number) {
    window.dispatchEvent(new CustomEvent<number>(NOTIFICATION_UNREAD_SYNC_EVENT, {
        detail: Math.max(0, Number(count) || 0)
    }));
}
export function getNotificationUnreadCountFromEvent(event: Event): number | null {
    const value = Number((event as CustomEvent<number>).detail);
    return Number.isFinite(value) ? Math.max(0, value) : null;
}
