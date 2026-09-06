export interface NumberOption {
    label: string;
    value: number;
}
export interface StringOption<T extends string = string> {
    label: string;
    value: T;
}
export const USER_TYPE = {
    NORMAL: 0,
    ACTIVE: 1,
    VIP: 2,
    SUPER_USER: 3,
    INACTIVE: 10,
    SUSPICIOUS: 11,
    BOT: 12,
    BANNED: 13,
    ACCOUNT_FARM: 14,
    IMPERSONATION: 15,
    COMPROMISED: 16,
    SPAM: 17,
    MONETIZATION_RESTRICTED: 18,
    LOW_QUALITY_CREATOR: 19,
    SYNTHETIC_IDENTITY: 20,
    COORDINATED_ABUSE: 21,
    FAKE_ENGAGEMENT: 22,
    RATING_MANIPULATION: 23,
    FOLLOW_MANIPULATION: 24,
    COMMENT_MESSAGE_ABUSE: 25,
    REPORT_ABUSE: 26,
    PLAYBACK_VIEW_ABUSE: 27,
    CRAWLER_SCRAPER: 28,
    DOWNLOAD_ABUSE: 29,
    RESOURCE_UPLOAD_ABUSE: 30,
    CONTENT_THEFT: 31,
    COPYRIGHT_INFRINGER: 32,
    MALICIOUS_EDIT: 33,
    PAYMENT_RISK: 34,
    REFUND_ABUSE: 35,
    CHARGEBACK_RISK: 36,
    BENEFIT_ABUSE: 37,
    BAN_EVASION: 38,
    DEVICE_IP_RISK: 39,
    MANUAL_REVIEW_HOLD: 40
} as const;
export type UserTypeCode = typeof USER_TYPE[keyof typeof USER_TYPE];
export const USER_TYPE_OPTIONS: NumberOption[] = [
    { label: '正常用户', value: USER_TYPE.NORMAL },
    { label: '活跃用户', value: USER_TYPE.ACTIVE },
    { label: 'VIP用户', value: USER_TYPE.VIP },
    { label: '超级用户', value: USER_TYPE.SUPER_USER },
    { label: '不活跃用户', value: USER_TYPE.INACTIVE },
    { label: '可疑用户', value: USER_TYPE.SUSPICIOUS },
    { label: '机器人', value: USER_TYPE.BOT },
    { label: '封禁用户', value: USER_TYPE.BANNED },
    { label: '账号农场', value: USER_TYPE.ACCOUNT_FARM },
    { label: '冒充账号', value: USER_TYPE.IMPERSONATION },
    { label: '被盗风险账号', value: USER_TYPE.COMPROMISED },
    { label: '垃圾营销账号', value: USER_TYPE.SPAM },
    { label: '变现受限账号', value: USER_TYPE.MONETIZATION_RESTRICTED },
    { label: '低质创作者', value: USER_TYPE.LOW_QUALITY_CREATOR },
    { label: '异常AI账号', value: USER_TYPE.SYNTHETIC_IDENTITY },
    { label: '协同刷量账号', value: USER_TYPE.COORDINATED_ABUSE },
    { label: '虚假互动账号', value: USER_TYPE.FAKE_ENGAGEMENT },
    { label: '评分操纵账号', value: USER_TYPE.RATING_MANIPULATION },
    { label: '关注/粉丝操纵账号', value: USER_TYPE.FOLLOW_MANIPULATION },
    { label: '评论/私信滥用账号', value: USER_TYPE.COMMENT_MESSAGE_ABUSE },
    { label: '举报/申诉滥用账号', value: USER_TYPE.REPORT_ABUSE },
    { label: '播放/浏览刷量账号', value: USER_TYPE.PLAYBACK_VIEW_ABUSE },
    { label: '爬虫/采集账号', value: USER_TYPE.CRAWLER_SCRAPER },
    { label: '异常下载账号', value: USER_TYPE.DOWNLOAD_ABUSE },
    { label: '资源上传滥用账号', value: USER_TYPE.RESOURCE_UPLOAD_ABUSE },
    { label: '搬运/盗传账号', value: USER_TYPE.CONTENT_THEFT },
    { label: '版权侵权账号', value: USER_TYPE.COPYRIGHT_INFRINGER },
    { label: '恶意编辑账号', value: USER_TYPE.MALICIOUS_EDIT },
    { label: '支付风险账号', value: USER_TYPE.PAYMENT_RISK },
    { label: '退款滥用账号', value: USER_TYPE.REFUND_ABUSE },
    { label: '拒付/争议风险账号', value: USER_TYPE.CHARGEBACK_RISK },
    { label: '权益滥用账号', value: USER_TYPE.BENEFIT_ABUSE },
    { label: '规避封禁账号', value: USER_TYPE.BAN_EVASION },
    { label: '设备/IP风险账号', value: USER_TYPE.DEVICE_IP_RISK },
    { label: '人工复核限制账号', value: USER_TYPE.MANUAL_REVIEW_HOLD }
];
export const NORMAL_USER_TYPE_OPTIONS = USER_TYPE_OPTIONS.filter(option => option.value <= USER_TYPE.SUPER_USER);
export const ABNORMAL_USER_TYPE_OPTIONS = USER_TYPE_OPTIONS.filter(option => option.value >= USER_TYPE.INACTIVE);
export const RESTRICTED_USER_TYPE_CODE_LIST = USER_TYPE_OPTIONS
    .filter(option => option.value >= USER_TYPE.SUSPICIOUS)
    .map(option => option.value);
export const RESTRICTED_USER_TYPE_CODES = new Set<number>(RESTRICTED_USER_TYPE_CODE_LIST);
export function normalizeUserTypeCode(value: unknown): number | null {
    if (typeof value === 'number' && Number.isFinite(value))
        return value;
    if (typeof value === 'string' && value.trim() !== '') {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
}
export function isRestrictedUserType(value: unknown): boolean {
    const userType = normalizeUserTypeCode(value);
    return userType !== null && RESTRICTED_USER_TYPE_CODES.has(userType);
}
export type CreditScoreFilterValue = 'high' | 'medium' | 'low';
export interface CreditScoreRange {
    min: number;
    max: number;
}
export const CREDIT_SCORE_MAX = 100;
export const CREDIT_SCORE_HIGH_MIN = 80;
export const CREDIT_SCORE_NORMAL_MIN = 60;
export const CREDIT_SCORE_DISPLAY_EXCELLENT_MIN = 90;
export const CREDIT_SCORE_DISPLAY_GOOD_MIN = 80;
export const CREDIT_SCORE_DISPLAY_NORMAL_MIN = 70;
export const CREDIT_SCORE_DISPLAY_POOR_MIN = 60;
export const CREDIT_SCORE_FILTER_OPTIONS: Array<StringOption<CreditScoreFilterValue>> = [
    { label: `优质用户(>=${CREDIT_SCORE_HIGH_MIN})`, value: 'high' },
    { label: `良好用户(${CREDIT_SCORE_NORMAL_MIN}-${CREDIT_SCORE_HIGH_MIN - 1})`, value: 'medium' },
    { label: `低信用(<${CREDIT_SCORE_NORMAL_MIN})`, value: 'low' }
];
export function resolveCreditScoreFilterRange(value?: CreditScoreFilterValue | '' | null): CreditScoreRange | null {
    switch (value) {
        case 'high':
            return { min: CREDIT_SCORE_HIGH_MIN, max: CREDIT_SCORE_MAX };
        case 'medium':
            return { min: CREDIT_SCORE_NORMAL_MIN, max: CREDIT_SCORE_HIGH_MIN - 1 };
        case 'low':
            return { min: 0, max: CREDIT_SCORE_NORMAL_MIN - 1 };
        default:
            return null;
    }
}
export type CreditScoreDisplayLevel = 'excellent' | 'good' | 'normal' | 'poor' | 'bad';
export function resolveCreditScoreDisplayLevel(score?: number | null): CreditScoreDisplayLevel {
    const safeScore = typeof score === 'number' && Number.isFinite(score) ? score : 0;
    if (safeScore >= CREDIT_SCORE_DISPLAY_EXCELLENT_MIN)
        return 'excellent';
    if (safeScore >= CREDIT_SCORE_DISPLAY_GOOD_MIN)
        return 'good';
    if (safeScore >= CREDIT_SCORE_DISPLAY_NORMAL_MIN)
        return 'normal';
    if (safeScore >= CREDIT_SCORE_DISPLAY_POOR_MIN)
        return 'poor';
    return 'bad';
}
