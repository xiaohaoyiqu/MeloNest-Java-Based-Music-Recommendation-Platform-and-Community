import { request } from '@/utils/request';
export interface Lyric {
    id: string;
    songId: string;
    content: string;
    language: string;
    lyricType: number;
    source: string;
    userId: string;
    createTime: string;
    updateTime: string;
}
export interface MusicLanguage {
    code: string;
    name: string;
    nativeName: string;
    direction: string;
}
export interface TranslationStatus {
    taskId: string | number;
    songId?: string | number;
    status: number;
    targetLanguage: string;
    lyricType: number;
    requestTime?: string;
    completeTime?: string;
    updatedTime?: string;
    attemptCount?: number;
    maxAttempts?: number;
    nextRetryTime?: string;
    resultContent?: string;
    errorCode?: 'AI_SERVICE_UNAVAILABLE' | 'AI_RESPONSE_EMPTY' | 'SOURCE_UNAVAILABLE' | 'TRANSLATION_FAILED' | 'QUEUE_BUSY' | 'TASK_RECOVERING';
    errorMessage?: string;
}
export function getSongLyric(songId: string, options: {
    signal?: AbortSignal;
} = {}) {
    return request<Lyric>({
        url: `/lyric/${songId}`,
        method: 'GET',
        ...(options.signal ? { signal: options.signal } : {})
    });
}
export function getSongLyrics(songId: string) {
    return request<Lyric[]>({
        url: `/lyric/${songId}/all`,
        method: 'GET'
    });
}
export function saveLyric(songId: string, content: string, language = 'zh-CN', lyricType = 1) {
    return request<boolean>({
        url: '/lyric',
        method: 'POST',
        params: { songId, content, language, lyricType }
    });
}
export function deleteLyric(lyricId: string) {
    return request<boolean>({
        url: `/lyric/${lyricId}`,
        method: 'DELETE'
    });
}
export function requestTranslation(songId: string, targetLanguage: string, lyricType = 2) {
    return request<number>({
        url: '/lyric/translate',
        method: 'POST',
        params: { songId, targetLanguage, lyricType }
    });
}
export function getTranslationStatus(taskId: string) {
    return request<TranslationStatus>({
        url: `/lyric/translate/status/${taskId}`,
        method: 'GET'
    });
}
export function getLatestTranslationStatus(songId: string) {
    return request<TranslationStatus | null>({
        url: `/lyric/translate/latest/${songId}`,
        method: 'GET'
    });
}
export function getSupportedLanguages() {
    return request<MusicLanguage[]>({
        url: '/lyric/languages',
        method: 'GET'
    });
}
export function testDeepSeekConnection() {
    return request<{
        connected: boolean;
        message: string;
        latency: number;
    }>({
        url: '/lyric/test/deepseek',
        method: 'GET'
    });
}
export function getLocalLyric(songId: string) {
    return request<string>({
        url: `/lyric/local/${songId}`,
        method: 'GET'
    });
}
export function syncLocalLyric(songId: string, lyricType = 1, language?: string) {
    return request<boolean>({
        url: `/lyric/local/${songId}/sync`,
        method: 'POST',
        params: { lyricType, language }
    });
}
export function calibrateNode3Lyrics(limit = 100, dryRun = true, includeTranslations = false) {
    return request<Record<string, unknown>>({
        url: '/lyric/local/calibrate',
        method: 'POST',
        params: { limit, dryRun, includeTranslations }
    });
}
export const lyricApi = {
    getSongLyric,
    getSongLyrics,
    saveLyric,
    deleteLyric,
    requestTranslation,
    getTranslationStatus,
    getLatestTranslationStatus,
    getSupportedLanguages,
    testDeepSeekConnection,
    getLocalLyric,
    syncLocalLyric,
    calibrateNode3Lyrics
};
