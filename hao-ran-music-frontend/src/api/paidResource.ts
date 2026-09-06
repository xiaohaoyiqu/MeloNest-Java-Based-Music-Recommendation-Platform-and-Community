import { request } from '@/utils/request';
export type ResourceType = 'song' | 'album' | 'playlist' | 'mv';
export type ConfigurablePaidResourceType = Exclude<ResourceType, 'playlist'>;
export type PaidResourceStatus = 'pending' | 'approved' | 'rejected';
export type ChangeType = 'create' | 'update_price' | 'cancel';
export interface PaidResource {
    id: string | number;
    ownerId: string | number;
    resourceType: ResourceType;
    resourceId: string | number;
    price: string | number;
    subscribePeriod: number | null;
    isEnabled: 0 | 1;
    status: PaidResourceStatus;
    salesCount: number;
    totalEarnings: string | number;
    changeType: string;
    changeReason?: string;
    createTime: string;
    updateTime: string;
}
export interface PaidResourceSummary {
    resourceType: ResourceType;
    resourceId: string | number;
    price: string | number;
    subscribePeriod: number | null;
    type: 'detail' | 'list';
}
export interface PriceRange {
    minPrice: string | number;
    maxPrice: string | number;
    avgPrice: string | number;
    recommendPrice: string | number;
}
export interface PaidResourceActionResult {
    id?: string | number;
    resourceId?: string | number;
    resourceType?: ResourceType;
    ownerId?: string | number;
    price?: string | number;
    isEnabled?: 0 | 1;
    approved?: boolean;
    status?: PaidResourceStatus;
    changeReviewStatus?: 'pending' | 'approved' | 'rejected' | 'cancelled' | null;
    reviewType?: 'initial' | 'change';
    salesCount?: number;
    totalEarnings?: string | number;
    subscribePeriod?: number | null;
    type?: 'created' | 'updated';
    message?: string;
}
export interface PurchaseResourceResult {
    orderId: string | number;
    orderNo?: string;
    amount: string | number;
    resourceInfo: PaidResourceSummary;
    message?: string;
}
export interface PageResult<T> {
    records: T[];
    total: number;
    current: number;
    size: number;
    pages: number;
}
export interface PurchasedResourceRecord {
    id?: string | number;
    resourceType?: ResourceType;
    resourceId?: string | number;
    name?: string;
    cover?: string;
    description?: string;
    songCount?: number;
    playCount?: number;
    creatorName?: string;
    creatorAvatar?: string;
    purchaseTime?: string;
    expireTime?: string;
}
export function setPaidResource(resourceType: ConfigurablePaidResourceType, resourceId: string | number, price: number, subscribePeriod?: number, changeType?: ChangeType, changeReason?: string) {
    return request<PaidResourceActionResult>({
        url: '/paid-resource/set',
        method: 'POST',
        params: { resourceType, resourceId, price, subscribePeriod, changeType, changeReason }
    });
}
export function cancelPaidResource(id: string | number, resourceType: ResourceType, resourceId: string | number) {
    return request<boolean>({
        url: `/paid-resource/${id}`,
        method: 'DELETE',
        params: { resourceType, resourceId }
    });
}
export function getMyPaidResources(page = 1, size = 20) {
    return request<PageResult<PaidResource>>({
        url: '/paid-resource/my',
        method: 'GET',
        params: { page, size }
    });
}
export function getPaidResourceDetail(resourceType: ResourceType, resourceId: string | number) {
    return request<PaidResourceSummary>({
        url: '/paid-resource/detail',
        method: 'GET',
        params: { resourceType, resourceId }
    });
}
export function checkPurchased(resourceType: ResourceType, resourceId: string | number) {
    return request<boolean>({
        url: '/paid-resource/check',
        method: 'GET',
        params: { resourceType, resourceId }
    });
}
export function purchaseResource(resourceType: ResourceType, resourceId: string | number, idempotencyKey: string) {
    return request<PurchaseResourceResult>({
        url: '/paid-resource/buy',
        method: 'POST',
        params: { resourceType, resourceId },
        headers: { 'Idempotency-Key': idempotencyKey }
    });
}
export function getPaidResourceList(resourceType?: ResourceType, page = 1, size = 20) {
    return request<PageResult<PaidResourceSummary>>({
        url: '/paid-resource/list',
        method: 'GET',
        params: { resourceType, page, size }
    });
}
export function reviewPaidResource(id: string | number, approved: boolean, reviewReason?: string) {
    return request<PaidResourceActionResult>({
        url: `/paid-resource/review/${id}`,
        method: 'POST',
        params: { approved, reviewReason }
    });
}
export function getPendingPaidResources(page = 1, size = 20) {
    return request<PageResult<PaidResource>>({
        url: '/paid-resource/pending',
        method: 'GET',
        params: { page, size }
    });
}
export function getUserPurchasedResources(resourceType?: ResourceType, page = 1, size = 20) {
    return request<PageResult<PurchasedResourceRecord>>({
        url: '/paid-resource/purchased',
        method: 'GET',
        params: { resourceType, page, size }
    });
}
export function getPriceRange(resourceType: ResourceType) {
    return request<PriceRange>({
        url: `/paid-resource/price-range/${resourceType}`,
        method: 'GET'
    });
}
export const paidResourceApi = {
    setPaidResource,
    cancelPaidResource,
    getMyPaidResources,
    getPaidResourceDetail,
    checkPurchased,
    purchaseResource,
    getPaidResourceList,
    reviewPaidResource,
    getPendingPaidResources,
    getUserPurchasedResources,
    getPriceRange
};
