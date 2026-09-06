import type { RouteLocationRaw } from 'vue-router';
export interface NotificationNavigationSource {
    type?: string;
    link?: string;
    relatedId?: string | number;
    resourceType?: string;
    resourceId?: string | number;
    senderId?: string | number;
    metadata?: string | Record<string, unknown>;
}
export interface NotificationTarget {
    route?: RouteLocationRaw;
    externalUrl?: string;
}
function metadataOf(value: NotificationNavigationSource['metadata']): Record<string, unknown> {
    if (!value)
        return {};
    if (typeof value === 'object')
        return value;
    try {
        const parsed = JSON.parse(value);
        return parsed && typeof parsed === 'object' ? parsed : {};
    }
    catch {
        return {};
    }
}
function safeLink(link?: string): NotificationTarget | null {
    const value = link?.trim();
    if (!value)
        return null;
    if (value.startsWith('/') && !value.startsWith('//'))
        return { route: value };
    try {
        const url = new URL(value);
        if (url.protocol === 'https:' || url.protocol === 'http:')
            return { externalUrl: url.toString() };
    }
    catch {
    }
    return null;
}
function resourceRoute(type: string, id: string): RouteLocationRaw | null {
    const normalized = type.toLowerCase().replace(/_/g, '-').trim();
    const encoded = encodeURIComponent(id);
    const directRoutes: Record<string, string> = {
        song: `/song/${encoded}`,
        album: `/album/${encoded}`,
        artist: `/artist/${encoded}`,
        mv: `/mv/${encoded}`,
        playlist: `/playlist/${encoded}`,
        user: `/user/${encoded}`,
        marketplace: `/square/marketplace/${encoded}`,
        'marketplace-item': `/square/marketplace/${encoded}`
    };
    if (directRoutes[normalized])
        return directRoutes[normalized];
    if (['post', 'music-post', 'dynamic'].includes(normalized)) {
        return { path: '/square', query: { postId: id } };
    }
    if (['work', 'music-square-work', 'creator-work', 'submission'].includes(normalized)) {
        return { path: '/my/creator', query: { tab: 'works', workId: id } };
    }
    return null;
}
export function resolveNotificationTarget(source: NotificationNavigationSource): NotificationTarget | null {
    const metadata = metadataOf(source.metadata);
    const type = String(metadata.resourceType || metadata.targetType || source.resourceType || '').trim();
    const id = String(metadata.resourceId || metadata.targetId || source.resourceId || source.relatedId || '').trim();
    if (type && id) {
        const route = resourceRoute(type, id);
        if (route)
            return { route };
    }
    const linked = safeLink(source.link);
    if (linked)
        return linked;
    const notificationType = String(source.type || '').toLowerCase();
    if (notificationType === 'system' || notificationType === 'announcement')
        return { route: '/announcements' };
    if (notificationType.startsWith('moderation_'))
        return { route: { path: '/my/creator', query: { tab: 'works' } } };
    if (notificationType.startsWith('creator_'))
        return { route: '/my/creator' };
    if (notificationType === 'follow') {
        const userId = source.senderId || source.relatedId;
        if (userId)
            return { route: `/user/${encodeURIComponent(String(userId))}` };
    }
    if (notificationType === 'revenue') {
        return { route: { path: '/my/creator', query: { tab: 'earnings' } } };
    }
    return null;
}
