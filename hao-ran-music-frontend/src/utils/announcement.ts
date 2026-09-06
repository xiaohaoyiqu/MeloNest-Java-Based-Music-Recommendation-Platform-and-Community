import type { PushItem } from '@/api/push';
function announcementTime(item: PushItem): number {
    const value = item.startTime || item.createTime;
    if (!value)
        return 0;
    const time = new Date(value).getTime();
    return Number.isNaN(time) ? 0 : time;
}
export function sortAnnouncements(items: PushItem[]): PushItem[] {
    return [...items].sort((left, right) => {
        const priorityDiff = Number(right.priority || 0) - Number(left.priority || 0);
        return priorityDiff || announcementTime(right) - announcementTime(left);
    });
}
export function formatAnnouncementDate(value?: string): string {
    if (!value)
        return '近日';
    const date = new Date(value);
    if (Number.isNaN(date.getTime()))
        return '近日';
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}
