import type { SongInfo } from './song';
export type { AlbumInfo, AlbumSimple, IPage } from '@/api/album';
export interface AlbumSong extends SongInfo {
    title?: string;
    artist?: string;
    url?: string;
}
