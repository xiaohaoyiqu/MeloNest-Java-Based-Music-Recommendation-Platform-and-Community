import type { SongInfo } from '@/api/song';
export function hasPlayResource(song: SongInfo): boolean {
    if (!song)
        return false;
    const hasResolvableId = song.id !== undefined && song.id !== null && String(song.id).trim() !== '';
    return !!(song.urlStandard ||
        song.urlHigh ||
        song.urlLossless ||
        song.urlHires ||
        song.urlMaster ||
        song.urlInstrumental ||
        isLocalMusic(song) ||
        hasResolvableId);
}
export function filterPlayableSongs(songs: SongInfo[]): SongInfo[] {
    if (!songs)
        return [];
    return songs.filter(hasPlayResource);
}
export function countPlayableSongs(songs: SongInfo[]): number {
    if (!songs)
        return 0;
    return songs.filter(hasPlayResource).length;
}
export function isLocalMusic(song: SongInfo): boolean {
    return !!song.isLocal || song.source === 'local' || song.resourceType === 0 || song.resourceType === 1;
}
