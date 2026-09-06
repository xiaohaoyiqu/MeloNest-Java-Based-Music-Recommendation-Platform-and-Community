import { request } from '@/utils/request';
export function getFavoriteList(params?: any) {
    return request({
        url: '/song/favorite/list',
        method: 'GET',
        params
    });
}
export async function checkFavorite(songId: string | number) {
    const response = await request<Record<string, {
        isFavorite?: boolean;
    }>>({
        url: '/song/status/batch',
        method: 'POST',
        data: [songId]
    });
    return {
        ...response,
        data: Boolean(response.data?.[String(songId)]?.isFavorite)
    };
}
