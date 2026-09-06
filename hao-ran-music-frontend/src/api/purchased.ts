import { request } from '@/utils/request';
export type PurchasedResourceType = 'song' | 'album' | 'playlist' | 'mv';
export interface PurchasedResource {
    id: string;
    userId: string;
    resourceType: string;
    resourceId: string | number;
    purchasePrice?: string | number;
    purchaseTime: string;
    expireTime: string;
    isPermanent: boolean;
    isExpired: boolean;
    remainingDays: number;
    resourceName: string;
    resourceCover: string;
    creatorInfo?: {
        id: string;
        name: string;
        avatar: string;
    };
}
export interface PurchaseRecord {
    id: string;
    orderId: string;
    orderNo: string;
    resourceType: string;
    resourceId: string | number;
    resourceName: string;
    resourceCover: string;
    purchasePrice: string | number;
    purchaseTime: string;
    expireTime: string;
    isPermanent: boolean;
    status: string;
}
export interface PurchaseStatistics {
    totalPurchases: number;
    activePurchases: number;
    expiringPurchases?: number;
    permanentPurchases: number;
}
interface PurchasedPagePayload {
    records?: Record<string, any>[];
    list?: Record<string, any>[];
    total?: number;
    current?: number;
    page?: number;
    size?: number;
    pages?: number;
}
function normalizePurchasedResource(record: Record<string, any>): PurchasedResource {
    const resourceId = record.resourceId ?? record.id;
    const resourceType = record.resourceType || 'playlist';
    const expireTime = record.expireTime || '';
    const expireTimestamp = expireTime ? new Date(expireTime).getTime() : Number.POSITIVE_INFINITY;
    const remainingDays = Number.isFinite(expireTimestamp)
        ? Math.max(0, Math.ceil((expireTimestamp - Date.now()) / 86400000))
        : 0;
    const creatorInfo = record.creatorName || record.creatorAvatar
        ? {
            id: String(record.creatorId || ''),
            name: record.creatorName || '',
            avatar: record.creatorAvatar || ''
        }
        : undefined;
    return {
        id: String(record.purchaseId ?? record.id),
        userId: String(record.userId || ''),
        resourceType,
        resourceId,
        purchasePrice: record.purchasePrice ?? record.price ?? '0',
        purchaseTime: record.purchaseTime || '',
        expireTime,
        isPermanent: !expireTime,
        isExpired: Boolean(expireTime && expireTimestamp <= Date.now()),
        remainingDays,
        resourceName: record.resourceName || record.name || `${resourceType} #${resourceId}`,
        resourceCover: record.resourceCover || record.cover || '',
        creatorInfo
    };
}
export async function getUserPurchasedResources(params: {
    resourceType?: PurchasedResourceType;
    page?: number;
    size?: number;
} = {}) {
    const response = await request<PurchasedPagePayload>({
        url: '/paid-resource/purchased',
        method: 'GET',
        params: {
            resourceType: params.resourceType,
            page: params.page || 1,
            size: params.size || 20
        }
    });
    const rows = response.data?.records || response.data?.list || [];
    const current = response.data?.current || response.data?.page || params.page || 1;
    const size = response.data?.size || params.size || 20;
    const total = response.data?.total || 0;
    return {
        ...response,
        data: {
            records: rows.map(normalizePurchasedResource),
            total,
            current,
            size,
            pages: response.data?.pages || Math.ceil(total / size)
        }
    };
}
export function getMyPaidPlaylists(page = 1, size = 20) {
    return request<{
        records: any[];
        total: number;
    }>({
        url: '/paid-resource/purchased',
        method: 'GET',
        params: { resourceType: 'playlist', page, size }
    });
}
export const purchasedApi = {
    getUserPurchasedResources,
    getMyPaidPlaylists
};
