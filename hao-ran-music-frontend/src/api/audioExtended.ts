import request from '@/utils/request';
export function getMoodCurve(days: number = 30) {
    return request.get(`/audio-extended/mood-curve?days=${days}`);
}
export function getMoodDiary(startDate: string, endDate: string) {
    return request.get('/audio-extended/mood-diary', { params: { startDate, endDate } });
}
export function getMoodAnalysis() {
    return request.get('/audio-extended/mood-analysis');
}
export function getMusicMap(region: string = 'all', limit: number = 500) {
    return request.get('/audio-extended/map', { params: { region, limit } });
}
export function getNearbySongs(valence: number, energy: number, radius: number = 0.1, limit: number = 20) {
    return request.get('/audio-extended/map/nearby', {
        params: { valence, energy, radius, limit }
    });
}
export function getUserExploration(userId: string | number) {
    return request.get(`/audio-extended/map/exploration/${userId}`);
}
export function getHealthReport() {
    return request.get('/audio-extended/health-report');
}
export function getListeningSummary() {
    return request.get('/audio-extended/listening-summary');
}
export function getMusicFingerprint() {
    return request.get('/audio-extended/music-fingerprint');
}
export function getYearlyReport(year?: number) {
    return request.get('/audio-extended/yearly-report', { params: { year } });
}
export function getMixableSongs(songId: string | number, bpmTolerance: number = 10, limit: number = 20) {
    return request.get(`/audio-extended/dj/mixable/${songId}`, {
        params: { bpmTolerance, limit }
    });
}
export function generateDJMix(data: {
    baseSongId: string | number;
    durationMinutes?: number;
    selectedSongIds?: Array<string | number>;
    targetCount?: number;
    style?: string;
}) {
    return request.post('/audio-extended/dj/generate-mix', data);
}
export function checkMixable(songId1: string | number, songId2: string | number) {
    return request.get('/audio-extended/dj/check-mixable', {
        params: { songId1, songId2 }
    });
}
export function getMixInfo(songId: string | number) {
    return request.get(`/audio-extended/dj/mix-info/${songId}`);
}
export function getThatDayRecommend(month?: number, day?: number, limit: number = 20) {
    return request.get('/audio-extended/timemachine/that-day', { params: { month, day, limit } });
}
export function getMusicTimeline(months: number = 12) {
    return request.get('/audio-extended/timemachine/timeline', { params: { months } });
}
export function getYearlyMemory(year?: number) {
    return request.get('/audio-extended/timemachine/yearly-memory', { params: { year } });
}
export function generateSmartPlaylist(data: {
    description: string;
    count?: number;
}) {
    return request.post('/audio-extended/smart-playlist/generate', data);
}
export function generatePlaylistByActivity(data: {
    activity: string;
    count?: number;
}) {
    return request.post('/audio-extended/smart-playlist/by-activity', data);
}
export function saveSmartPlaylist(data: {
    name: string;
    songIds: Array<string | number>;
    description?: string;
}) {
    return request.post('/audio-extended/smart-playlist/save', data);
}
export function analyzePlaylistAudio(playlistId: string) {
    return request.get(`/playlist-audio/analyze/${playlistId}`);
}
export function getPlaylistAudioTags(playlistId: string) {
    return request.get(`/playlist-audio/tags/${playlistId}`);
}
export function updatePlaylistAudioTags(playlistId: string, tags: string[]) {
    return request.post(`/playlist-audio/update-tags/${playlistId}`, tags);
}
export default {
    getMoodCurve,
    getMoodDiary,
    getMoodAnalysis,
    getMusicMap,
    getNearbySongs,
    getUserExploration,
    getHealthReport,
    getListeningSummary,
    getMusicFingerprint,
    getYearlyReport,
    getMixableSongs,
    generateDJMix,
    checkMixable,
    getMixInfo,
    getThatDayRecommend,
    getMusicTimeline,
    getYearlyMemory,
    generateSmartPlaylist,
    generatePlaylistByActivity,
    saveSmartPlaylist,
    analyzePlaylistAudio,
    getPlaylistAudioTags,
    updatePlaylistAudioTags
};
