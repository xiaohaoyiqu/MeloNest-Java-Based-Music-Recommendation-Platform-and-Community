import { request } from '@/utils/request';
import type { ExactCount } from '@/utils/exactCount';
export interface MVInfo {
    id: string | number;
    uuid?: string;
    name: string;
    cover?: string;
    description?: string;
    artistId?: string | number;
    artistName?: string;
    artistNames?: string;
    isFavorited?: boolean;
    isLiked?: boolean;
    url360p?: string;
    url720p?: string;
    url1080p?: string;
    url2160p?: string;
    songId?: string | number;
    songName?: string;
    songLanguage?: string;
    artistAvatar?: string;
    duration?: number;
    playCount?: ExactCount;
    favoriteCount?: ExactCount;
    shareCount?: ExactCount;
    commentCount?: ExactCount;
    allowComment?: number | boolean;
    publishDate?: string;
    publishTime?: string;
    createTime?: string;
    mainType?: string;
    subTypes?: string[];
    isFavorite?: boolean;
    urlStandard?: string;
    urlHigh?: string;
    urlSuper?: string;
    url360?: string;
    url_360p?: string;
    url_360?: string;
    url_720p?: string;
    url_720?: string;
    url_1080p?: string;
    url_1080?: string;
    url_2160p?: string;
    url_2160?: string;
    url720?: string;
    url1080?: string;
    url2160?: string;
}
export type MVPlaybackQuality = 'standard' | 'high' | 'super' | '360p' | '720p' | '1080p' | '2160p';
export interface MVSimple {
    id: string | number;
    name: string;
    cover?: string;
    artistName: string;
    artistNames?: string;
    isFavorited?: boolean;
    isLiked?: boolean;
    url720p?: string;
    url1080p?: string;
    songId?: string | number;
    songName?: string;
    songLanguage?: string;
    artistId?: string | number;
    duration: number;
    playCount: ExactCount;
    publishDate: string;
}
export function getMVById(id: string | number, _userId?: string | number, options?: {
    signal?: AbortSignal;
}) {
    return request<MVInfo>({
        url: `/mv/info/${id}`,
        method: 'GET',
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export function getHotMVs(type?: string, limit: number = 20, _userId?: string | number) {
    return request<MVInfo[]>({
        url: '/mv/hot',
        method: 'GET',
        params: { type, limit }
    });
}
export function getNewMVs(page: number = 1, size: number = 20, _userId?: string | number) {
    return request<MVInfo[]>({
        url: '/mv/newest',
        method: 'GET',
        params: { page, size }
    });
}
export function getArtistMVs(artistId: string | number, page: number = 1, size: number = 20, _userId?: string | number) {
    return request<{
        records: MVInfo[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: `/mv/artist/${artistId}`,
        method: 'GET',
        params: { page, size }
    });
}
export function favoriteMV(mvId: string | number) {
    return request<void>({
        url: `/mv/favorite/${mvId}`,
        method: 'POST'
    });
}
export function unfavoriteMV(mvId: string | number) {
    return request<void>({
        url: `/mv/favorite/${mvId}`,
        method: 'DELETE'
    });
}
export function recordMVPlay(mvId: string | number) {
    return request<void>({
        url: `/mv/play/${mvId}`,
        method: 'POST'
    });
}
export function getMVUrl(mvId: string | number, quality: MVPlaybackQuality = 'standard') {
    return request<string>({
        url: `/mv/url/${mvId}`,
        method: 'GET',
        params: { quality }
    });
}
export function getMVPreviewUrl(mvId: string | number) {
    return request<string>({
        url: `/mv/preview/${mvId}`,
        method: 'GET'
    });
}
export function downloadMVFile(mvId: string | number, quality: string) {
    return request<any>({
        url: `/mv/download/${mvId}`,
        method: 'GET',
        params: { quality },
        responseType: 'blob'
    });
}
export function getFavoriteMVs() {
    return request<MVInfo[]>({
        url: '/mv/favorites',
        method: 'GET'
    });
}
export function getSimilarMVs(mvId: string | number, limit: number = 10) {
    return request<MVInfo[]>({
        url: `/mv/${mvId}/similar`,
        method: 'GET',
        params: { limit }
    });
}
export function getArtistOtherMVs(mvId: string | number, limit: number = 10) {
    return request<MVInfo[]>({
        url: `/mv/${mvId}/artist-mvs`,
        method: 'GET',
        params: { limit }
    });
}
export interface GetMVListParams {
    page?: number;
    size?: number;
    area?: string;
    genre?: string;
    sortBy?: string;
    keyword?: string;
    language?: string;
    publishYear?: number;
    publishDateStart?: string;
    publishDateEnd?: string;
    minDuration?: number;
    maxDuration?: number;
    quality?: '360p' | '720p' | '1080p' | '4k';
    binding?: 'bound' | 'other';
    albumType?: 'single' | 'ep' | 'album' | 'live' | 'compilation' | 'other';
}
export interface MVDownloadInfo {
    mvId: string | number;
    mvName: string;
    artistNames: string;
    url: string;
    size: number;
    quality: string;
    sizes?: {
        '360p'?: number;
        '720p'?: number;
        '1080p'?: number;
    };
}
export const mvApi = {
    getMVById: (id: string | number, _userId?: string | number) => {
        return request<MVInfo>({
            url: `/mv/info/${id}`,
            method: 'GET'
        });
    },
    getHotMVs: (type?: string, limit: number = 20, _userId?: string | number) => {
        return request<MVInfo[]>({
            url: '/mv/hot',
            method: 'GET',
            params: { type, limit }
        });
    },
    getNewMVs: (page: number = 1, size: number = 20, _userId?: string | number) => {
        return request<MVInfo[]>({
            url: '/mv/newest',
            method: 'GET',
            params: { page, size }
        });
    },
    getArtistMVs: (artistId: string | number, page: number = 1, size: number = 20, _userId?: string | number) => {
        return request<{
            records: MVInfo[];
            total: number;
            current: number;
            pages: number;
        }>({
            url: `/mv/artist/${artistId}`,
            method: 'GET',
            params: { page, size }
        });
    },
    getMVList: (params: GetMVListParams, options: {
        signal?: AbortSignal;
    } = {}) => {
        return request<any>({
            url: '/mv/page',
            method: 'GET',
            params,
            ...(options.signal ? { signal: options.signal } : {})
        });
    },
    favoriteMV: (mvId: string | number) => {
        return request<void>({
            url: `/mv/favorite/${mvId}`,
            method: 'POST'
        });
    },
    unfavoriteMV: (mvId: string | number) => {
        return request<void>({
            url: `/mv/favorite/${mvId}`,
            method: 'DELETE'
        });
    },
    recordMVPlay: (mvId: string | number) => {
        return request<void>({
            url: `/mv/play/${mvId}`,
            method: 'POST'
        });
    },
    getMVUrl: (mvId: string | number, quality: MVPlaybackQuality = 'standard') => {
        return request<string>({
            url: `/mv/url/${mvId}`,
            method: 'GET',
            params: { quality }
        });
    },
    getMVPreviewUrl: (mvId: string | number) => {
        return request<string>({
            url: `/mv/preview/${mvId}`,
            method: 'GET'
        });
    },
    downloadMVFile,
    getFavoriteMVs: () => {
        return request<MVInfo[]>({
            url: '/mv/favorites',
            method: 'GET'
        });
    },
    getDownloadInfo: (mvId: string | number, quality: string = '720') => {
        return request<MVDownloadInfo>({
            url: `/mv/download/${mvId}/info`,
            method: 'GET',
            params: { quality }
        });
    },
    getSimilarMVs: (mvId: string | number, limit: number = 10) => {
        return request<MVInfo[]>({
            url: `/mv/${mvId}/similar`,
            method: 'GET',
            params: { limit }
        });
    },
    getArtistOtherMVs: (mvId: string | number, limit: number = 10) => {
        return request<MVInfo[]>({
            url: `/mv/${mvId}/artist-mvs`,
            method: 'GET',
            params: { limit }
        });
    }
};
