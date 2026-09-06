import type { SongInfo } from './song';
import { request } from '@/utils/request';
export interface ListenHistoryPage<T = Record<string, unknown>> {
    records?: T[];
    list?: T[];
    total: number | string;
    page?: number;
    current?: number;
    size?: number;
    pages?: number;
    maxCount?: number;
}
export function addListenRecord(songId: string | number, progress?: number, quality?: string, isLocal?: number, eventId?: string) {
    return request<void>({
        url: '/history/listen/add',
        method: 'POST',
        params: { songId, progress, quality, isLocal, eventId }
    });
}
export function getRecentSongs(limit = 20) {
    return request<SongInfo[]>({
        url: '/history/listen/recent',
        method: 'GET',
        params: { limit }
    });
}
export function getListenHistory(page = 1, size = 20, options: {
    signal?: AbortSignal;
} = {}) {
    return request<ListenHistoryPage>({
        url: '/history/listen/list',
        method: 'GET',
        params: { page, size },
        ...(options.signal ? { signal: options.signal } : {})
    });
}
export function getHistory(page = 1, size = 20, options: {
    signal?: AbortSignal;
} = {}) {
    return getListenHistory(page, size, options);
}
export function clearListenHistory() {
    return request<void>({
        url: '/history/listen/clear',
        method: 'DELETE'
    });
}
export function deleteListenHistory(id: string) {
    return request<void>({
        url: `/history/listen/${id}`,
        method: 'DELETE'
    });
}
export function importHistory(songIds: number[]) {
    return request<number>({
        url: '/history/listen/import',
        method: 'POST',
        data: songIds
    });
}
export const listenHistoryApi = {
    addRecord: (songId: string | number, progress?: number, quality?: string, isLocal?: number, eventId?: string) => {
        return request<void>({
            url: '/history/listen/add',
            method: 'POST',
            params: { songId, progress, quality, isLocal, eventId }
        });
    },
    updateProgress: (songId: string | number, progress: number, quality: string, isLocal: number) => {
        return request<boolean>({
            url: '/history/listen/progress',
            method: 'PUT',
            params: { songId, progress, quality, isLocal }
        });
    },
    getRecentSongs: (limit = 20) => {
        return request<SongInfo[]>({
            url: '/history/listen/recent',
            method: 'GET',
            params: { limit }
        });
    },
    clearHistory: () => {
        return request<void>({
            url: '/history/listen/clear',
            method: 'DELETE'
        });
    },
    getHistory: (page = 1, size = 20) => {
        return request<ListenHistoryPage>({
            url: '/history/listen/list',
            method: 'GET',
            params: { page, size }
        });
    },
    deleteRecord: (id: string) => {
        return request<void>({
            url: `/history/listen/${id}`,
            method: 'DELETE'
        });
    },
    importHistory: (songIds: number[]) => {
        return request<number>({
            url: '/history/listen/import',
            method: 'POST',
            data: songIds
        });
    }
};
