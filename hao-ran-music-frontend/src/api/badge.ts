import { request } from '@/utils/request';
export interface UserBadge {
    id: string;
    userId: string;
    badgeType: string;
    badgeName: string;
    badgeIcon: string;
    badgeColor: string;
    position: string;
    expireTime: string;
    remainingDays: number;
    isEquipped: boolean;
    description?: string;
    obtainMethod?: string;
    rarity?: string;
    createTime?: string;
}
export type BadgeRarity = 'common' | 'rare' | 'epic' | 'legendary';
export interface BadgeStats {
    totalBadges: number;
    totalConfigBadges: number;
    completionRate: string;
    categoryStats: Record<string, number>;
    rarityStats: Record<string, number>;
}
export type BadgeType = 'achievement' | 'vip' | 'creator' | 'custom';
export function getUserBadges(userId: string) {
    return request<UserBadge[]>({
        url: `/user/badge/list/${userId}`,
        method: 'GET'
    });
}
export function getMyBadges() {
    return request<UserBadge[]>({
        url: '/user/badge/my',
        method: 'GET'
    });
}
export function getAvailableBadges() {
    return request<UserBadge[]>({
        url: '/user/badge/available',
        method: 'GET'
    });
}
export function getMyBadgeStats() {
    return request<BadgeStats>({
        url: '/badge/my-stats',
        method: 'GET'
    });
}
export function getBadgeShop() {
    return request<UserBadge[]>({
        url: '/badge/shop',
        method: 'GET'
    });
}
export function calculateAchievementBadges() {
    return request<UserBadge[]>({
        url: '/badge/calculate-achievements',
        method: 'POST'
    });
}
export function calculateBadges(userId: string) {
    return request<UserBadge[]>({
        url: `/user/badge/calculate/${userId}`,
        method: 'POST'
    });
}
export function setBadgeEquip(badgeId: string, equip: boolean) {
    return request<void>({
        url: `/user/badge/equip/${badgeId}`,
        method: 'PUT',
        params: { equip }
    });
}
export const badgeApi = {
    getUserBadges,
    getMyBadges,
    getAvailableBadges,
    getMyBadgeStats,
    getBadgeShop,
    calculateAchievementBadges,
    calculateBadges,
    setBadgeEquip
};
