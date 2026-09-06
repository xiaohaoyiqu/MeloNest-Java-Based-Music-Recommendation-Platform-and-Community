import { defineStore } from 'pinia';
import { useUserStore } from '@/stores/user';
import { ref, shallowRef, computed, nextTick } from 'vue';
import type { SongInfo } from '@/types/song';
import { listenHistoryApi } from '@/api/listenHistory';
import { songApi } from '@/api/song';
import { getLocalMusicDetail } from '@/api/localMusic';
import { mvApi } from '@/api/mv';
import { processImageUrl, getDefaultCover } from '@/utils/imageUrl';
import { getSongLyric } from '@/api/lyric';
import { logger } from '@/utils/logger';
import { isExactCountGreaterThan, type ExactCountInput } from '@/utils/exactCount';
function normalizeAudioUrl(url: string): string {
    if (!url)
        return '';
    if (url.startsWith('/')) {
        return url;
    }
    return url;
}
type AudioQuality = 'standard' | 'high' | 'lossless';
export interface PlayItem {
    song: SongInfo;
    sourceSong?: SongInfo;
    url: string;
    quality: 'standard' | 'high' | 'lossless';
    requestedQuality?: 'standard' | 'high' | 'lossless';
    preview?: boolean;
}
export type PlayMode = 'sequence' | 'random' | 'loop-one' | 'loop-all';
export type PlaybackStatus = 'idle' | 'requested' | 'loading' | 'playing' | 'paused' | 'failed';
export type LyricLoadStatus = 'idle' | 'loading' | 'ready' | 'empty' | 'failed';
export interface PlaybackRequest {
    requestId: number;
    songId: string;
    status: 'requested';
    replay: boolean;
}
export type PlaybackResult = {
    requestId: number;
    songId: string;
    songName: string;
    status: 'playing';
    replay: boolean;
} | {
    requestId: number;
    songId: string;
    songName: string;
    status: 'failed';
    errorCode: string;
};
export const usePlayerStore = defineStore('player', () => {
    const playlist = ref<PlayItem[]>([]);
    const currentIndex = ref(0);
    const playing = shallowRef(false);
    const playIntent = shallowRef(false);
    const playbackStatus = shallowRef<PlaybackStatus>('idle');
    const playbackRequestId = shallowRef(0);
    const playbackResult = shallowRef<PlaybackResult | null>(null);
    const currentTime = ref(0);
    const duration = ref(0);
    const lyricLoadStates = shallowRef(new Map<string, LyricLoadStatus>());
    const lyricRequests = new Map<string, Promise<void>>();
    const quality = ref<'standard' | 'high' | 'lossless'>('high');
    const playMode = ref<PlayMode>('sequence');
    const playbackRate = ref(1);
    const volume = ref(0.8);
    const replayTrigger = ref(0);
    let playbackUrlRequestSequence = 0;
    let activePlayEventId = '';
    let activeHistoryStartEventId = '';
    let activePlayEventReported = false;
    let activeHistoryRecordReady = false;
    let lastHistoryProgressReported = 0;
    let confirmedPlaybackRequestId = 0;
    let currentPlaybackReplay = false;
    let lastSongRequestId = '';
    let lastSongRequestAt = 0;
    const songStatsRefreshes = new Map<string, Promise<void>>();
    const localMusicStatsRefreshes = new Map<string, Promise<void>>();
    const mvPlaying = ref(false);
    const mvCurrentTime = ref(0);
    const mvDuration = ref(0);
    const mvUrl = ref('');
    function beginPlaybackRequest(song: SongInfo, replay = false): PlaybackRequest {
        playbackRequestId.value += 1;
        currentPlaybackReplay = replay;
        playbackResult.value = null;
        playbackStatus.value = 'requested';
        playIntent.value = true;
        playing.value = false;
        mvPlaying.value = false;
        activePlayEventId = '';
        activeHistoryStartEventId = '';
        activePlayEventReported = false;
        activeHistoryRecordReady = false;
        lastHistoryProgressReported = 0;
        return {
            requestId: playbackRequestId.value,
            songId: String(song.id),
            status: 'requested',
            replay
        };
    }
    function markPlaybackLoading(requestId = playbackRequestId.value): boolean {
        if (requestId !== playbackRequestId.value || !playIntent.value || playbackStatus.value === 'failed') {
            return false;
        }
        playbackStatus.value = 'loading';
        playing.value = false;
        return true;
    }
    function confirmPlaybackStarted(requestId = playbackRequestId.value): boolean {
        if (requestId !== playbackRequestId.value || !playIntent.value) {
            return false;
        }
        const song = currentSong.value;
        if (!song)
            return false;
        if (confirmedPlaybackRequestId === requestId) {
            playbackStatus.value = 'playing';
            playing.value = true;
            return false;
        }
        confirmedPlaybackRequestId = requestId;
        playbackStatus.value = 'playing';
        playing.value = true;
        const currentItem = playlist.value[currentIndex.value];
        if (!currentItem?.preview && !song.isPreview) {
            savePlayHistory(song);
            recordPlaybackStarted(song);
        }
        playbackResult.value = {
            requestId,
            songId: String(song.id),
            songName: song.name,
            status: 'playing',
            replay: currentPlaybackReplay
        };
        return true;
    }
    function failPlayback(requestId = playbackRequestId.value, errorCode = 'MEDIA_PLAYBACK_FAILED'): boolean {
        if (requestId !== playbackRequestId.value || playbackStatus.value === 'failed') {
            return false;
        }
        const song = currentSong.value;
        playIntent.value = false;
        playing.value = false;
        playbackStatus.value = 'failed';
        playbackResult.value = {
            requestId,
            songId: String(song?.id ?? ''),
            songName: song?.name || '当前歌曲',
            status: 'failed',
            errorCode
        };
        return true;
    }
    function pauseAudioPlayback() {
        syncListenProgress(true);
        playIntent.value = false;
        playing.value = false;
        playbackStatus.value = currentSong.value ? 'paused' : 'idle';
    }
    const currentSong = computed(() => {
        return playlist.value[currentIndex.value]?.song || null;
    });
    const currentLyricStatus = computed<LyricLoadStatus>(() => {
        const item = playlist.value[currentIndex.value];
        if (!item)
            return 'idle';
        if (item.song.lyric?.trim())
            return 'ready';
        return lyricLoadStates.value.get(getLyricKey(item)) || (isLocalSong(item.song) ? 'empty' : 'idle');
    });
    const isPreview = computed(() => Boolean(playlist.value[currentIndex.value]?.preview));
    const hasPrev = computed(() => {
        if (playMode.value === 'random')
            return playlist.value.length > 0;
        return currentIndex.value > 0;
    });
    const hasNext = computed(() => {
        if (playMode.value === 'random')
            return playlist.value.length > 0;
        if (playMode.value === 'loop-one')
            return true;
        return currentIndex.value < playlist.value.length - 1;
    });
    const playModeName = computed(() => {
        switch (playMode.value) {
            case 'sequence': return '顺序播放';
            case 'random': return '随机播放';
            case 'loop-one': return '单曲循环';
            case 'loop-all': return '列表循环';
            default: return '顺序播放';
        }
    });
    function addToPlaylist(items: PlayItem[] | SongInfo) {
        if (items && !Array.isArray(items) && typeof items === 'object' && 'id' in items) {
            playlist.value.push(createPlayItem(items as SongInfo));
        }
        else if (Array.isArray(items)) {
            playlist.value.push(...items);
        }
    }
    function replacePlaylist(items: PlayItem[]) {
        playlist.value = items;
        currentIndex.value = 0;
    }
    function playAt(index: number): PlaybackRequest | undefined {
        if (index >= 0 && index < playlist.value.length) {
            syncListenProgress(true);
            currentIndex.value = index;
            const newItem = playlist.value[index];
            applyPreferredQuality(newItem, quality.value);
            const request = beginPlaybackRequest(newItem.song);
            void preparePlayback(newItem, request.requestId);
            void fetchPlayerLyric(newItem);
            return request;
        }
    }
    function getQualityUrl(song: SongInfo, preferredQuality: 'standard' | 'high' | 'lossless'): [
        string,
        'standard' | 'high' | 'lossless'
    ] {
        let url: string;
        let quality: 'standard' | 'high' | 'lossless';
        if (preferredQuality === 'lossless' && song.urlLossless) {
            url = song.urlLossless;
            quality = 'lossless';
        }
        else if (preferredQuality === 'high' && song.urlHigh) {
            url = song.urlHigh;
            quality = 'high';
        }
        else if (preferredQuality === 'standard' && song.urlStandard) {
            url = song.urlStandard;
            quality = 'standard';
        }
        else {
            if (song.urlLossless) {
                url = song.urlLossless;
                quality = 'lossless';
            }
            else if (song.urlHigh) {
                url = song.urlHigh;
                quality = 'high';
            }
            else {
                url = song.urlStandard || '';
                quality = 'standard';
            }
        }
        return [normalizeAudioUrl(url), quality];
    }
    function applyPreferredQuality(item: PlayItem, preferredQuality: AudioQuality) {
        const [candidateUrl, actualQuality] = getQualityUrl(item.song, preferredQuality);
        item.url = actualQuality === 'lossless' ? '' : candidateUrl;
        item.quality = actualQuality;
        item.requestedQuality = preferredQuality;
        item.preview = false;
        item.song.isPreview = false;
        (item.song as any).quality = actualQuality;
    }
    function createPlayItem(song: SongInfo, preferredQuality: AudioQuality = quality.value): PlayItem {
        const item: PlayItem = {
            song: {
                ...song,
                cover: processImageUrl(song.cover) || getDefaultCover()
            },
            sourceSong: song,
            url: '',
            quality: 'standard',
            requestedQuality: preferredQuality,
            preview: false
        };
        applyPreferredQuality(item, preferredQuality);
        return item;
    }
    function isLocalSong(song: SongInfo): boolean {
        const value = song as any;
        return value.source === 'local' || value.isLocal === true
            || String(value.id || '').startsWith('local_')
            || Boolean(value.filePath && value.resourceType !== undefined);
    }
    function applyAuthoritativeSongStats(songId: string, snapshot: SongInfo, sourceSong?: SongInfo) {
        const targets = new Set<SongInfo>();
        if (sourceSong && String(sourceSong.id) === songId)
            targets.add(sourceSong);
        playlist.value.forEach(item => {
            if (String(item.song.id) !== songId)
                return;
            targets.add(item.song);
            if (item.sourceSong)
                targets.add(item.sourceSong);
        });
        const statFields: Array<keyof SongInfo> = [
            'playCount',
            'favoriteCount',
            'commentCount',
            'downloadCount',
            'hotScore',
            'avgRating',
            'ratingCount'
        ];
        targets.forEach(target => {
            statFields.forEach(field => {
                const value = snapshot[field];
                if (value !== undefined && value !== null) {
                    ;
                    (target as any)[field] = value;
                }
            });
        });
    }
    async function refreshSongStatsAfterQualifiedPlay(songId: string, baseline: ExactCountInput, sourceSong?: SongInfo, retry = true): Promise<void> {
        const response = await songApi.getSongById(songId);
        const snapshot = response.data;
        if (!snapshot)
            return;
        applyAuthoritativeSongStats(songId, snapshot, sourceSong);
        if (retry && !isExactCountGreaterThan(snapshot.playCount, baseline)) {
            await new Promise(resolve => globalThis.setTimeout(resolve, 1200));
            await refreshSongStatsAfterQualifiedPlay(songId, baseline, sourceSong, false);
        }
    }
    function scheduleSongStatsRefresh(songId: string, baseline: ExactCountInput, sourceSong?: SongInfo) {
        if (songStatsRefreshes.has(songId))
            return;
        const task = refreshSongStatsAfterQualifiedPlay(songId, baseline, sourceSong)
            .catch(error => {
            logger.warn('player_song_stats_refresh_failed', error, { songId });
        })
            .finally(() => {
            songStatsRefreshes.delete(songId);
        });
        songStatsRefreshes.set(songId, task);
    }
    function applyAuthoritativeLocalMusicStats(songId: string, playCount: number, sourceSong?: SongInfo) {
        const targets = new Set<SongInfo>();
        if (sourceSong && String(sourceSong.id) === songId)
            targets.add(sourceSong);
        playlist.value.forEach(item => {
            if (String(item.song.id) !== songId)
                return;
            targets.add(item.song);
            if (item.sourceSong)
                targets.add(item.sourceSong);
        });
        targets.forEach(target => {
            target.playCount = playCount;
        });
    }
    async function refreshLocalMusicStatsAfterQualifiedPlay(songId: string, baseline: number, sourceSong?: SongInfo, retry = true): Promise<void> {
        const response = await getLocalMusicDetail(songId);
        const snapshot = response.data;
        if (!snapshot)
            return;
        const refreshedCount = Number(snapshot.playCount || 0);
        applyAuthoritativeLocalMusicStats(songId, refreshedCount, sourceSong);
        if (retry && refreshedCount <= baseline) {
            await new Promise(resolve => globalThis.setTimeout(resolve, 1200));
            await refreshLocalMusicStatsAfterQualifiedPlay(songId, baseline, sourceSong, false);
        }
    }
    function scheduleLocalMusicStatsRefresh(songId: string, baseline: number, sourceSong?: SongInfo) {
        if (localMusicStatsRefreshes.has(songId))
            return;
        const task = refreshLocalMusicStatsAfterQualifiedPlay(songId, baseline, sourceSong)
            .catch(error => {
            logger.warn('player_local_music_stats_refresh_failed', error, { songId });
        })
            .finally(() => {
            localMusicStatsRefreshes.delete(songId);
        });
        localMusicStatsRefreshes.set(songId, task);
    }
    async function preparePlayback(item: PlayItem, requestId = playbackRequestId.value) {
        const urlRequestId = ++playbackUrlRequestSequence;
        markPlaybackLoading(requestId);
        if (isLocalSong(item.song)) {
            return;
        }
        const hasDeclaredSource = Boolean(item.song.urlStandard || item.song.urlHigh || item.song.urlLossless);
        if (!hasDeclaredSource) {
            try {
                const response = await songApi.getSongById(item.song.id);
                if (urlRequestId !== playbackUrlRequestSequence
                    || requestId !== playbackRequestId.value
                    || playlist.value[currentIndex.value] !== item) {
                    return;
                }
                const hydratedSong = {
                    ...item.song,
                    ...(response.data || {}),
                    cover: processImageUrl(response.data?.cover || item.song.cover) || getDefaultCover()
                };
                const [hydratedUrl, hydratedQuality] = getQualityUrl(hydratedSong, item.requestedQuality || quality.value);
                item.song = hydratedSong;
                item.url = hydratedQuality === 'lossless' ? '' : hydratedUrl;
                item.quality = hydratedQuality;
                (item.song as any).quality = hydratedQuality;
            }
            catch (error) {
                logger.warn('player_song_hydration_failed', error, { songId: String(item.song.id) });
            }
        }
        const requestedQuality = item.requestedQuality || item.quality;
        const fullQualityCandidates: AudioQuality[] = requestedQuality === 'lossless'
            ? ['lossless', 'high']
            : [requestedQuality];
        let fullAccessError: unknown;
        for (const candidate of fullQualityCandidates) {
            try {
                const response = await songApi.getPlayUrl(item.song.id, candidate);
                const controlledUrl = response.data;
                const expectedPrefix = `/api/song/stream/${item.song.id}?`;
                if (!controlledUrl || !controlledUrl.startsWith(expectedPrefix)) {
                    throw new Error('INVALID_PLAYBACK_URL');
                }
                if (urlRequestId !== playbackUrlRequestSequence
                    || requestId !== playbackRequestId.value
                    || playlist.value[currentIndex.value] !== item) {
                    return;
                }
                const resolvedQuality = controlledUrl.match(/[?&]quality=(standard|high|lossless)(?:&|$)/)?.[1] as AudioQuality | undefined;
                item.url = normalizeAudioUrl(controlledUrl);
                item.quality = resolvedQuality || candidate;
                item.preview = false;
                item.song.isPreview = false;
                (item.song as any).quality = item.quality;
                replayTrigger.value++;
                return;
            }
            catch (error) {
                fullAccessError = error;
            }
        }
        try {
            const response = await songApi.getPreviewUrl(item.song.id);
            const previewUrl = response.data;
            const expectedPrefix = `/api/song/stream/${item.song.id}?`;
            if (!previewUrl || !previewUrl.startsWith(expectedPrefix) || !/[?&]quality=preview(?:&|$)/.test(previewUrl)) {
                throw new Error('INVALID_PREVIEW_URL');
            }
            if (urlRequestId !== playbackUrlRequestSequence
                || requestId !== playbackRequestId.value
                || playlist.value[currentIndex.value] !== item) {
                return;
            }
            item.url = normalizeAudioUrl(previewUrl);
            item.quality = 'standard';
            item.preview = true;
            item.song.isPreview = true;
            (item.song as any).quality = 'standard';
            replayTrigger.value++;
            logger.debug('player_preview_fallback', { songId: String(item.song.id) });
        }
        catch (previewError) {
            if (urlRequestId !== playbackUrlRequestSequence
                || requestId !== playbackRequestId.value
                || playlist.value[currentIndex.value] !== item) {
                return;
            }
            item.url = '';
            failPlayback(requestId, 'PLAYBACK_URL_UNAVAILABLE');
            logger.warn('player_playback_url_resolution_failed', {
                songId: String(item.song.id),
                fullAccessCode: Number((fullAccessError as any)?.code || 0) || undefined,
                previewCode: Number((previewError as any)?.code || 0) || undefined
            });
        }
    }
    function playNext(): PlaybackRequest | undefined {
        if (playlist.value.length === 0)
            return;
        syncListenProgress(true);
        switch (playMode.value) {
            case 'random':
                let randomIndex = Math.floor(Math.random() * playlist.value.length);
                while (randomIndex === currentIndex.value && playlist.value.length > 1) {
                    randomIndex = Math.floor(Math.random() * playlist.value.length);
                }
                currentIndex.value = randomIndex;
                break;
            case 'loop-one':
                break;
            case 'loop-all':
                if (currentIndex.value < playlist.value.length - 1) {
                    currentIndex.value++;
                }
                else {
                    currentIndex.value = 0;
                }
                break;
            default:
                if (currentIndex.value < playlist.value.length - 1) {
                    currentIndex.value++;
                }
                break;
        }
        const newItem = playlist.value[currentIndex.value];
        applyPreferredQuality(newItem, quality.value);
        currentTime.value = 0;
        const song = newItem.song;
        const request = beginPlaybackRequest(song);
        void preparePlayback(newItem, request.requestId);
        void fetchPlayerLyric(newItem);
        return request;
    }
    function playPrev(): PlaybackRequest | undefined {
        if (playlist.value.length === 0)
            return;
        syncListenProgress(true);
        if (playMode.value === 'random') {
            let randomIndex = Math.floor(Math.random() * playlist.value.length);
            while (randomIndex === currentIndex.value && playlist.value.length > 1) {
                randomIndex = Math.floor(Math.random() * playlist.value.length);
            }
            currentIndex.value = randomIndex;
        }
        else {
            if (currentIndex.value > 0) {
                currentIndex.value--;
            }
            else {
                currentIndex.value = playlist.value.length - 1;
            }
        }
        const newItem = playlist.value[currentIndex.value];
        applyPreferredQuality(newItem, quality.value);
        currentTime.value = 0;
        const song = playlist.value[currentIndex.value].song;
        const request = beginPlaybackRequest(song);
        void preparePlayback(newItem, request.requestId);
        void fetchPlayerLyric(newItem);
        return request;
    }
    function togglePlay() {
        if (playing.value || playIntent.value) {
            pauseAudioPlayback();
            return;
        }
        const item = playlist.value[currentIndex.value];
        if (!item)
            return;
        if (playbackStatus.value === 'paused' && playbackRequestId.value > 0) {
            playIntent.value = true;
            playbackStatus.value = 'loading';
            return {
                requestId: playbackRequestId.value,
                songId: String(item.song.id),
                status: 'requested' as const,
                replay: false
            };
        }
        const request = beginPlaybackRequest(item.song);
        void preparePlayback(item, request.requestId);
        return request;
    }
    function pause() {
        pauseAudioPlayback();
    }
    function clearPlaylist() {
        playlist.value = [];
        currentIndex.value = 0;
        pauseAudioPlayback();
        playbackStatus.value = 'idle';
        currentTime.value = 0;
    }
    function setQuality(q: 'standard' | 'high' | 'lossless') {
        quality.value = q;
        if (currentSong.value) {
            const item = playlist.value[currentIndex.value];
            applyPreferredQuality(item, q);
            const request = beginPlaybackRequest(item.song);
            void preparePlayback(item, request.requestId);
            return request;
        }
    }
    function togglePlayMode() {
        const modes: PlayMode[] = ['sequence', 'random', 'loop-one', 'loop-all'];
        const currentIdx = modes.indexOf(playMode.value);
        playMode.value = modes[(currentIdx + 1) % modes.length];
    }
    function setPlayMode(mode: PlayMode) {
        playMode.value = mode;
    }
    function setPlaybackRate(rate: number) {
        playbackRate.value = rate;
    }
    function setVolume(vol: number) {
        volume.value = Math.max(0, Math.min(1, vol));
    }
    function playSong(song: SongInfo, addToQueue: boolean = true, preferredQuality?: 'standard' | 'high' | 'lossless', playlistId?: number | string, dedupeRapidRequest = true): PlaybackRequest {
        syncListenProgress(true);
        const requestedSongId = String(song.id);
        const now = Date.now();
        if (dedupeRapidRequest
            && requestedSongId === lastSongRequestId
            && now - lastSongRequestAt < 400
            && String(currentSong.value?.id ?? '') === requestedSongId
            && playIntent.value) {
            return {
                requestId: playbackRequestId.value,
                songId: requestedSongId,
                status: 'requested',
                replay: false
            };
        }
        lastSongRequestId = requestedSongId;
        lastSongRequestAt = now;
        logger.debug('player_song_requested', {
            songId: String(song.id),
            addToQueue,
            preferredQuality
        });
        const item = createPlayItem(song, preferredQuality || quality.value);
        const playQuality = item.quality;
        const url = item.url;
        logger.debug('player_item_prepared', { songId: String(song.id), playQuality });
        let replay = String(currentSong.value?.id ?? '') === String(song.id) && (playing.value || playIntent.value);
        if (addToQueue) {
            const existingIndex = playlist.value.findIndex(p => String(p.song.id) === String(song.id));
            if (existingIndex >= 0) {
                const isCurrentSong = currentIndex.value === existingIndex;
                if (isCurrentSong) {
                    if (playlist.value[existingIndex].quality !== playQuality) {
                        playlist.value[existingIndex].quality = playQuality;
                        playlist.value[existingIndex].url = url;
                        currentTime.value = 0;
                        replayTrigger.value++;
                    }
                    else {
                        currentTime.value = 0;
                        replayTrigger.value++;
                    }
                }
                else {
                    playlist.value[existingIndex] = item;
                    currentIndex.value = existingIndex;
                }
            }
            else {
                playlist.value.push(item);
                currentIndex.value = playlist.value.length - 1;
            }
        }
        else {
            playlist.value = [item];
            currentIndex.value = 0;
        }
        currentTime.value = 0;
        const currentItem = playlist.value[currentIndex.value];
        void fetchPlayerLyric(currentItem);
        const request = beginPlaybackRequest(currentItem.song, replay);
        void preparePlayback(currentItem, request.requestId);
        logger.debug('player_state_updated', {
            playlistSize: playlist.value.length,
            currentIndex: currentIndex.value,
            playbackStatus: playbackStatus.value
        });
        return request;
    }
    function toggleSong(song: SongInfo, addToQueue: boolean = true, preferredQuality?: AudioQuality) {
        const replayVersion = replayTrigger.value;
        const request = playSong(song, addToQueue, preferredQuality, undefined, false);
        const isCurrent = String(currentSong.value?.id ?? '') === String(song.id);
        if (isCurrent && replayTrigger.value === replayVersion) {
            replayTrigger.value++;
        }
        return request;
    }
    function playMV(mvId: string | string, url: string, duration: number = 0) {
        pauseAudioPlayback();
        mvPlaying.value = true;
        mvDuration.value = duration;
        mvCurrentTime.value = 0;
        mvUrl.value = url;
        const userStore = useUserStore();
        if (userStore.isLogin && mvId) {
            mvApi.recordMVPlay(String(mvId)).catch(err => {
                logger.warn('player_mv_statistic_failed', err);
            });
        }
    }
    function pauseMV() {
        mvPlaying.value = false;
    }
    function setMvPlaying(value: boolean) {
        if (value) {
            pauseAudioPlayback();
        }
        mvPlaying.value = value;
    }
    function savePlayHistory(song: SongInfo) {
        activePlayEventId = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
            ? crypto.randomUUID()
            : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
        activePlayEventReported = false;
        try {
            const historyItem = {
                id: song.id,
                name: song.name,
                artistNames: song.artistNames,
                albumName: song.albumName,
                cover: song.cover,
                playTime: Date.now()
            };
            let history: typeof historyItem[] = [];
            const stored = localStorage.getItem('playHistory');
            if (stored) {
                try {
                    history = JSON.parse(stored);
                }
                catch (e) {
                    logger.error('player_history_parse_failed', e);
                }
            }
            history = history.filter(item => item.id !== song.id);
            history.unshift(historyItem);
            if (history.length > 500) {
                history = history.slice(0, 500);
            }
            localStorage.setItem('playHistory', JSON.stringify(history));
        }
        catch (error) {
            logger.error('player_history_save_failed', error);
        }
    }
    function recordPlaybackStarted(song: SongInfo) {
        const userStore = useUserStore();
        if (!userStore.isLogin || !activePlayEventId || activeHistoryStartEventId)
            return;
        const eventId = `${activePlayEventId}-started`;
        activeHistoryStartEventId = eventId;
        const isLocalMusic = isLocalSong(song) ? 1 : 0;
        const currentQuality = playlist.value[currentIndex.value]?.quality || 'standard';
        listenHistoryApi.addRecord(song.id, 0, currentQuality, isLocalMusic, eventId)
            .then(() => {
            if (activeHistoryStartEventId !== eventId)
                return;
            activeHistoryRecordReady = true;
            lastHistoryProgressReported = 0;
        })
            .catch(err => {
            if (activeHistoryStartEventId !== eventId)
                return;
            activeHistoryStartEventId = '';
            logger.warn('player_history_start_report_failed', err, { songId: String(song.id) });
        });
    }
    function removeFromPlaylist(index: number) {
        if (index >= 0 && index < playlist.value.length) {
            playlist.value.splice(index, 1);
            if (index < currentIndex.value) {
                currentIndex.value--;
            }
            else if (index === currentIndex.value) {
                if (currentIndex.value >= playlist.value.length) {
                    currentIndex.value = Math.max(0, playlist.value.length - 1);
                    pauseAudioPlayback();
                }
            }
        }
    }
    function clearAll() {
        syncListenProgress(true);
        playlist.value = [];
        currentIndex.value = 0;
        pauseAudioPlayback();
        playbackStatus.value = 'idle';
    }
    function playPlaylist(songs: SongInfo[], startIndex: number = 0): PlaybackRequest | undefined {
        if (!songs || songs.length === 0) {
            logger.debug('player_playlist_empty');
            return;
        }
        syncListenProgress(true);
        const validIndex = Math.max(0, Math.min(startIndex, songs.length - 1));
        const nextSongId = String(songs[validIndex]?.id ?? '');
        const replay = nextSongId === String(currentSong.value?.id ?? '') && (playing.value || playIntent.value);
        const items: PlayItem[] = songs.map(song => createPlayItem(song));
        playlist.value = items;
        currentIndex.value = validIndex;
        logger.debug('player_playlist_started', {
            playlistSize: items.length,
            currentIndex: validIndex
        });
        const song = items[validIndex].song;
        currentTime.value = 0;
        const request = beginPlaybackRequest(song, replay);
        if (replay)
            replayTrigger.value++;
        void fetchPlayerLyric(items[validIndex]);
        void preparePlayback(items[validIndex], request.requestId);
        return request;
    }
    function insertNext(song: SongInfo): PlaybackRequest | undefined {
        logger.debug('player_song_queued_next', { songId: String(song.id) });
        const item = createPlayItem(song);
        if (playlist.value.length === 0) {
            playlist.value.push(item);
            currentIndex.value = 0;
            const request = beginPlaybackRequest(item.song);
            nextTick(() => {
                void preparePlayback(item, request.requestId);
            });
            void fetchPlayerLyric(item);
            return request;
        }
        else {
            const insertIndex = currentIndex.value + 1;
            playlist.value.splice(insertIndex, 0, item);
        }
    }
    function seek(time: number) {
        currentTime.value = time;
    }
    function getLyricKey(item: PlayItem): string {
        return `${isLocalSong(item.song) ? 'local' : 'catalog'}:${String(item.song.id)}`;
    }
    function setLyricLoadState(key: string, status: LyricLoadStatus) {
        const next = new Map(lyricLoadStates.value);
        next.set(key, status);
        while (next.size > 100)
            next.delete(next.keys().next().value as string);
        lyricLoadStates.value = next;
    }
    function fetchPlayerLyric(item: PlayItem, force = false): Promise<void> {
        const key = getLyricKey(item);
        if (item.song.lyric?.trim()) {
            setLyricLoadState(key, 'ready');
            return Promise.resolve();
        }
        if (isLocalSong(item.song)) {
            setLyricLoadState(key, 'empty');
            return Promise.resolve();
        }
        const previousStatus = lyricLoadStates.value.get(key);
        if (!force && previousStatus === 'ready')
            return Promise.resolve();
        const existing = lyricRequests.get(key);
        if (existing)
            return existing;
        setLyricLoadState(key, 'loading');
        const songId = String(item.song.id);
        const request = getSongLyric(songId)
            .then(response => {
            const content = response.data?.content?.trim() || '';
            if (content) {
                playlist.value.forEach(candidate => {
                    if (!isLocalSong(candidate.song) && String(candidate.song.id) === songId) {
                        candidate.song.lyric = content;
                        if (candidate.sourceSong)
                            candidate.sourceSong.lyric = content;
                    }
                });
            }
            setLyricLoadState(key, content ? 'ready' : 'empty');
        })
            .catch(error => {
            setLyricLoadState(key, 'failed');
            logger.debug('player_lyric_load_failed', {
                songId,
                errorType: error instanceof Error ? error.name : 'UnknownError'
            });
        })
            .finally(() => lyricRequests.delete(key));
        lyricRequests.set(key, request);
        return request;
    }
    function retryCurrentLyric() {
        const item = playlist.value[currentIndex.value];
        if (item)
            void fetchPlayerLyric(item, true);
    }
    function setCurrentTime(time: number) {
        currentTime.value = time;
        const song = currentSong.value;
        const userStore = useUserStore();
        if (!song || !userStore.isLogin || !activePlayEventId) {
            return;
        }
        if (activePlayEventReported) {
            syncListenProgress(false);
            return;
        }
        const knownDuration = duration.value > 0 ? duration.value : Number((song as any).duration || 0);
        const threshold = knownDuration > 0
            ? Math.min(30, Math.max(5, Math.ceil(knownDuration / 2)))
            : 30;
        if (time < threshold) {
            return;
        }
        activePlayEventReported = true;
        const reportedEventId = activePlayEventId;
        const isLocalMusic = isLocalSong(song) ? 1 : 0;
        const reportedItem = playlist.value[currentIndex.value];
        const currentQuality = reportedItem?.quality || 'standard';
        const baselinePlayCount = song.playCount;
        listenHistoryApi.addRecord(song.id, Math.floor(time), currentQuality, isLocalMusic, activePlayEventId)
            .then(() => {
            if (activePlayEventId !== reportedEventId)
                return;
            activeHistoryRecordReady = true;
            lastHistoryProgressReported = Math.floor(time);
            if (isLocalMusic) {
                const localBaseline = typeof baselinePlayCount === 'number' ? baselinePlayCount : 0;
                scheduleLocalMusicStatsRefresh(String(song.id), localBaseline, reportedItem?.sourceSong);
            }
            else {
                scheduleSongStatsRefresh(String(song.id), baselinePlayCount, reportedItem?.sourceSong);
            }
        })
            .catch(err => {
            if (activePlayEventId !== reportedEventId)
                return;
            activePlayEventReported = false;
            logger.warn('player_event_report_failed', err);
        });
    }
    function syncListenProgress(force = false) {
        const song = currentSong.value;
        const userStore = useUserStore();
        if (!song || !userStore.isLogin || !activePlayEventReported || !activeHistoryRecordReady)
            return;
        const progress = Math.max(0, Math.floor(currentTime.value));
        if (progress <= 0 || (!force && progress - lastHistoryProgressReported < 10))
            return;
        const eventId = activePlayEventId;
        const songId = song.id;
        const currentQuality = playlist.value[currentIndex.value]?.quality || 'standard';
        const isLocalMusic = isLocalSong(song) ? 1 : 0;
        lastHistoryProgressReported = progress;
        listenHistoryApi.updateProgress(songId, progress, currentQuality, isLocalMusic).catch(err => {
            if (activePlayEventId === eventId) {
                lastHistoryProgressReported = Math.min(lastHistoryProgressReported, Math.max(0, progress - 10));
            }
            logger.warn('player_history_progress_update_failed', err, { songId: String(songId) });
        });
    }
    function setDuration(dur: number) {
        duration.value = dur;
    }
    function setPlaying(isPlaying: boolean) {
        if (isPlaying) {
            confirmPlaybackStarted();
        }
        else {
            playing.value = false;
        }
    }
    function stopMv() {
        mvPlaying.value = false;
        mvUrl.value = '';
    }
    function playIndex(index: number) {
        playAt(index);
    }
    const autoPlay = ref(false);
    function setAutoPlay(value: boolean) {
        autoPlay.value = value;
    }
    return {
        playlist,
        currentIndex,
        playing,
        isPlaying: playing,
        playIntent,
        playbackStatus,
        playbackRequestId,
        playbackResult,
        currentTime,
        duration,
        quality,
        playMode,
        playbackRate,
        volume,
        currentSong,
        currentLyricStatus,
        isPreview,
        hasPrev,
        hasNext,
        playModeName,
        addToPlaylist,
        replacePlaylist,
        playAt,
        playNext,
        playPrev,
        togglePlay,
        pause,
        clearPlaylist,
        setQuality,
        togglePlayMode,
        setPlayMode,
        setPlaybackRate,
        setVolume,
        replayTrigger,
        mvPlaying,
        mvCurrentTime,
        mvDuration,
        mvUrl,
        setMvPlaying,
        playSong,
        toggleSong,
        playMV,
        pauseMV,
        playPlaylist,
        insertNext,
        addToNext: insertNext,
        removeFromPlaylist,
        clearAll,
        seek,
        retryCurrentLyric,
        setCurrentTime,
        syncListenProgress,
        setDuration,
        setPlaying,
        markPlaybackLoading,
        confirmPlaybackStarted,
        failPlayback,
        stopMv,
        playIndex,
        autoPlay,
        setAutoPlay
    };
});
