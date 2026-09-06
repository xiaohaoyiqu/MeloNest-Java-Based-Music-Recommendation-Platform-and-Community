import { request } from '@/utils/request';
import type { SongInfo } from './song';
import type { ExactCount } from '@/utils/exactCount';
export interface IPage<T> {
    records: T[];
    total: number;
    size: number;
    current: number;
    pages: number;
}
export interface AlbumInfo {
    id: string | number;
    name: string;
    originalName?: string;
    artistId?: string | number;
    artistIds?: string;
    artistNames?: string;
    artistAvatar?: string;
    cover: string;
    description?: string;
    releaseDate?: string;
    country?: string;
    province?: string;
    region?: string;
    company?: string;
    type?: string;
    genres?: string;
    language?: string;
    songCount?: number;
    playCount?: ExactCount;
    favoriteCount?: ExactCount;
    commentCount?: ExactCount;
    status?: number;
    createTime?: string;
    isFavorite?: boolean;
    songs?: SongInfo[];
    publishDate?: string;
    mainType?: string;
    tags?: string[];
    uuid?: string;
    allowDownload?: number;
    allowComment?: number;
    allowShare?: number;
}
export interface AlbumSimple {
    id: string | number;
    name: string;
    cover: string;
    artistNames?: string;
    coverUrl?: string;
    playCount?: ExactCount;
    artistId?: string | number;
    releaseDate?: string;
    songCount?: number;
    publishDate?: string;
    artistName?: string;
}
export function getAlbumById(id: string | number, _userId?: string | number, options?: {
    signal?: AbortSignal;
}) {
    return request<AlbumInfo>({
        url: `/album/info/${id}`,
        method: 'GET',
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export function getNewAlbums(page: number = 1, size: number = 20, _userId?: string | number) {
    return request<IPage<AlbumInfo>>({
        url: '/album/new',
        method: 'GET',
        params: { page, size }
    });
}
export function getHotAlbums(type?: string, limit: number = 20, _userId?: string | number) {
    return request<AlbumInfo[]>({
        url: '/album/hot',
        method: 'GET',
        params: { type, limit }
    });
}
export function getArtistAlbums(artistId: string | number, page: number = 1, size: number = 20, _userId?: string | number) {
    return request<AlbumInfo[]>({
        url: `/album/artist/${artistId}`,
        method: 'GET',
        params: { page, size }
    });
}
export function favoriteAlbum(albumId: string | number) {
    return request<void>({
        url: `/album/favorite/${albumId}`,
        method: 'POST'
    });
}
export function unfavoriteAlbum(albumId: string | number) {
    return request<void>({
        url: `/album/favorite/${albumId}`,
        method: 'DELETE'
    });
}
export function getFavoriteAlbums() {
    return request<AlbumInfo[]>({
        url: '/album/favorites',
        method: 'GET'
    });
}
export function getAlbumSongs(albumId: string | number, _userId?: string | number) {
    return request<SongInfo[]>({
        url: `/album/${albumId}/songs`,
        method: 'GET'
    });
}
export async function searchAlbums(params: {
    keyword?: string;
    page?: number;
    size?: number;
}) {
    const response = await request<{
        albums?: AlbumSimple[];
        total?: number;
        page?: number;
        size?: number;
        pages?: number;
    }>({
        url: '/search/albums',
        method: 'GET',
        params
    });
    const data = response?.data;
    return {
        ...response,
        data: {
            records: data?.albums || [],
            total: data?.total || 0,
            current: data?.page || params.page || 1,
            size: data?.size || params.size || 20,
            pages: data?.pages || 0
        } as IPage<AlbumSimple>
    };
}
export function getSimilarAlbums(albumId: string | number, limit: number = 10) {
    return request<AlbumInfo[]>({
        url: `/album/${albumId}/similar`,
        method: 'GET',
        params: { limit }
    });
}
export function getArtistOtherAlbums(albumId: string | number, limit: number = 10) {
    return request<AlbumInfo[]>({
        url: `/album/${albumId}/artist-albums`,
        method: 'GET',
        params: { limit }
    });
}
export const albumApi = {
    getAlbumList: (params: {
        page: number;
        size: number;
        keyword?: string;
        area?: string;
        genre?: string;
        language?: string;
        sortBy?: string;
        userId?: string | number;
    }, options: {
        signal?: AbortSignal;
    } = {}) => {
        return request<{
            records: AlbumInfo[];
            total: number;
            size: number;
            current: number;
            pages: number;
        }>({
            url: '/album/page',
            method: 'GET',
            params,
            ...(options.signal ? { signal: options.signal } : {})
        });
    },
    getAlbumById: (id: string | number, _userId?: string | number) => {
        return request<AlbumInfo>({
            url: `/album/info/${id}`,
            method: 'GET'
        });
    },
    getNewAlbums: (page: number = 1, size: number = 20, _userId?: string | number) => {
        return request<IPage<AlbumInfo>>({
            url: '/album/new',
            method: 'GET',
            params: { page, size }
        });
    },
    getHotAlbums: (type?: string, limit: number = 20, _userId?: string | number) => {
        return request<AlbumInfo[]>({
            url: '/album/hot',
            method: 'GET',
            params: { type, limit }
        });
    },
    getArtistAlbums: (artistId: string | number, page: number = 1, size: number = 20, _userId?: string | number) => {
        return request<AlbumInfo[]>({
            url: `/album/artist/${artistId}`,
            method: 'GET',
            params: { page, size }
        });
    },
    favoriteAlbum: (albumId: string | number) => {
        return request<void>({
            url: `/album/favorite/${albumId}`,
            method: 'POST'
        });
    },
    unfavoriteAlbum: (albumId: string | number) => {
        return request<void>({
            url: `/album/favorite/${albumId}`,
            method: 'DELETE'
        });
    },
    getFavoriteAlbums: () => {
        return request<AlbumInfo[]>({
            url: '/album/favorites',
            method: 'GET'
        });
    },
    searchAlbums,
    getSimilarAlbums: (albumId: string | number, limit: number = 10) => {
        return request<AlbumInfo[]>({
            url: `/album/${albumId}/similar`,
            method: 'GET',
            params: { limit }
        });
    },
    getArtistOtherAlbums: (albumId: string | number, limit: number = 10) => {
        return request<AlbumInfo[]>({
            url: `/album/${albumId}/artist-albums`,
            method: 'GET',
            params: { limit }
        });
    }
};
