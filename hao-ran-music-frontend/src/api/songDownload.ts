import type { AxiosResponse } from 'axios';
import service, { request } from '@/utils/request';
export type AudioQuality = 'standard' | 'high' | 'lossless';
export interface DownloadInfo {
    songId: string | number;
    songName: string;
    artistName: string;
    coverUrl: string;
    quality: string;
    downloadUrl: string;
    fileSize: number;
    duration: number;
    format: string;
    price?: number;
    vipRequired: boolean;
}
export function getDownloadInfo(songId: string | number, quality: AudioQuality = 'standard') {
    return request<DownloadInfo>({
        url: `/song/download/${songId}/info`,
        method: 'GET',
        params: { quality }
    });
}
export function downloadSong(songId: string | number, quality: AudioQuality = 'standard') {
    return service({
        url: `/song/download/${songId}`,
        method: 'GET',
        params: { quality },
        responseType: 'blob'
    }) as Promise<AxiosResponse<Blob>>;
}
export const songDownloadApi = {
    getDownloadInfo,
    downloadSong
};
