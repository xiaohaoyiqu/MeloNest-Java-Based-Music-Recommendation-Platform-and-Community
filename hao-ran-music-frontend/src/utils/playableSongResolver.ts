import { getSongById } from '@/api/song';
import { getLocalFileUrl } from '@/api/localProxy';
import type { SongInfo } from '@/types/song';
type SongSource = Partial<SongInfo> & {
    songId?: string | number;
    songName?: string;
    artistName?: string;
    playUrl?: string;
};
const pendingOnlineSongs = new Map<string, Promise<SongInfo | null>>();
function text(value: unknown): string | undefined {
    if (typeof value !== 'string')
        return undefined;
    const normalized = value.trim();
    return normalized || undefined;
}
function normalizeSong(source: SongSource): SongInfo | null {
    const id = source.songId ?? source.id;
    if (id == null || String(id).trim() === '')
        return null;
    return {
        ...source,
        id,
        name: text(source.name) || text(source.songName) || '未命名歌曲',
        artistNames: text(source.artistNames) || text(source.artistName),
        cover: text(source.cover) || text(source.coverUrl),
        urlStandard: text(source.urlStandard) || text(source.playUrl),
        urlHigh: text(source.urlHigh),
        urlLossless: text(source.urlLossless)
    };
}
function hasPlayableUrl(song: SongInfo): boolean {
    return Boolean(song.urlStandard || song.urlHigh || song.urlLossless || song.blobUrl || song.localPath);
}
function needsLocalProxy(path?: string): boolean {
    if (!path || path.startsWith('local:') || /^https?:\/\//i.test(path) || path.startsWith('blob:'))
        return false;
    return path.includes(':') || path.includes('\\');
}
async function resolveLocalSong(song: SongInfo): Promise<SongInfo | null> {
    const sourcePath = text(song.filePath) || text(song.localPath) || text(song.urlStandard);
    if (!sourcePath)
        return null;
    const resolvedUrl = needsLocalProxy(sourcePath)
        ? await getLocalFileUrl(sourcePath)
        : sourcePath;
    if (!resolvedUrl)
        return null;
    return {
        ...song,
        source: 'local',
        isLocal: true,
        filePath: song.filePath || sourcePath,
        localPath: resolvedUrl,
        urlStandard: resolvedUrl
    };
}
export async function resolvePlayableSong(source: SongSource): Promise<SongInfo | null> {
    const song = normalizeSong(source);
    if (!song)
        return null;
    const local = song.isLocal === true || song.source === 'local';
    if (local)
        return resolveLocalSong(song);
    if (hasPlayableUrl(song))
        return song;
    const key = String(song.id);
    const pending = pendingOnlineSongs.get(key);
    if (pending)
        return pending;
    const hydration = (async () => {
        try {
            const response = await getSongById(song.id, undefined);
            const hydrated = normalizeSong({ ...song, ...(response.data || {}) });
            return hydrated && hasPlayableUrl(hydrated) ? hydrated : null;
        }
        catch {
            return null;
        }
    })();
    pendingOnlineSongs.set(key, hydration);
    try {
        return await hydration;
    }
    finally {
        if (pendingOnlineSongs.get(key) === hydration)
            pendingOnlineSongs.delete(key);
    }
}
