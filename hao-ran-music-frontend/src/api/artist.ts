import { request } from '@/utils/request';
import type { SongInfo } from './song';
import type { AlbumSimple, IPage } from './album';
export interface ArtistInfo {
    id: string | number;
    uuid?: string;
    name: string;
    avatar: string;
    cover: string;
    description?: string;
    gender?: number;
    birthDate?: string;
    birthPlace?: string;
    type?: string | number;
    artistKind?: 'person' | 'group' | 'unknown';
    artistKindName?: string;
    profileSource?: 'town_creator' | 'catalog';
    profileSourceName?: string;
    mainType?: string;
    subTypes?: string[];
    transNames?: string;
    songCount?: number;
    albumCount?: number;
    mvCount?: number;
    playCount?: number;
    commentCount?: number;
    fansCount?: number;
    isFollow?: boolean;
    isCreator?: boolean | number;
    createTime?: string;
    hotSongs?: SongInfo[];
    songs?: SongInfo[];
    albums?: AlbumSimple[];
}
export interface ArtistSimple {
    id: string | number;
    name: string;
    avatar: string;
    description?: string;
    songCount?: number;
    albumCount?: number;
    playCount?: number;
    commentCount?: number;
    fansCount?: number;
}
export function getArtistById(id: string | number, _userId?: string | number, options?: {
    signal?: AbortSignal;
}) {
    return request<ArtistInfo>({
        url: `/artist/info/${id}`,
        method: 'GET',
        ...(options?.signal ? { signal: options.signal } : {})
    });
}
export function getHotArtists(type?: string, limit: number = 20, _userId?: string | number) {
    return request<ArtistInfo[]>({
        url: '/artist/hot',
        method: 'GET',
        params: { type, limit }
    });
}
export function getNewArtists(page: number = 1, size: number = 20) {
    return request<IPage<ArtistInfo>>({
        url: '/artist/page',
        method: 'GET',
        params: { page, size, sortBy: 'time' }
    });
}
export function getArtistSongs(artistId: string | number, _page: number = 1, size: number = 20, _userId?: string | number) {
    return request<SongInfo[]>({
        url: `/artist/${artistId}/songs`,
        method: 'GET',
        params: { limit: size }
    });
}
export function followArtist(artistId: string | number) {
    return request<void>({
        url: `/artist/follow/${artistId}`,
        method: 'POST'
    });
}
export function unfollowArtist(artistId: string | number) {
    return request<void>({
        url: `/artist/follow/${artistId}`,
        method: 'DELETE'
    });
}
export function getArtistList(area?: string, _userId?: string | number) {
    return request<ArtistInfo[]>({
        url: '/artist/list',
        method: 'GET',
        params: { area }
    });
}
export function getArtistPage(params: {
    page: number;
    size: number;
    area?: string;
}, options: {
    signal?: AbortSignal;
} = {}) {
    return request<{
        records: ArtistInfo[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/artist/page',
        method: 'GET',
        params,
        ...(options.signal ? { signal: options.signal } : {})
    });
}
export function getSimilarArtists(artistId: string | number, limit: number = 10) {
    return request<ArtistInfo[]>({
        url: `/artist/${artistId}/similar`,
        method: 'GET',
        params: { limit }
    });
}
export const artistApi = {
    getArtistById: (id: string | number, _userId?: string | number) => {
        return request<ArtistInfo>({
            url: `/artist/info/${id}`,
            method: 'GET'
        });
    },
    getHotArtists: (type?: string, limit: number = 20, _userId?: string | number) => {
        return request<ArtistInfo[]>({
            url: '/artist/hot',
            method: 'GET',
            params: { type, limit }
        });
    },
    getNewArtists: (page: number = 1, size: number = 20) => {
        return request<IPage<ArtistInfo>>({
            url: '/artist/page',
            method: 'GET',
            params: { page, size, sortBy: 'time' }
        });
    },
    getArtistSongs: (artistId: string | number, page: number = 1, size: number = 20, _userId?: string | number) => {
        return request<SongInfo[]>({
            url: `/artist/${artistId}/songs`,
            method: 'GET',
            params: { page, size }
        });
    },
    followArtist: (artistId: string | number) => {
        return request<void>({
            url: `/artist/follow/${artistId}`,
            method: 'POST'
        });
    },
    unfollowArtist: (artistId: string | number) => {
        return request<void>({
            url: `/artist/follow/${artistId}`,
            method: 'DELETE'
        });
    },
    getArtistList: (area?: string, _userId?: string | number) => {
        return request<ArtistInfo[]>({
            url: '/artist/list',
            method: 'GET',
            params: { area }
        });
    },
    getArtistPage: (params: {
        page: number;
        size: number;
        area?: string;
    }) => {
        return request<{
            records: ArtistInfo[];
            total: number;
            current: number;
            pages: number;
        }>({
            url: '/artist/page',
            method: 'GET',
            params
        });
    },
    getSimilarArtists: (artistId: string | number, limit: number = 10) => {
        return request<ArtistInfo[]>({
            url: `/artist/${artistId}/similar`,
            method: 'GET',
            params: { limit }
        });
    }
};
