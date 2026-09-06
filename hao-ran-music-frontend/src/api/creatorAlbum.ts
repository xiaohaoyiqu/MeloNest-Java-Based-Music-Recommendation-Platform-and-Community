import { request } from '@/utils/request';
export type AlbumType = 1 | 2 | 3 | 4;
export type AlbumStatus = 0 | 1 | 2;
export type AlbumLanguage = 1 | 2 | 3 | 4 | 5;
export type UploadType = 1 | 2 | 3;
export type AudioQuality = 1 | 2 | 3 | 4 | 5;
export interface CreatorAlbum {
    id?: string | number;
    userId: string;
    albumName: string;
    albumType: AlbumType;
    coverUrl?: string;
    description?: string;
    tags?: string;
    language: AlbumLanguage;
    releaseDate?: string;
    status: AlbumStatus;
    isPublishDateSet?: number;
    autoCreateSong?: number;
    songCount?: number;
    totalDuration?: number;
    createTime?: string;
    updateTime?: string;
}
export interface AlbumSong {
    id?: string | number;
    albumId: string;
    songId?: string | number;
    songName: string;
    position: number;
    isSingle?: number;
    audioUrl?: string;
    audioQuality?: AudioQuality;
    fileSize?: number;
    duration?: number;
    bitrate?: number;
    sampleRate?: number;
    format?: string;
    uploadType?: UploadType;
    fileUrls?: string;
    zipFileUrl?: string;
    createTime?: string;
}
export interface PageResponse<T> {
    records: T[];
    total: number;
    current: number;
    pages: number;
    size: number;
}
export interface PageParams {
    page: number;
    size: number;
}
export function createAlbum(data: {
    albumName: string;
    albumType: AlbumType;
    coverUrl?: string;
    description?: string;
    tags?: string;
    language?: AlbumLanguage;
    releaseDate?: string;
    autoCreateSong?: number;
}) {
    return request<number>({
        url: '/creator/album',
        method: 'POST',
        data: new URLSearchParams(Object.entries(data) as any).toString(),
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        }
    });
}
export function updateAlbum(id: string, data: {
    albumName?: string;
    coverUrl?: string;
    description?: string;
    tags?: string;
}) {
    return request<boolean>({
        url: `/creator/album/${id}`,
        method: 'PUT',
        data: new URLSearchParams(Object.entries(data) as any).toString(),
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        }
    });
}
export function publishAlbum(id: string) {
    return request<boolean>({
        url: `/creator/album/${id}/publish`,
        method: 'POST'
    });
}
export function deleteAlbum(id: string) {
    return request<boolean>({
        url: `/creator/album/${id}`,
        method: 'DELETE'
    });
}
export function getAlbumDetail(id: string) {
    return request<CreatorAlbum>({
        url: `/creator/album/${id}`,
        method: 'GET'
    });
}
export function getMyAlbums(page = 1, size = 20) {
    return request<PageResponse<CreatorAlbum>>({
        url: '/creator/album/my',
        method: 'GET',
        params: { page, size }
    });
}
export function getAlbumSongs(id: string) {
    return request<AlbumSong[]>({
        url: `/creator/album/${id}/songs`,
        method: 'GET'
    });
}
export function addSongsToAlbum(id: string, songIds: number[]) {
    return request<boolean>({
        url: `/creator/album/${id}/songs`,
        method: 'POST',
        data: songIds
    });
}
export function removeSongFromAlbum(albumId: string, songId: string) {
    return request<boolean>({
        url: `/creator/album/${albumId}/songs/${songId}`,
        method: 'DELETE'
    });
}
export function updateSongPosition(albumId: string, songId: string, position: number) {
    return request<boolean>({
        url: `/creator/album/${albumId}/songs/${songId}`,
        method: 'PUT',
        params: { position }
    });
}
export function uploadSongToAlbum(id: string, data: {
    songName: string;
    audioUrl: string;
    uploadType?: UploadType;
}) {
    return request<number>({
        url: `/creator/album/${id}/upload-song`,
        method: 'POST',
        data: new URLSearchParams(Object.entries(data) as any).toString(),
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        }
    });
}
export const creatorAlbumApi = {
    createAlbum,
    updateAlbum,
    publishAlbum,
    deleteAlbum,
    getAlbumDetail,
    getMyAlbums,
    getAlbumSongs,
    addSongsToAlbum,
    removeSongFromAlbum,
    updateSongPosition,
    uploadSongToAlbum
};
export default creatorAlbumApi;
