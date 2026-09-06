import type { SongInfo } from '@/api/song';
import type { AlbumInfo } from '@/api/album';
import type { PlaylistInfo } from '@/api/playlist';
export interface PermissionConfig {
    allowDownload?: number;
    allowComment?: number;
    allowShare?: number;
}
export interface DownloadPermission {
    allowed: boolean;
    reason?: string;
}
export interface CommentPermission {
    allowed: boolean;
    reason?: string;
    showClosedTip: boolean;
}
export interface SharePermission {
    allowed: boolean;
    reason?: string;
}
export function checkSongDownloadPermission(song: SongInfo, fromPlaylist?: PlaylistInfo, fromAlbum?: AlbumInfo): DownloadPermission {
    if (song.allowDownload === 0) {
        return { allowed: false, reason: '歌曲作者已禁止下载' };
    }
    if (fromAlbum && fromAlbum.allowDownload === 0) {
        return { allowed: false, reason: '专辑作者已禁止下载' };
    }
    if (fromPlaylist && fromPlaylist.allowDownload === 0) {
        return { allowed: false, reason: '歌单作者已禁止下载' };
    }
    return { allowed: true };
}
export function checkSongCommentPermission(song: SongInfo, fromPlaylist?: PlaylistInfo, fromAlbum?: AlbumInfo): CommentPermission {
    if (song.allowComment === 0) {
        return { allowed: false, reason: '歌曲作者已关闭评论', showClosedTip: true };
    }
    if (fromAlbum && fromAlbum.allowComment === 0) {
        return { allowed: false, reason: '专辑作者已关闭评论', showClosedTip: true };
    }
    if (fromPlaylist && fromPlaylist.allowComment === 0) {
        return { allowed: false, reason: '歌单作者已关闭评论', showClosedTip: true };
    }
    return { allowed: true, showClosedTip: false };
}
export function checkSongSharePermission(song: SongInfo, fromPlaylist?: PlaylistInfo, fromAlbum?: AlbumInfo): SharePermission {
    if (song.allowShare === 0) {
        return { allowed: false, reason: '歌曲作者已禁止分享' };
    }
    if (fromAlbum && fromAlbum.allowShare === 0) {
        return { allowed: false, reason: '专辑作者已禁止分享' };
    }
    if (fromPlaylist) {
        if (fromPlaylist.allowShare === 0) {
            return { allowed: false, reason: '歌单作者已禁止分享' };
        }
        if (fromPlaylist.isPublic === 0) {
            return { allowed: false, reason: '私密歌单不允许分享' };
        }
    }
    return { allowed: true };
}
export function checkAlbumDownloadPermission(album: AlbumInfo): DownloadPermission {
    if (album.allowDownload === 0) {
        return { allowed: false, reason: '专辑作者已禁止下载' };
    }
    return { allowed: true };
}
export function checkAlbumCommentPermission(album: AlbumInfo): CommentPermission {
    if (album.allowComment === 0) {
        return { allowed: false, reason: '专辑作者已关闭评论', showClosedTip: true };
    }
    return { allowed: true, showClosedTip: false };
}
export function checkAlbumSharePermission(album: AlbumInfo): SharePermission {
    if (album.allowShare === 0) {
        return { allowed: false, reason: '专辑作者已禁止分享' };
    }
    return { allowed: true };
}
export function checkPlaylistDownloadPermission(playlist: PlaylistInfo): DownloadPermission {
    if (playlist.allowDownload === 0) {
        return { allowed: false, reason: '歌单作者已禁止下载' };
    }
    return { allowed: true };
}
export function checkPlaylistCommentPermission(playlist: PlaylistInfo): CommentPermission {
    if (playlist.allowComment === 0) {
        return { allowed: false, reason: '歌单作者已关闭评论', showClosedTip: true };
    }
    return { allowed: true, showClosedTip: false };
}
export function checkPlaylistSharePermission(playlist: PlaylistInfo): SharePermission {
    if (playlist.allowShare === 0) {
        return { allowed: false, reason: '歌单作者已禁止分享' };
    }
    if (playlist.isPublic === 0) {
        return { allowed: false, reason: '私密歌单不允许分享' };
    }
    return { allowed: true };
}
export class PermissionChecker {
    constructor(private song?: SongInfo, private playlist?: PlaylistInfo, private album?: AlbumInfo) { }
    canDownload(): DownloadPermission {
        if (this.song) {
            return checkSongDownloadPermission(this.song, this.playlist, this.album);
        }
        if (this.album) {
            return checkAlbumDownloadPermission(this.album);
        }
        if (this.playlist) {
            return checkPlaylistDownloadPermission(this.playlist);
        }
        return { allowed: true };
    }
    canComment(): CommentPermission {
        if (this.song) {
            return checkSongCommentPermission(this.song, this.playlist, this.album);
        }
        if (this.album) {
            return checkAlbumCommentPermission(this.album);
        }
        if (this.playlist) {
            return checkPlaylistCommentPermission(this.playlist);
        }
        return { allowed: true, showClosedTip: false };
    }
    canShare(): SharePermission {
        if (this.song) {
            return checkSongSharePermission(this.song, this.playlist, this.album);
        }
        if (this.album) {
            return checkAlbumSharePermission(this.album);
        }
        if (this.playlist) {
            return checkPlaylistSharePermission(this.playlist);
        }
        return { allowed: true };
    }
}
