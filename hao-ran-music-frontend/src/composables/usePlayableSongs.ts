import { computed, isRef, type Ref } from 'vue';
import type { SongInfo } from '@/api/song';
type MaybeRefGetter<T> = T | Ref<T> | (() => T | Ref<T>);
function resolveSource<T>(source: MaybeRefGetter<T>): T {
    const value = typeof source === 'function' ? (source as () => T | Ref<T>)() : source;
    return (isRef(value) ? value.value : value) as T;
}
function hasPlayableUrl(song?: SongInfo | null): boolean {
    if (!song)
        return false;
    return !!(song.urlStandard ||
        song.urlHigh ||
        song.urlLossless ||
        song.urlHires ||
        song.urlMaster ||
        song.isLocal);
}
export function usePlayableSongs(songs: MaybeRefGetter<SongInfo[]>) {
    return computed(() => resolveSource(songs).filter(hasPlayableUrl));
}
export function usePlayableSong(song: MaybeRefGetter<SongInfo | null | undefined>) {
    return computed(() => hasPlayableUrl(resolveSource(song)));
}
