import { isRestrictedUserType as checkRestrictedUserType } from '@/constants/userAccount';
export { RESTRICTED_USER_TYPE_CODES, isRestrictedUserType } from '@/constants/userAccount';
export interface UserAccountLike {
    status?: unknown;
    userType?: unknown;
    user_type?: unknown;
    isBanned?: unknown;
    deleted?: unknown;
    isDeleted?: unknown;
}
export type UserAccountStatusType = 'normal' | 'banned' | 'frozen' | 'deleted' | 'abnormal';
export interface UserAccountStatusView {
    type: UserAccountStatusType;
    unavailable: boolean;
    text: string;
    shortText: string;
    tagType: 'danger' | 'warning' | 'info';
}
function toNumber(value: unknown): number | null {
    if (typeof value === 'number' && Number.isFinite(value))
        return value;
    if (typeof value === 'string' && value.trim() !== '') {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
}
export function isTruthyFlag(value: unknown): boolean {
    if (value === true)
        return true;
    if (typeof value === 'number')
        return value === 1;
    if (typeof value === 'string') {
        return ['1', 'true', 'yes'].includes(value.trim().toLowerCase());
    }
    return false;
}
export function getUserAccountStatus(user?: UserAccountLike | null): UserAccountStatusView {
    const status = toNumber(user?.status);
    const userType = user?.userType ?? user?.user_type;
    if (isTruthyFlag(user?.deleted) || isTruthyFlag(user?.isDeleted)) {
        return {
            type: 'deleted',
            unavailable: true,
            text: '用户不存在',
            shortText: '用户不存在',
            tagType: 'info'
        };
    }
    if (isTruthyFlag(user?.isBanned) || status === 2) {
        return {
            type: 'banned',
            unavailable: true,
            text: '该用户已被封禁',
            shortText: '已封禁',
            tagType: 'danger'
        };
    }
    if (status === 0) {
        return {
            type: 'frozen',
            unavailable: true,
            text: '该用户已被冻结',
            shortText: '已冻结',
            tagType: 'warning'
        };
    }
    if (status !== null && status !== 1) {
        return {
            type: 'abnormal',
            unavailable: true,
            text: '该用户账号异常',
            shortText: '账号异常',
            tagType: 'info'
        };
    }
    if (checkRestrictedUserType(userType)) {
        return {
            type: 'abnormal',
            unavailable: true,
            text: '该用户账号异常',
            shortText: '账号异常',
            tagType: 'info'
        };
    }
    return {
        type: 'normal',
        unavailable: false,
        text: '',
        shortText: '',
        tagType: 'info'
    };
}
export function isUserAccountUnavailable(user?: UserAccountLike | null): boolean {
    return getUserAccountStatus(user).unavailable;
}
export function getAccountUnavailableMessage(user?: UserAccountLike | null, action = '操作'): string {
    const status = getUserAccountStatus(user);
    return status.unavailable ? `${status.text}，无法${action}` : '';
}
export function getCurrentAccountUnavailableMessage(user?: UserAccountLike | null, action = '操作'): string {
    const status = getUserAccountStatus(user);
    if (!status.unavailable)
        return '';
    const textMap: Record<Exclude<UserAccountStatusType, 'normal'>, string> = {
        banned: '当前账号已被封禁',
        frozen: '当前账号已被冻结',
        deleted: '当前账号不存在',
        abnormal: '当前账号异常'
    };
    return `${textMap[status.type]}，无法${action}`;
}
