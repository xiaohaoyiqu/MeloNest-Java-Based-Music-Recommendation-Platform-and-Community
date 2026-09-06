export type { PlaylistInfo } from '@/api/playlist';
import type { ExactCount } from '@/utils/exactCount';
export interface PlaylistSong {
    id: string | number;
    title?: string;
    name?: string;
    artist?: string;
    artistNames?: string;
    album?: string;
    albumName?: string;
    duration: number;
    url?: string;
    cover?: string;
    coverUrl?: string;
}
export interface PlaylistDetail {
    id: string | number;
    name: string;
    description?: string;
    cover?: string;
    songCount: number;
    playCount: ExactCount;
    isPublic: boolean | number;
    creatorName: string;
    creatorAvatar: string;
    songs: PlaylistSong[];
    allowDownload?: boolean | number;
    allowComment?: boolean | number;
    allowShare?: boolean | number;
}
export interface PlaylistFormData {
    name: string;
    description?: string;
    intro?: string;
    cover?: string;
    isPublic?: boolean | number;
    category?: string;
    language?: string;
    priority?: number;
    tags?: string[];
    playCount?: ExactCount;
    favoriteCount?: ExactCount;
    enablePaid?: boolean;
    monthlyPrice?: number;
}
