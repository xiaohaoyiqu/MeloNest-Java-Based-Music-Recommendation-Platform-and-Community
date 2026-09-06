import { request } from '@/utils/request';
import { logger as frontendLogger } from '@/utils/logger';
export async function downloadLocalProxyPackage(): Promise<Blob> {
    const response = await request<Blob>({
        url: '/local-music/download-proxy',
        method: 'GET',
        responseType: 'blob'
    });
    return response.data as Blob;
}
export interface LocalMusicVO {
    id: string | number;
    name: string;
    artistName: string;
    albumName: string;
    duration: number;
    fileSize: number;
    fileFormat: string;
    playUrl: string;
    coverUrl: string;
    lyricUrl: string;
    playCount: number;
    createTime: string;
    added?: boolean;
    isFavorite?: boolean;
    songId?: string | number;
    resourceType?: number;
    quality?: string | number;
    filePath?: string;
    fileExists?: boolean;
    versionType?: string | number;
    versionName?: string;
    lyric?: string;
}
export function addLocalMusic(filePath: string, name?: string, artist?: string, album?: string) {
    return request<LocalMusicVO>({
        url: '/local-music/song/add',
        method: 'POST',
        data: { filePath, name, artist, album }
    });
}
export function addBatchLocalMusic(filePaths: string[]) {
    return request<LocalMusicVO[]>({
        url: '/local-music/song/add-batch',
        method: 'POST',
        data: filePaths
    });
}
export interface LocalMusicListQuery {
    keyword?: string;
    sortField?: 'created' | 'name' | 'artist' | 'duration' | 'playcount';
    sortOrder?: 'asc' | 'desc';
}
export function getLocalMusicList(page: number = 1, size: number = 20, resourceType?: number, query: LocalMusicListQuery = {}) {
    return request<{
        records: LocalMusicVO[];
        total: number;
        size: number;
        current: number;
        pages: number;
    }>({
        url: '/local-music/song/list',
        method: 'GET',
        params: { page, size, resourceType, ...query }
    });
}
export function getLocalMusicDetail(id: string | number) {
    return request<LocalMusicVO>({
        url: `/local-music/song/${id}`,
        method: 'GET'
    });
}
export function deleteLocalMusic(id: string | number) {
    return request({
        url: `/local-music/song/${id}`,
        method: 'DELETE'
    });
}
export function updateLocalMusic(id: string | number, data: {
    name: string;
    artist?: string;
    artistName?: string;
    album?: string;
    albumName?: string;
    versionType?: string | number;
    versionName?: string;
}) {
    return request<LocalMusicVO>({
        url: `/local-music/song/${id}`,
        method: 'PUT',
        data: {
            name: data.name,
            artistName: data.artistName ?? data.artist ?? '',
            albumName: data.albumName ?? data.album,
            versionType: data.versionType,
            versionName: data.versionName
        }
    });
}
export function updateLocalMusicLyric(id: string | number, lyric: string) {
    return request<boolean>({
        url: `/local-music/song/${id}/lyric`,
        method: 'PUT',
        data: { lyric }
    });
}
export function batchDeleteLocalMusic(ids: Array<string | number>) {
    return request({
        url: '/local-music/song/batch',
        method: 'DELETE',
        data: ids
    });
}
export function clearLocalMusic() {
    return request({
        url: '/local-music/song/clear',
        method: 'DELETE'
    });
}
export function scanSongs(path: string, page: number = 1, size: number = 50) {
    return request({
        url: '/local-music/song/scan',
        method: 'GET',
        params: { path, page, size }
    });
}
export function addBySongIds(songIds: number[]) {
    return request<LocalMusicVO[]>({
        url: '/local-music/song/add-by-ids',
        method: 'POST',
        data: { songIds }
    });
}
export function scanMVs(path: string, page: number = 1, size: number = 50) {
    return request({
        url: '/local-music/mv/scan',
        method: 'GET',
        params: { path, page, size }
    });
}
export function addMVsByIds(mvIds: number[]) {
    return request<LocalMusicVO[]>({
        url: '/local-music/mv/add-by-ids',
        method: 'POST',
        data: { mvIds }
    });
}
export function searchLocalMusicBySongName(songName: string, excludeId: string | number, page: number = 1, size: number = 10) {
    return request<{
        records: LocalMusicVO[];
        total: number;
        size: number;
        current: number;
        pages: number;
    }>({
        url: '/local-music/song/search-by-name',
        method: 'GET',
        params: { songName, excludeId, page, size }
    });
}
function parseFileName(filename: string): {
    name: string;
    artist: string;
} {
    const nameWithoutExt = filename.replace(/\.[^/.]+$/, '');
    const dashIndex = nameWithoutExt.lastIndexOf(' - ');
    if (dashIndex > 0 && dashIndex < nameWithoutExt.length - 1) {
        return {
            name: nameWithoutExt.substring(0, dashIndex).trim(),
            artist: nameWithoutExt.substring(dashIndex + 2).trim()
        };
    }
    return {
        name: nameWithoutExt,
        artist: '未知歌手'
    };
}
function extractFilename(path: string): string {
    if (!path)
        return '';
    return path.split(/[\\/]/).pop() || path;
}
interface LocalFileInfo {
    size: number;
    duration?: number;
    quality?: 'lossless' | 'high' | 'standard';
    audio?: {
        title?: string;
        artist?: string;
        album?: string;
    };
}
function getQualityValue(quality?: 'lossless' | 'high' | 'standard'): number {
    if (quality === 'lossless')
        return 2;
    if (quality === 'high')
        return 1;
    return 0;
}
async function getLocalFileInfo(filePath: string): Promise<LocalFileInfo | null> {
    if (filePath.startsWith('local:')) {
        return null;
    }
    if (!filePath || (!filePath.includes(':') && !filePath.includes('\\'))) {
        return null;
    }
    try {
        const localProxy = await import('@/api/localProxy');
        const proxyUrl = await localProxy.getProxyUrl();
        if (!proxyUrl) {
            return null;
        }
        const fileInfo = await localProxy.getFileInfo(filePath);
        if (fileInfo && fileInfo.size) {
            const audioInfo = (fileInfo as any).audio;
            return {
                size: fileInfo.size || 0,
                duration: audioInfo?.duration || 0,
                quality: audioInfo?.quality || undefined,
                audio: audioInfo ? {
                    title: audioInfo.title,
                    artist: audioInfo.artist,
                    album: audioInfo.album
                } : undefined
            };
        }
    }
    catch (err) {
        frontendLogger.capture('warn', '[getLocalFileInfo] 获取文件信息失败:', err);
    }
    return null;
}
export async function addManualMV(data: {
    filePath?: string;
    name?: string;
    artist?: string;
    cover?: string;
    fileSize?: number;
    duration?: number;
    quality?: number;
}) {
    let mvName = data.name;
    let artist = data.artist;
    const fileInfo = data.filePath && !data.filePath.startsWith('local:') && (data.filePath.includes(':') || data.filePath.includes('\\'))
        ? await getLocalFileInfo(data.filePath)
        : null;
    if (data.filePath) {
        const filename = extractFilename(data.filePath);
        if (!mvName || mvName.includes('\\') || mvName.includes('/')) {
            const parsed = parseFileName(filename);
            mvName = fileInfo?.audio?.title || parsed.name;
            if (!artist) {
                artist = fileInfo?.audio?.artist || parsed.artist;
            }
        }
    }
    if (!artist)
        artist = fileInfo?.audio?.artist;
    if (!artist) {
        artist = '未知歌手';
    }
    if (!mvName) {
        mvName = '未知MV';
    }
    const requestData = {
        url: data.filePath,
        name: mvName,
        artist: artist,
        cover: data.cover || '',
        fileSize: data.fileSize || fileInfo?.size || 0,
        duration: data.duration || fileInfo?.duration || 0,
        quality: data.quality ?? getQualityValue(fileInfo?.quality)
    };
    return request<LocalMusicVO>({
        url: '/local-music/mv/add-manual',
        method: 'POST',
        data: requestData
    });
}
export async function addManualSong(data: {
    filePath?: string;
    name?: string;
    artist?: string;
    album?: string;
    fileSize?: number;
    duration?: number;
    quality?: number;
}) {
    let songName = data.name;
    let artist = data.artist;
    const fileInfo = data.filePath && !data.filePath.startsWith('local:') && (data.filePath.includes(':') || data.filePath.includes('\\'))
        ? await getLocalFileInfo(data.filePath)
        : null;
    if (data.filePath) {
        const filename = extractFilename(data.filePath);
        if (!songName || songName.includes('\\') || songName.includes('/')) {
            const parsed = parseFileName(filename);
            songName = fileInfo?.audio?.title || parsed.name;
            if (!artist) {
                artist = fileInfo?.audio?.artist || parsed.artist;
            }
        }
    }
    if (!artist)
        artist = fileInfo?.audio?.artist;
    if (!artist) {
        artist = '未知歌手';
    }
    if (!songName) {
        songName = '未知歌曲';
    }
    const requestData = {
        url: data.filePath,
        name: songName,
        artist: artist,
        cover: data.album || fileInfo?.audio?.album || '',
        fileSize: data.fileSize || fileInfo?.size || 0,
        duration: data.duration || fileInfo?.duration || 0,
        quality: data.quality ?? getQualityValue(fileInfo?.quality)
    };
    return request<LocalMusicVO>({
        url: '/local-music/song/add-manual',
        method: 'POST',
        data: requestData
    });
}
