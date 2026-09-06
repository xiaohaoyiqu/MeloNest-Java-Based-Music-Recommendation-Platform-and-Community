export type ModerationTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger';
export interface ModerationOption {
    label: string;
    value: string;
    tagType?: ModerationTagType;
    adminOnly?: boolean;
}
export const MODERATION_TARGET_TYPE_OPTIONS: ModerationOption[] = [
    { label: '歌曲资源申请', value: 'song_resource_request', tagType: 'primary' },
    { label: '资源申请', value: 'resource_request', tagType: 'primary' },
    { label: '创作者作品', value: 'creator_work', tagType: 'success' },
    { label: '用户上传', value: 'user_upload', tagType: 'warning' },
    { label: '普通用户投稿', value: 'user_work', tagType: 'warning' },
    { label: '音乐广场投稿', value: 'music_square_work', tagType: 'success' },
    { label: '表情包投稿', value: 'emoji_package', tagType: 'warning' },
    { label: '装饰投稿', value: 'decoration', tagType: 'warning' },
    { label: '歌词修正', value: 'lyric_request', tagType: 'info' },
    { label: '举报处理', value: 'report', tagType: 'danger' },
    { label: '评论举报', value: 'comment_report', tagType: 'danger' },
    { label: '动态举报', value: 'post_report', tagType: 'danger' },
    { label: '艺人认证', value: 'artist_application', tagType: 'danger', adminOnly: true },
    { label: '创作者申请', value: 'creator_apply', tagType: 'danger', adminOnly: true },
    { label: '外部创作者申请', value: 'external_creator_apply', tagType: 'danger', adminOnly: true },
    { label: '创作者VIP申请', value: 'creator_vip_apply', tagType: 'danger', adminOnly: true },
    { label: '用户认证', value: 'user_verification', tagType: 'danger', adminOnly: true },
    { label: '提现申请', value: 'withdraw_apply', tagType: 'danger', adminOnly: true },
    { label: '退款申请', value: 'refund_apply', tagType: 'danger', adminOnly: true },
    { label: '支付凭证', value: 'payment_order', tagType: 'warning', adminOnly: true },
    { label: '外部内容入库', value: 'external_content', tagType: 'warning', adminOnly: true },
    { label: '装扮市场物品', value: 'marketplace_item', tagType: 'warning', adminOnly: true },
    { label: '歌曲', value: 'song', tagType: 'primary' },
    { label: '专辑', value: 'album', tagType: 'success' },
    { label: '歌单', value: 'playlist', tagType: 'warning' },
    { label: 'MV', value: 'mv', tagType: 'danger' },
    { label: '评论', value: 'comment', tagType: 'info' },
    { label: '动态', value: 'post', tagType: 'info' },
    { label: '用户', value: 'user', tagType: 'info' }
];
export const MODERATION_SUBMITTER_SOURCE_OPTIONS: ModerationOption[] = [
    { label: '平台', value: 'platform', tagType: 'primary' },
    { label: '外部', value: 'external', tagType: 'warning' },
    { label: '创作者', value: 'creator', tagType: 'success' },
    { label: '用户', value: 'user', tagType: 'info' },
    { label: '系统', value: 'system', tagType: 'info' },
    { label: '管理员', value: 'admin', tagType: 'danger' }
];
export const MODERATION_STATUS_OPTIONS: ModerationOption[] = [
    { label: '待处理', value: 'pending', tagType: 'warning' },
    { label: '审核中', value: 'in_progress', tagType: 'primary' },
    { label: '已通过', value: 'approved', tagType: 'success' },
    { label: '已拒绝', value: 'rejected', tagType: 'danger' },
    { label: '已跳过', value: 'skipped', tagType: 'info' }
];
export function getModerationTargetTypeOptions(includeAdminOnly = true) {
    return includeAdminOnly
        ? MODERATION_TARGET_TYPE_OPTIONS
        : MODERATION_TARGET_TYPE_OPTIONS.filter(option => !option.adminOnly);
}
export function getModerationTargetTypeName(type?: string) {
    return findLabel(MODERATION_TARGET_TYPE_OPTIONS, type);
}
export function getModerationTargetTypeColor(type?: string): ModerationTagType {
    return findTagType(MODERATION_TARGET_TYPE_OPTIONS, type);
}
export function getModerationSubmitterSourceName(source?: string) {
    return findLabel(MODERATION_SUBMITTER_SOURCE_OPTIONS, source);
}
export function getModerationStatusName(status?: string) {
    return findLabel(MODERATION_STATUS_OPTIONS, status);
}
export function getModerationStatusColor(status?: string): ModerationTagType {
    return findTagType(MODERATION_STATUS_OPTIONS, status);
}
export function getModerationPriorityColor(priority?: number | string | null): ModerationTagType {
    const value = Number(priority);
    if (Number.isNaN(value))
        return 'info';
    if (value <= 2)
        return 'danger';
    if (value <= 5)
        return 'warning';
    return 'info';
}
function findLabel(options: ModerationOption[], value?: string) {
    if (!value)
        return '-';
    return options.find(option => option.value === value)?.label || value;
}
function findTagType(options: ModerationOption[], value?: string): ModerationTagType {
    if (!value)
        return 'info';
    return options.find(option => option.value === value)?.tagType || 'info';
}
