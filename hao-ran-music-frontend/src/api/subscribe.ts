import { request } from '@/utils/request';
export type SubscribeStatus = 'active' | 'expired' | 'cancelled';
export type EntityId = string | number;
export type DecimalAmount = string | number;
export type SubscribeType = 'month' | 'quarter' | 'year';
export interface SubscribeRecord {
    id: EntityId;
    userId?: EntityId;
    playlistId: EntityId;
    playlistTitle?: string;
    playlistCover?: string;
    creatorId?: EntityId;
    creatorName?: string;
    subscribeType: SubscribeType;
    price: DecimalAmount;
    startTime: string | null;
    endTime: string | null;
    autoRenew: boolean;
    status: SubscribeStatus;
}
export interface SubscribeCheckResult {
    subscribed: boolean;
    expiresAt: string | null;
    subscribeType?: SubscribeType;
    autoRenew?: boolean;
    accountAvailable: boolean;
}
export interface SubscribeSetupResult {
    success: boolean;
    playlistId: EntityId;
    price: DecimalAmount;
    period: number;
    status: 'pending';
    platformFee: DecimalAmount;
    creatorEarning: DecimalAmount;
    message: string;
}
export interface SubscribeOrderResult {
    orderId: EntityId;
    subscribeOrderId: EntityId;
    status: string;
    canCancel?: boolean;
    canSubmitProof?: boolean;
    remainingTime?: number;
    currency?: string;
    idempotentReplay?: boolean;
}
export interface SubscribePage<T> {
    records: T[];
    total: number;
    current: number;
    size: number;
    pages: number;
}
interface RawSubscribePage<T> {
    records?: T[];
    list?: T[];
    total?: number;
    current?: number;
    page?: number;
    size?: number;
    pages?: number;
}
function normalizePage<T>(payload: RawSubscribePage<T> | undefined, fallbackPage: number, fallbackSize: number): SubscribePage<T> {
    const records = payload?.records ?? payload?.list ?? [];
    const total = payload?.total ?? 0;
    const current = payload?.current ?? payload?.page ?? fallbackPage;
    const size = payload?.size ?? fallbackSize;
    return {
        records,
        total,
        current,
        size,
        pages: payload?.pages ?? (size > 0 ? Math.ceil(total / size) : 0)
    };
}
export interface SubscribeStatistics {
    totalSubscribers: number;
    activeSubscribers: number;
    totalRevenue: string | number;
    monthlyRevenue: string | number;
    averageRevenue: string | number;
    renewRate: number;
}
export interface SubscriberInfo {
    userId: EntityId;
    userName?: string;
    userAvatar?: string;
    subscribeType: SubscribeType;
    startTime: string | null;
    endTime: string | null;
    autoRenew: boolean;
}
export interface SubscribedPlaylistRecord {
    id: EntityId;
    name: string;
    cover?: string;
    description?: string;
    songCount: number;
    playCount?: number | string;
    subscribeType: SubscribeType;
    endTime: string | null;
    autoRenew: boolean;
    creatorName?: string;
    creatorAvatar?: string;
}
export function setPlaylistSubscribe(playlistId: string | number, price: number, period: number) {
    return request<SubscribeSetupResult>({
        url: '/subscribe/set',
        method: 'POST',
        params: { playlistId, price, period }
    });
}
export function subscribePlaylist(playlistId: string | number, idempotencyKey: string, subscribeType?: SubscribeType, autoRenew?: boolean) {
    return request<SubscribeOrderResult>({
        url: `/subscribe/${playlistId}`,
        method: 'POST',
        params: { subscribeType, autoRenew },
        headers: { 'Idempotency-Key': idempotencyKey }
    });
}
export function checkSubscribed(id: string | number) {
    return request<SubscribeCheckResult>({
        url: `/subscribe/check/${id}`,
        method: 'GET'
    });
}
export async function getMySubscribes(status?: SubscribeStatus, page = 1, size = 20) {
    const response = await request<RawSubscribePage<SubscribeRecord>>({
        url: '/subscribe/my',
        method: 'GET',
        params: { status, page, size }
    });
    return { ...response, data: normalizePage(response.data, page, size) };
}
export async function getPlaylistSubscribers(playlistId: string | number, page = 1, size = 20) {
    const response = await request<RawSubscribePage<SubscriberInfo>>({
        url: '/subscribe/subscribers',
        method: 'GET',
        params: { playlistId, page, size }
    });
    return { ...response, data: normalizePage(response.data, page, size) };
}
export function getPlaylistSubscribeStats(playlistId: string | number) {
    return request<SubscribeStatistics>({
        url: '/subscribe/stats',
        method: 'GET',
        params: { playlistId }
    });
}
export function cancelSubscribe(playlistId: string | number) {
    return request<boolean>({
        url: `/subscribe/${playlistId}/cancel`,
        method: 'POST'
    });
}
export function renewSubscribe(playlistId: string | number, idempotencyKey: string, subscribeType?: SubscribeType) {
    return request<SubscribeOrderResult>({
        url: `/subscribe/${playlistId}/renew`,
        method: 'POST',
        params: { subscribeType },
        headers: { 'Idempotency-Key': idempotencyKey }
    });
}
export function cancelAutoRenew(playlistId: string | number) {
    return request<boolean>({
        url: `/subscribe/${playlistId}/auto-renew/disable`,
        method: 'POST'
    });
}
export function enableAutoRenew(playlistId: string | number) {
    return request<boolean>({
        url: `/subscribe/${playlistId}/auto-renew/enable`,
        method: 'POST'
    });
}
export function cancelPlaylistSubscribe(playlistId: string | number) {
    return request<boolean>({
        url: `/subscribe/${playlistId}`,
        method: 'DELETE'
    });
}
export function getExpiringSubscribes(days = 3) {
    return request<{
        records: SubscribeRecord[];
        total: number;
    }>({
        url: '/subscribe/expiring',
        method: 'GET',
        params: { days }
    });
}
export async function getMySubscribedPlaylists(page = 1, size = 20) {
    const response = await request<RawSubscribePage<SubscribedPlaylistRecord> & {
        accountAvailable?: boolean;
    }>({
        url: '/subscribe/subscribed-playlists',
        method: 'GET',
        params: { page, size }
    });
    return {
        ...response,
        data: {
            ...normalizePage(response.data, page, size),
            accountAvailable: response.data?.accountAvailable ?? true
        }
    };
}
export const subscribeApi = {
    setPlaylistSubscribe,
    getMySubscribedPlaylists,
    subscribePlaylist,
    checkSubscribed,
    getMySubscribes,
    getPlaylistSubscribers,
    getPlaylistSubscribeStats,
    cancelSubscribe,
    renewSubscribe,
    cancelAutoRenew,
    enableAutoRenew,
    cancelPlaylistSubscribe,
    getExpiringSubscribes
};
