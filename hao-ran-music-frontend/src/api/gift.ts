import request from '@/utils/request';
import type { PageResult } from './types';
export type GiftType = 'vip' | 'marketplace_item';
export type GiftStatus = 'pending_payment' | 'paid' | 'completed' | 'failed' | 'blocked' | string;
export type VipGiftType = 'month' | 'quarter' | 'year';
export interface GiftOrderVO {
    giftOrderId: string | number;
    giftNo?: string;
    giftType: GiftType | string;
    giverId?: string | number;
    receiverId?: string | number;
    targetType?: string;
    targetId?: string | number;
    targetName?: string;
    amount?: number;
    currency?: string;
    status: GiftStatus;
    giftMessage?: string;
    paymentOrderId?: string | number;
    createTime?: string;
    updateTime?: string;
}
export interface GiftCreateResult extends GiftOrderVO {
    paymentOrderId: string | number;
}
export interface VipGiftCreateDTO {
    receiverId: string | number;
    vipType: VipGiftType | string;
    giftMessage?: string;
}
export interface MarketplaceGiftCreateDTO {
    receiverId: string | number;
    itemId: string | number;
    giftMessage?: string;
}
export function createVipGift(data: VipGiftCreateDTO) {
    return request<GiftCreateResult>({
        url: '/gift/vip',
        method: 'post',
        data
    });
}
export function createMarketplaceGift(data: MarketplaceGiftCreateDTO) {
    return request<GiftCreateResult>({
        url: '/gift/marketplace',
        method: 'post',
        data
    });
}
export function getGiftDetail(giftOrderId: string | number) {
    return request<GiftOrderVO>({
        url: `/gift/${giftOrderId}`,
        method: 'get'
    });
}
export function getSentGifts(status?: string, page = 1, size = 20) {
    return request<PageResult<GiftOrderVO>>({
        url: '/gift/sent',
        method: 'get',
        params: { status, page, size }
    });
}
export function getReceivedGifts(status?: string, page = 1, size = 20) {
    return request<PageResult<GiftOrderVO>>({
        url: '/gift/received',
        method: 'get',
        params: { status, page, size }
    });
}
export const giftApi = {
    createVipGift,
    createMarketplaceGift,
    getGiftDetail,
    getSentGifts,
    getReceivedGifts
};
