export interface SongDisplaySource {
    name?: string;
    title?: string;
    artistNames?: string;
    artist?: string;
    albumName?: string;
    album?: string;
    mainGenre?: string;
    mainType?: string;
    versionType?: string | number;
    versionName?: string;
    language?: string;
}
export interface SongSecondaryOptions {
    includeAlbum?: boolean;
    albumPrefix?: string;
    includeGenreFallback?: boolean;
}
function cleanText(value?: string): string {
    return typeof value === 'string' ? value.trim() : '';
}
const emptyMetadataValues = new Set(['non', 'none', 'null', 'undefined', 'unknown', 'n/a', 'na', '-']);
const versionLabels: Record<string, string> = {
    original: '原版',
    live: '现场版',
    remix: '混音版',
    cover: '翻唱版',
    acoustic: '不插电版',
    instrumental: '纯音乐版',
    demo: '演示版'
};
const languageLabels: Record<string, string> = {
    zh: '中文',
    en: '英文',
    ja: '日语',
    ko: '韩语',
    fr: '法语',
    de: '德语',
    es: '西班牙语',
    it: '意大利语',
    pt: '葡萄牙语',
    ru: '俄语',
    th: '泰语',
    other: '其他'
};
function cleanMetadataValue(value?: string | number): string {
    const text = value == null ? '' : String(value).trim();
    return emptyMetadataValues.has(text.toLowerCase()) ? '' : text;
}
export function getSongDisplayName(song: SongDisplaySource): string {
    return cleanText(song.name) || cleanText(song.title) || '未命名歌曲';
}
export function getSongArtistName(song: SongDisplaySource): string {
    return cleanText(song.artistNames) || cleanText(song.artist) || '未知歌手';
}
export function getSongVersionLabel(song: SongDisplaySource): string {
    const versionName = cleanMetadataValue(song.versionName);
    if (versionName)
        return versionName;
    const versionType = cleanMetadataValue(song.versionType);
    return versionLabels[versionType.toLowerCase()] || versionType;
}
export function getSongLanguageLabel(language?: string): string {
    const value = cleanMetadataValue(language);
    return languageLabels[value.toLowerCase()] || value;
}
export function formatSongSecondaryInfo(song: SongDisplaySource, options: SongSecondaryOptions = {}): string {
    const { includeAlbum = true, albumPrefix = '', includeGenreFallback = true } = options;
    const parts = [getSongArtistName(song)];
    const album = cleanText(song.albumName) || cleanText(song.album);
    if (includeAlbum && album) {
        parts.push(`${albumPrefix}《${album}》`);
    }
    else if (includeGenreFallback) {
        const genre = cleanText(song.mainGenre) || cleanText(song.mainType);
        if (genre)
            parts.push(genre);
    }
    return parts.join(' · ');
}
