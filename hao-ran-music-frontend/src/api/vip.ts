import { request } from '@/utils/request';
export interface VipUserInfo {
    userId: string;
    vipLevel: string;
    vipLevelName: string;
    vipStatus: string;
    vipStatusName: string;
    vipStartTime: string;
    vipExpireTime: string;
    remainingDays: string;
    vip: string;
    lifetimeVip: string;
    autoRenew: string;
    totalVipDays: string;
    totalSpending: string;
    privileges: string[];
}
export interface VipPurchaseRequest {
    vipLevel?: string | number;
    paymentMethod: 'alipay' | 'wechat' | 'credit';
    amount?: number;
}
export interface VipLevelInfo {
    level: number;
    name: string;
    price: number;
    duration: string;
    privileges: string[];
}
export interface VipPrivileges {
    levels: VipLevelInfo[];
    currentPrivileges?: string[];
    purchaseAvailable?: boolean;
    autoRenewEnableAvailable?: boolean;
    paymentMessage?: string;
}
export function getVipInfo() {
    return request<VipUserInfo>({
        url: '/user/vip/info',
        method: 'GET'
    });
}
export function purchaseVip(data: VipPurchaseRequest) {
    return request<VipUserInfo>({
        url: '/user/vip/purchase',
        method: 'POST',
        data
    });
}
export function renewVip() {
    return request<VipUserInfo>({
        url: '/user/vip/renew',
        method: 'POST'
    });
}
export function setAutoRenew(autoRenew: boolean, cycle?: number) {
    return request<void>({
        url: '/user/vip/auto-renew',
        method: 'POST',
        data: { autoRenew, cycle }
    });
}
export function getVipPrivileges(level = 0) {
    return request<VipPrivileges>({
        url: '/user/vip/privileges',
        method: 'GET',
        params: { level }
    });
}
export function checkVipStatus() {
    return request<{
        isVip: boolean;
        vipLevel: number;
    }>({
        url: '/user/vip/check',
        method: 'GET'
    });
}
export function getMySubscribes(page = 1, size = 20) {
    return request<{
        records: any[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/subscribe/my',
        method: 'GET',
        params: { page, size }
    });
}
export async function getMyPurchasedResources() {
    const response = await request<{
        records?: any[];
        list?: any[];
        total?: number;
    }>({
        url: '/paid-resource/purchased',
        method: 'GET',
        params: { page: 1, size: 20 }
    });
    const list = response.data?.records || response.data?.list || [];
    return {
        ...response,
        data: {
            list,
            total: response.data?.total || 0
        }
    };
}
export const vipApi = {
    getVipInfo,
    purchaseVip,
    renewVip,
    setAutoRenew,
    updateAutoRenew: setAutoRenew,
    getVipPrivileges,
    checkVipStatus,
    getMySubscribes,
    getMyPurchasedResources
};
