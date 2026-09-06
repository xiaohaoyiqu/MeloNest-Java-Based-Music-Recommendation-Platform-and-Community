import { request } from '@/utils/request';
import type { SongInfo as BaseSongInfo } from '@/types/song';
import type { IPage } from './album';
export { searchSongs } from './search';
export type SongInfo = BaseSongInfo;
export interface GetNewSongsParams {
    page?: number;
    size?: number;
    mainType?: string;
    language?: string;
    userId?: string | number;
    mainGenre?: string;
    daysWithin?: number | string;
    sortBy?: string;
    keyword?: string;
    minDuration?: number;
    maxDuration?: number;
    paymentType?: 'free' | 'paid';
    quality?: 'lossless';
}
export function getSongById(id: string | number, _userId?: string | number, options?: {
    signal?: AbortSignal;
}) {
    return request<SongInfo>({
        url: `/song/info/${id}`,
        method: 'GET',
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export function getLocalLyric(songId: string | number) {
    return request<string>({
        url: `/lyric/local/${songId}`,
        method: 'GET'
    });
}
export function favoriteSong(songId: string | number, userId?: string | number) {
    return request<boolean>({
        url: `/song/favorite/${songId}`,
        method: 'POST'
    });
}
export function unfavoriteSong(songId: string | number, userId?: string | number) {
    return request<boolean>({
        url: `/song/favorite/${songId}`,
        method: 'DELETE'
    });
}
export function getPlayUrl(songId: string | number, quality: string = 'standard') {
    return request<string>({
        url: `/song/url/${songId}`,
        method: 'GET',
        params: { quality }
    });
}
export function getPreviewUrl(songId: string | number) {
    return request<string>({
        url: `/song/preview/${songId}`,
        method: 'GET'
    });
}
export function getNewSongs(params: GetNewSongsParams, options: {
    signal?: AbortSignal;
} = {}) {
    return request<IPage<SongInfo>>({
        url: '/song/new',
        method: 'GET',
        params,
        ...(options.signal ? { signal: options.signal } : {})
    });
}
export function getHotSongs(limit = 20, _userId?: string | number, type?: string) {
    return request<SongInfo[]>({
        url: '/song/hot',
        method: 'GET',
        params: type ? { limit, type } : { limit }
    });
}
export function getFavoriteSongs(page = 1, size = 20) {
    return request<IPage<any>>({
        url: '/song/favorite/list',
        method: 'GET',
        params: { page, size }
    });
}
export function getFavoriteSongDetails(page = 1, size = 20) {
    return request<IPage<SongInfo>>({
        url: '/song/favorite/details',
        method: 'GET',
        params: { page, size }
    });
}
export const songApi = {
    getSongById: (id: string | number, _userId?: string | number) => {
        return request<SongInfo>({
            url: `/song/info/${id}`,
            method: 'GET'
        });
    },
    getLocalLyric: (songId: string | number) => {
        return request<string>({
            url: `/lyric/local/${songId}`,
            method: 'GET'
        });
    },
    favoriteSong: (songId: string | number, userId?: string | number) => {
        return request<boolean>({
            url: `/song/favorite/${songId}`,
            method: 'POST'
        });
    },
    unfavoriteSong: (songId: string | number, userId?: string | number) => {
        return request<boolean>({
            url: `/song/favorite/${songId}`,
            method: 'DELETE'
        });
    },
    getPlayUrl: (songId: string | number, quality: string = 'standard') => {
        return request<string>({
            url: `/song/url/${songId}`,
            method: 'GET',
            params: { quality }
        });
    },
    getPreviewUrl: (songId: string | number) => {
        return request<string>({
            url: `/song/preview/${songId}`,
            method: 'GET'
        });
    },
    getFavoriteSongs: (page = 1, size = 20) => {
        return request<IPage<any>>({
            url: '/song/favorite/list',
            method: 'GET',
            params: { page, size }
        });
    },
    getFavoriteSongDetails: (page = 1, size = 20) => {
        return request<IPage<SongInfo>>({
            url: '/song/favorite/details',
            method: 'GET',
            params: { page, size }
        });
    },
    getNewSongs: (params: GetNewSongsParams, options: {
        signal?: AbortSignal;
    } = {}) => {
        return request<IPage<SongInfo>>({
            url: '/song/new',
            method: 'GET',
            params,
            ...(options.signal ? { signal: options.signal } : {})
        });
    },
    getHotSongs: (limit = 20, _userId?: string | number, type?: string) => {
        return request<SongInfo[]>({
            url: '/song/hot',
            method: 'GET',
            params: type ? { limit, type } : { limit }
        });
    }
};
