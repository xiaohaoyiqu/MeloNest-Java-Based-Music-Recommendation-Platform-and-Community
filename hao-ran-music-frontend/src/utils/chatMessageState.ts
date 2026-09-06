const SYNCED_MESSAGE_FIELDS = [
    'createTime',
    'isRead',
    'isRecalled',
    'content',
    'resourceId',
    'attachmentAssetId',
    'resourceAvailable'
] as const;
export function hasChatMessageStateChanged(previous: Record<string, unknown>, current: Record<string, unknown>): boolean {
    return SYNCED_MESSAGE_FIELDS.some(field => previous[field] !== current[field]);
}
