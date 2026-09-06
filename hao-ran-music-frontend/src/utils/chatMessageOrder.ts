export interface ChronologicalMessage {
    id?: string | number | null;
    createTime?: string | null;
}
export function getChatMessageTimestamp(createTime: unknown): number {
    const source = String(createTime ?? '').trim();
    if (!source)
        return Number.MAX_SAFE_INTEGER;
    const normalized = source.includes('T') ? source : source.replace(/\s+/, 'T');
    const timestamp = Date.parse(normalized);
    return Number.isFinite(timestamp) ? timestamp : Number.MAX_SAFE_INTEGER;
}
function compareMessageIds(leftId: ChronologicalMessage['id'], rightId: ChronologicalMessage['id']): number {
    const left = String(leftId ?? '');
    const right = String(rightId ?? '');
    if (left === right)
        return 0;
    if (/^\d+$/.test(left) && /^\d+$/.test(right)) {
        return left.length - right.length || left.localeCompare(right);
    }
    return left.localeCompare(right, 'en', { numeric: true });
}
export function sortChatMessagesChronologically<T extends ChronologicalMessage>(messages: T[]): T[] {
    return [...messages].sort((left, right) => {
        const timeDifference = getChatMessageTimestamp(left.createTime) - getChatMessageTimestamp(right.createTime);
        return timeDifference || compareMessageIds(left.id, right.id);
    });
}
