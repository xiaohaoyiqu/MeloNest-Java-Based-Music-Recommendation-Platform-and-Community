export enum CreatorStatus {
    PENDING = 'pending',
    APPROVED = 'active',
    REJECTED = 'rejected',
    SUSPENDED = 'suspended'
}
export const CreatorStatusText: Record<string, string> = {
    [CreatorStatus.PENDING]: '创作者申请中',
    [CreatorStatus.APPROVED]: '创作者',
    [CreatorStatus.REJECTED]: '已拒绝',
    [CreatorStatus.SUSPENDED]: '已暂停'
};
export const CreatorTypeText: Record<string, string> = {
    'independent': '独立创作者',
    'signed': '签约创作者'
};
export function isActiveCreator(isCreator: boolean | undefined, creatorStatus?: string): boolean {
    if (!isCreator)
        return false;
    return creatorStatus === CreatorStatus.APPROVED;
}
export function getCreatorLabel(isCreator: boolean | undefined, creatorStatus?: string): string {
    if (!isCreator)
        return '普通用户';
    return CreatorStatusText[creatorStatus || ''] || '普通用户';
}
export function getCreatorTagType(isCreator: boolean | undefined, creatorStatus?: string): 'success' | 'warning' | 'info' | 'danger' | undefined {
    if (!isCreator)
        return undefined;
    switch (creatorStatus) {
        case CreatorStatus.APPROVED:
            return 'success';
        case CreatorStatus.PENDING:
            return 'warning';
        case CreatorStatus.SUSPENDED:
            return 'info';
        case CreatorStatus.REJECTED:
            return 'danger';
        default:
            return 'success';
    }
}
export function getCreatorTagText(isCreator: boolean | undefined, creatorStatus?: string): string {
    return getCreatorLabel(isCreator, creatorStatus);
}
