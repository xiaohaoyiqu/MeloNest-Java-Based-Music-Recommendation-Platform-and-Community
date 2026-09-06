import { request } from '@/utils/request';
import { logger as frontendLogger } from '@/utils/logger';
export type DecorationType = 'avatar_frame' | 'comment_bar' | 'player' | 'dialog_box' | 'theme' | 'badge';
export type DecorationRarity = 'common' | 'rare' | 'epic' | 'legendary';
export type ObtainType = 'sign' | 'activity' | 'vip' | 'achievement' | 'points' | 'cash';
export type DecorationReviewStatus = 'draft' | 'pending' | 'approved' | 'rejected';
export interface DecorationCreatorInput {
    decorationName: string;
    decorationType: DecorationType;
    description: string;
    iconUrl: string;
    previewUrl: string;
    styleConfig: string;
    rarity: DecorationRarity;
    obtainType: 'points' | 'cash';
    pointsCost: number;
    cashPrice: number;
    isPermanent: 0 | 1;
    durationDays?: number;
}
export interface DecorationWork extends DecorationCreatorInput {
    id: string | number;
    decorationId: string;
    creatorId: string | number;
    sourceType: 'custom';
    reviewStatus: DecorationReviewStatus;
    reviewReason?: string;
    contentVersion: number;
    isEnabled: number;
    submitTime?: string;
    reviewTime?: string;
    updateTime?: string;
}
export interface UserDecoration {
    id: string | number;
    userId?: string | number;
    decorationId: string | number;
    decorationType?: string;
    type?: string;
    name?: string;
    decorationName?: string;
    rarity?: string;
    iconUrl?: string;
    badgeIcon?: string;
    previewUrl?: string;
    description?: string;
    isEquipped: boolean;
    obtainTime?: string;
    expireTime?: string;
    obtainMethod?: string;
}
export interface DecorationDTO {
    configId?: string | number;
    decorationId: string;
    decorationName: string;
    decorationType: string;
    description: string;
    iconUrl: string;
    previewUrl: string;
    styleConfig: Record<string, any>;
    rarity: string;
    pointsCost: number;
    cashPrice: number;
    obtainType?: ObtainType;
    permanent?: boolean;
    durationDays?: number;
    canRedeem: boolean;
    obtainDescription: string;
}
export interface DecorationTypeInfo {
    type: string;
    name: string;
    description: string;
}
export function getUserDecorations() {
    return request<Record<string, UserDecoration[]>>({
        url: '/decoration/user/list',
        method: 'GET'
    }).catch((error: any) => {
        frontendLogger.capture('debug', '获取用户装饰失败，返回空对象:', error);
        const emptyResult: Record<string, UserDecoration[]> = {};
        emptyResult.badge = [];
        emptyResult.avatar_frame = [];
        emptyResult.comment_bar = [];
        emptyResult.player = [];
        emptyResult.dialog_box = [];
        emptyResult.theme = [];
        return { code: 200, data: emptyResult, message: '未登录' };
    });
}
export function getEquippedDecorations() {
    return request<Record<string, UserDecoration>>({
        url: '/decoration/user/equipped',
        method: 'GET'
    }).catch((error: any) => {
        frontendLogger.capture('debug', '获取装饰失败，返回空对象:', error);
        const emptyResult: Record<string, UserDecoration> = {};
        emptyResult.badge = null as any;
        emptyResult.avatar_frame = null as any;
        emptyResult.comment_bar = null as any;
        emptyResult.player = null as any;
        emptyResult.dialog_box = null as any;
        emptyResult.theme = null as any;
        if (error?.isAuthError) {
            return { code: 1004, data: emptyResult, message: '未登录' };
        }
        return { code: 200, data: emptyResult, message: '获取装饰失败' };
    });
}
export function getDecorationShop(type?: DecorationType) {
    return request<DecorationDTO[]>({
        url: '/decoration/shop',
        method: 'GET',
        params: { type }
    });
}
export function getDecorationByType(type: DecorationType) {
    return request<DecorationDTO[]>({
        url: `/decoration/shop/${type}`,
        method: 'GET'
    });
}
export function getDecorationDetail(decorationId: string | number) {
    return request<DecorationDTO>({
        url: `/decoration/detail/${decorationId}`,
        method: 'GET'
    });
}
export function equipDecoration(decorationId: string | number) {
    return request<boolean>({
        url: '/decoration/equip',
        method: 'POST',
        params: { decorationId }
    });
}
export function unequipDecoration(decorationType: DecorationType) {
    return request<boolean>({
        url: '/decoration/unequip',
        method: 'POST',
        params: { decorationType }
    });
}
export function redeemDecoration(decorationId: string | number) {
    return request<boolean>({
        url: '/decoration/redeem',
        method: 'POST',
        params: { decorationId }
    });
}
export function getUserPoints() {
    return request<number>({
        url: '/decoration/user/points',
        method: 'GET'
    });
}
export function getDecorationTypes() {
    return request<DecorationTypeInfo[]>({
        url: '/decoration/types',
        method: 'GET'
    });
}
export function getMyDecorationWorks() {
    return request<DecorationWork[]>({ url: '/decoration/creator/my', method: 'GET' });
}
export function createDecorationDraft(data: DecorationCreatorInput) {
    return request<string | number>({ url: '/decoration/creator', method: 'POST', data });
}
export function updateDecorationDraft(decorationId: string | number, data: DecorationCreatorInput) {
    return request<void>({ url: `/decoration/creator/${decorationId}`, method: 'PUT', data });
}
export function submitDecorationWork(decorationId: string | number) {
    return request<void>({ url: `/decoration/creator/${decorationId}/submit`, method: 'POST' });
}
export function getDecorationReviewDetail(decorationId: string | number) {
    return request<DecorationWork>({ url: `/decoration/creator/${decorationId}/review-detail`, method: 'GET' });
}
export function uploadDecorationMaterial(file: File) {
    const data = new FormData();
    data.append('file', file);
    return request<string>({
        url: '/decoration/creator/upload',
        method: 'POST',
        data,
        headers: { 'Content-Type': 'multipart/form-data' }
    });
}
export const decorationApi = {
    getUserDecorations,
    getEquippedDecorations,
    getDecorationShop,
    getDecorationByType,
    getDecorationDetail,
    equipDecoration,
    unequipDecoration,
    redeemDecoration,
    getUserPoints,
    getDecorationTypes
};
export default decorationApi;
