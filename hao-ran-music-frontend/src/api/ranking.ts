import { request } from '@/utils/request';
import type { SongInfo } from './song';
import type { ArtistSimple } from './artist';
import type { AlbumSimple } from './album';
export interface MVSimple {
    id: string | number;
    name: string;
    cover?: string;
    playCount?: number;
    duration?: number;
    artistNames?: string;
    songLanguage?: string;
    publishDate?: string;
}
export interface RankingOverview {
    hotSongs: SongInfo[];
    newSongs: SongInfo[];
    hotAlbums: AlbumSimple[];
    hotArtists: ArtistSimple[];
    hotMVs?: MVSimple[];
}
export interface ArtistRankingByCategory {
    chinese_male: ArtistSimple[];
    chinese_female: ArtistSimple[];
    western: ArtistSimple[];
    asian: ArtistSimple[];
}
export interface CreatorSimple {
    id: string | number;
    name: string;
    avatar?: string;
    workCount?: number;
    songCount?: number;
    playCount?: number;
    fansCount?: number;
}
export function getSongRanking(type: 'hot' | 'new' | 'rise' = 'hot', limit = 100) {
    return request<SongInfo[]>({
        url: '/ranking/songs',
        method: 'GET',
        params: { type, limit }
    });
}
export function getAlbumRanking(type: 'hot' | 'new' = 'hot', limit = 50) {
    return request<AlbumSimple[]>({
        url: '/ranking/albums',
        method: 'GET',
        params: { type, limit }
    });
}
export function getArtistRanking(limit = 50) {
    return request<ArtistSimple[]>({
        url: '/ranking/artists',
        method: 'GET',
        params: { limit }
    });
}
export function getMvRanking(type: 'hot' | 'new' = 'hot', limit = 50) {
    return request<MVSimple[]>({
        url: '/ranking/mvs',
        method: 'GET',
        params: { type, limit }
    });
}
export function getGenreRanking(genre: string, limit = 50) {
    return request<SongInfo[]>({
        url: `/ranking/genre/${genre}`,
        method: 'GET',
        params: { limit }
    });
}
export function getRankingOverview() {
    return request<RankingOverview>({
        url: '/ranking/overview',
        method: 'GET'
    });
}
export function getArtistRankingByCategory(limit = 10) {
    return request<ArtistRankingByCategory>({
        url: '/ranking/artists/by-category',
        method: 'GET',
        params: { limit }
    });
}
export function getCreatorRanking(type: 'hot' | 'new' | 'active' = 'hot', limit = 50) {
    return request<CreatorSimple[]>({
        url: '/ranking/creators',
        method: 'GET',
        params: { type, limit }
    });
}
export function getLanguageRanking(language: 'zh' | 'en' | 'ko' | 'ja' = 'zh', limit = 50) {
    return request<SongInfo[]>({
        url: '/ranking/language-songs',
        method: 'GET',
        params: { language, limit }
    });
}
export const rankingApi = {
    getSongRanking,
    getAlbumRanking,
    getArtistRanking,
    getMvRanking,
    getGenreRanking,
    getRankingOverview,
    getCreatorRanking,
    getLanguageRanking,
    getArtistRankingByCategory
};
