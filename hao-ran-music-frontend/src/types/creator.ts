export enum CreatorType {
    INDEPENDENT = 'independent',
    SIGNED = 'signed',
    PARTNER = 'partner',
    EXTERNAL = 'external'
}
export const CREATOR_TYPE_LABELS: Record<CreatorType, string> = {
    [CreatorType.INDEPENDENT]: '独立创作者',
    [CreatorType.SIGNED]: '签约创作者',
    [CreatorType.PARTNER]: '合作创作者',
    [CreatorType.EXTERNAL]: '外部创作者'
};
export const CREATOR_TYPE_WEIGHT: Record<CreatorType, number> = {
    [CreatorType.INDEPENDENT]: 1.0,
    [CreatorType.SIGNED]: 1.5,
    [CreatorType.PARTNER]: 1.2,
    [CreatorType.EXTERNAL]: 0.8
};
export enum CreatorStatus {
    PENDING = 'pending',
    ACTIVE = 'active',
    SUSPENDED = 'suspended',
    INACTIVE = 'inactive',
    REMOVED = 'removed'
}
export const CREATOR_STATUS_LABELS: Record<CreatorStatus, string> = {
    [CreatorStatus.PENDING]: '审核中',
    [CreatorStatus.ACTIVE]: '活跃',
    [CreatorStatus.SUSPENDED]: '已暂停',
    [CreatorStatus.INACTIVE]: '非活跃',
    [CreatorStatus.REMOVED]: '已移除'
};
export const CREATOR_TYPE_OPTIONS = [
    { label: '独立创作者', value: CreatorType.INDEPENDENT },
    { label: '签约创作者', value: CreatorType.SIGNED },
    { label: '合作创作者', value: CreatorType.PARTNER },
    { label: '外部创作者', value: CreatorType.EXTERNAL }
];
export function getCreatorTypeLabel(type: string): string {
    return CREATOR_TYPE_LABELS[type as CreatorType] || type;
}
export function getCreatorTypeWeight(type: string): number {
    return CREATOR_TYPE_WEIGHT[type as CreatorType] || 1.0;
}
export function getCreatorStatusLabel(status: string): string {
    return CREATOR_STATUS_LABELS[status as CreatorStatus] || status;
}
export function isValidCreatorStatus(status: string): boolean {
    return [
        CreatorStatus.ACTIVE,
        CreatorStatus.PENDING
    ].includes(status as CreatorStatus);
}
