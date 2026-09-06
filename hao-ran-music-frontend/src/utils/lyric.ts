export interface LyricLine {
    time: number;
    text: string;
    originalTime: string;
}
export interface ParsedLyric {
    lines: LyricLine[];
    hasLyric: boolean;
    truncated: boolean;
}
export const MAX_LYRIC_CONTENT_LENGTH = 20000;
export const MAX_LYRIC_LINES = 5000;
export function parseLrc(lrcString?: string): ParsedLyric {
    if (!lrcString || lrcString.trim() === '') {
        return { lines: [], hasLyric: false, truncated: false };
    }
    const lines: LyricLine[] = [];
    const normalized = lrcString.replace(/\r\n?/g, '\n');
    const contentTruncated = normalized.length > MAX_LYRIC_CONTENT_LENGTH;
    const safeContent = normalized.slice(0, MAX_LYRIC_CONTENT_LENGTH);
    const allLrcLines = safeContent.split('\n');
    const lineTruncated = allLrcLines.length > MAX_LYRIC_LINES;
    const lrcLines = allLrcLines.slice(0, MAX_LYRIC_LINES);
    const timeRegex = /\[(\d{1,3}):([0-5]\d)(?:[.:](\d{1,3}))?\]/g;
    const metadataRegex = /\[(?:ar|al|by|ti|length|offset|re|ve):[^\]]*\]/gi;
    const offsetMatch = safeContent.match(/\[offset:([+-]?\d{1,7})\]/i);
    const offsetSeconds = offsetMatch
        ? Math.max(-600, Math.min(600, Number(offsetMatch[1]) / 1000))
        : 0;
    for (const line of lrcLines) {
        const trimmedLine = line.trim();
        if (!trimmedLine)
            continue;
        const timeMatches = [...trimmedLine.matchAll(timeRegex)];
        if (timeMatches.length === 0) {
            const plainText = trimmedLine.replace(metadataRegex, '').trim();
            if (plainText) {
                lines.push({ time: -1, text: plainText, originalTime: '' });
            }
            continue;
        }
        const text = trimmedLine.replace(timeRegex, '').replace(metadataRegex, '').trim();
        if (!text)
            continue;
        for (const match of timeMatches) {
            const minutes = parseInt(match[1], 10);
            const seconds = parseInt(match[2], 10);
            const fraction = match[3] ? Number(`0.${match[3]}`) : 0;
            const time = Math.max(0, minutes * 60 + seconds + fraction + offsetSeconds);
            lines.push({
                time,
                text,
                originalTime: match[0]
            });
        }
    }
    lines.sort((a, b) => {
        const aTimed = a.time >= 0;
        const bTimed = b.time >= 0;
        if (aTimed && bTimed)
            return a.time - b.time;
        if (aTimed)
            return -1;
        if (bTimed)
            return 1;
        return 0;
    });
    return {
        lines,
        hasLyric: lines.length > 0,
        truncated: contentTruncated || lineTruncated
    };
}
export function getCurrentLyricIndex(lines: LyricLine[], currentTime: number): number {
    if (lines.length === 0 || !Number.isFinite(currentTime))
        return -1;
    let low = 0;
    let high = lines.length - 1;
    let result = -1;
    while (low <= high) {
        const middle = Math.floor((low + high) / 2);
        const time = lines[middle].time;
        if (time < 0 || time > currentTime) {
            high = middle - 1;
        }
        else {
            result = middle;
            low = middle + 1;
        }
    }
    return result;
}
export function formatLyricTime(seconds: number): string {
    const safeSeconds = Number.isFinite(seconds) ? Math.max(0, seconds) : 0;
    const mins = Math.floor(safeSeconds / 60);
    const secs = Math.floor(safeSeconds % 60);
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}
