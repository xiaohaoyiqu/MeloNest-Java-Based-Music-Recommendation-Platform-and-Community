import { request } from '@/utils/request';
export function syncFavoriteData(userId?: string | number) {
    return request<boolean>({
        url: '/admin/sync/favorite',
        method: 'POST',
        params: { userId }
    });
}
export function fixFavoritePlaylist(userId: string) {
    return request<number>({
        url: `/admin/sync/fix-favorite/${userId}`,
        method: 'POST'
    });
}
export const syncApi = {
    syncFavoriteData,
    fixFavoritePlaylist
};
