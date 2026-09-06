import { logger as frontendLogger } from '@/utils/logger';
export interface AudioFileInfo {
    name: string;
    size: number;
    duration: number;
    quality: 'standard' | 'high' | 'lossless';
}
function getQualityFromFile(filename: string): 'standard' | 'high' | 'lossless' {
    const ext = filename.split('.').pop()?.toLowerCase() || '';
    const losslessFormats = ['flac', 'wav', 'ape', 'wv', 'tta', 'aiff', 'aif', 'dsf', 'dff'];
    if (losslessFormats.includes(ext)) {
        return 'lossless';
    }
    if (ext === 'm4a') {
        return 'high';
    }
    return 'standard';
}
export async function getAudioDuration(file: File): Promise<number> {
    return new Promise((resolve, reject) => {
        const audio = new Audio();
        const url = URL.createObjectURL(file);
        audio.addEventListener('loadedmetadata', () => {
            URL.revokeObjectURL(url);
            resolve(audio.duration);
        });
        audio.addEventListener('error', (e) => {
            URL.revokeObjectURL(url);
            resolve(0);
        });
        setTimeout(() => {
            if (audio.duration) {
                URL.revokeObjectURL(url);
                resolve(audio.duration);
            }
            else {
                URL.revokeObjectURL(url);
                resolve(0);
            }
        }, 5000);
        audio.src = url;
    });
}
export async function getVideoDuration(file: File): Promise<number> {
    return new Promise((resolve) => {
        const video = document.createElement('video');
        const url = URL.createObjectURL(file);
        video.addEventListener('loadedmetadata', () => {
            URL.revokeObjectURL(url);
            resolve(video.duration);
        });
        video.addEventListener('error', () => {
            URL.revokeObjectURL(url);
            resolve(0);
        });
        setTimeout(() => {
            if (video.duration) {
                URL.revokeObjectURL(url);
                resolve(video.duration);
            }
            else {
                URL.revokeObjectURL(url);
                resolve(0);
            }
        }, 5000);
        video.src = url;
    });
}
export async function parseVideoFile(file: File): Promise<{
    name: string;
    artist: string;
    duration: number;
    fileSize: number;
}> {
    let duration = 0;
    try {
        duration = await getVideoDuration(file);
    }
    catch (e) {
        frontendLogger.capture('warn', '获取视频时长失败: ', file.name, e);
    }
    const parsed = parseFileName(file.name);
    return {
        name: parsed.name,
        artist: parsed.artist,
        duration,
        fileSize: file.size
    };
}
export async function getAudioFilesInfo(files: File[], onProgress?: (current: number, total: number) => void): Promise<AudioFileInfo[]> {
    const results: AudioFileInfo[] = [];
    const audioExtensions = ['mp3', 'flac', 'wav', 'aac', 'ogg', 'm4a', 'wma', 'ape', 'opus', 'aiff', 'aif'];
    for (let i = 0; i < files.length; i++) {
        const file = files[i];
        const ext = file.name.split('.').pop()?.toLowerCase() || '';
        if (audioExtensions.includes(ext)) {
            try {
                const duration = await getAudioDuration(file);
                results.push({
                    name: file.name,
                    size: file.size,
                    duration: duration,
                    quality: getQualityFromFile(file.name)
                });
            }
            catch (e) {
                frontendLogger.capture('error', `解析文件失败: ${file.name}`, e);
                results.push({
                    name: file.name,
                    size: file.size,
                    duration: 0,
                    quality: getQualityFromFile(file.name)
                });
            }
        }
        if (onProgress) {
            onProgress(i + 1, files.length);
        }
    }
    return results;
}
export function filterAudioFiles(files: File[]): File[] {
    const audioExtensions = ['mp3', 'flac', 'wav', 'aac', 'ogg', 'm4a', 'wma', 'ape', 'opus', 'aiff', 'aif'];
    return files.filter(file => {
        const ext = file.name.split('.').pop()?.toLowerCase() || '';
        return audioExtensions.includes(ext);
    });
}
export function filterVideoFiles(files: File[]): File[] {
    const videoExtensions = ['mp4', 'webm', 'mkv', 'avi', 'mov', 'flv', 'wmv', 'm4v'];
    return files.filter(file => {
        const ext = file.name.split('.').pop()?.toLowerCase() || '';
        return videoExtensions.includes(ext);
    });
}
export function parseFileName(filename: string): {
    name: string;
    artist: string;
} {
    const nameWithoutExt = filename.replace(/\.[^/.]+$/, '');
    const dashIndex = nameWithoutExt.lastIndexOf(' - ');
    if (dashIndex > 0 && dashIndex < nameWithoutExt.length - 1) {
        return {
            name: nameWithoutExt.substring(dashIndex + 2).trim(),
            artist: nameWithoutExt.substring(0, dashIndex).trim()
        };
    }
    return {
        name: nameWithoutExt,
        artist: '未知歌手'
    };
}
