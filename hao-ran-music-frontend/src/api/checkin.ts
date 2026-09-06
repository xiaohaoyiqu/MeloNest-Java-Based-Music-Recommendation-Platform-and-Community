import { request } from '@/utils/request';
export interface CheckinResult {
    checkinDate: string;
    continuousDays: number;
    rewardPoints: number;
    rewardVipDays: number;
    isNewRecord: boolean;
    totalPoints: number;
    costPoints?: number;
}
export interface CheckinStats {
    monthCount: number;
    continuousDays: number;
    checkedToday: boolean;
    totalCount: number;
    totalPoints: number;
    businessDate: string;
    businessZone: string;
    makeupWindowDays: number;
    makeupCostPoints: number;
    makeupRewardPoints: number;
    makeupAffectsContinuousDays: boolean;
    dailyRewardPoints: number;
    weeklyBonusPoints: number;
    weeklyVipDays: number;
    monthlyBonusPoints: number;
}
export const doCheckin = () => {
    return request<CheckinResult>({
        url: '/user/checkin',
        method: 'POST'
    });
};
export const hasCheckedInToday = () => {
    return request<boolean>({
        url: '/user/checkin/status',
        method: 'GET'
    });
};
export const getCheckinStats = () => {
    return request<CheckinStats>({
        url: '/user/checkin/stats',
        method: 'GET'
    }).catch((error: any) => {
        if (error?.isAuthError || error?.code === 401 || error?.code === 1004) {
            return {
                code: 200,
                data: {
                    monthCount: 0,
                    continuousDays: 0,
                    checkedToday: false,
                    totalCount: 0,
                    totalPoints: 0,
                    businessDate: '',
                    businessZone: 'Asia/Shanghai',
                    makeupWindowDays: 7,
                    makeupCostPoints: 10,
                    makeupRewardPoints: 5,
                    makeupAffectsContinuousDays: false,
                    dailyRewardPoints: 5,
                    weeklyBonusPoints: 15,
                    weeklyVipDays: 5,
                    monthlyBonusPoints: 100
                },
                message: '未登录'
            };
        }
        throw error;
    });
};
export const getMonthCheckinDates = () => {
    return request<string[]>({
        url: '/user/checkin/calendar',
        method: 'GET'
    }).catch((error: any) => {
        if (error?.isAuthError || error?.code === 401 || error?.code === 1004) {
            return { code: 200, data: [], message: '未登录' };
        }
        throw error;
    });
};
export const makeupCheckin = (date: string) => {
    return request<CheckinResult>({
        url: '/user/checkin/makeup',
        method: 'POST',
        params: { date }
    });
};
export const getUserPoints = () => {
    return request<number>({
        url: '/user/checkin/points',
        method: 'GET'
    }).catch((error: any) => {
        if (error?.isAuthError || error?.code === 401 || error?.code === 1004) {
            return { code: 200, data: 0, message: '未登录' };
        }
        throw error;
    });
};
export interface VipExchangePackage {
    packageCode: string;
    packageName: string;
    vipDays: number;
    costPoints: number;
    ruleVersion: number;
}
export interface VipExchangeResult extends VipExchangePackage {
    orderId: string | number;
    requestId: string;
    vipLevel: number;
    beforePoints: number;
    afterPoints: number;
    status: 'granted';
    replayed: boolean;
}
export const getVipExchangePackages = () => {
    return request<VipExchangePackage[]>({
        url: '/user/checkin/vip-packages',
        method: 'GET'
    });
};
export const redeemVip = (packageCode: string, requestId: string) => {
    return request<VipExchangeResult>({
        url: '/user/checkin/redeem-vip',
        method: 'POST',
        data: { packageCode, requestId }
    });
};
export interface SigninAchievement {
    id: string | number;
    days: number;
    achievementName: string;
    achievementDesc: string;
    rewardPoints: number;
    rewardVipDays: number;
    rewardDecorationId?: string | number;
    rewardBadgeId?: string | number;
    iconUrl?: string;
    badgeUrl?: string;
}
export interface SigninAchievementProgress {
    achievement: SigninAchievement;
    unlocked: boolean;
    rewarded: boolean;
    canClaim: boolean;
    accountUnavailable: boolean;
    accountUnavailableMessage?: string;
    rewardTime?: string;
    progress: number;
}
export interface ClaimRewardResult {
    success: boolean;
    message: string;
    rewards?: string[];
    rewardPoints?: number;
    rewardVipDays?: number;
}
export const getSigninAchievements = () => {
    return request<SigninAchievementProgress[]>({
        url: '/user/signin-achievement/list',
        method: 'GET'
    });
};
export const claimSigninReward = (achievementId: string | number) => {
    return request<ClaimRewardResult>({
        url: `/user/signin-achievement/claim/${achievementId}`,
        method: 'POST'
    });
};
