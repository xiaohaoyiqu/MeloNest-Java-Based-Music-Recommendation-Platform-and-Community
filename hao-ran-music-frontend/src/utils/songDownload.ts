import { downloadSong, type AudioQuality } from '@/api/songDownload';
export interface DownloadableSong {
    id: string | number;
    name: string;
    artistNames?: string;
    artist?: string;
}
function safeFilenamePart(value: string) {
    return value.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').trim() || '未知';
}
export async function saveSongDownload(song: DownloadableSong, quality: AudioQuality = 'standard') {
    const response = await downloadSong(song.id, quality);
    if (!(response.data instanceof Blob)) {
        throw new Error('歌曲下载响应格式不正确');
    }
    const artist = safeFilenamePart(song.artistNames || song.artist || '未知歌手');
    const name = safeFilenamePart(song.name);
    const filename = `${artist} - ${name}.mp3`;
    const objectUrl = URL.createObjectURL(response.data);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = filename;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
    return filename;
}
