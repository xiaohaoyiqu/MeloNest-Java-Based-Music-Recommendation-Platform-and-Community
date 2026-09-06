import { request } from '@/utils/request';
export type StoreProductType = 'emoji_package' | 'decoration';
export type StoreProductAction = 'off_sale' | 'on_sale' | 'unlisted' | 'public' | 'hide' | 'restore' | 'retain_entitlement' | 'block_entitlement' | 'revoke_entitlement' | 'hold_settlement' | 'resume_settlement';
export interface StoreProductPolicy {
    id?: number | string;
    productType: StoreProductType;
    productId: number | string;
    sellerId?: number | string;
    saleStatus: 'on_sale' | 'off_sale_owner' | 'off_sale_admin';
    visibility: 'public' | 'unlisted' | 'hidden';
    entitlementPolicy: 'retain' | 'temporarily_blocked' | 'revoked';
    settlementStatus: 'normal' | 'hold';
    version: number;
    reason?: string;
}
export interface StoreProduct extends Omit<StoreProductPolicy, 'id' | 'version'> {
    name: string;
    coverUrl?: string;
    purchaseMode?: 'free' | 'points' | 'cash' | string;
    pointsPrice?: number;
    cashPrice?: number;
    itemCount?: number;
    itemLimit?: number;
    reviewStatus?: string;
    effectiveStatus: 'available' | 'account_restricted' | string;
    updateTime?: string;
}
export interface StoreProductPage {
    list: StoreProduct[];
    total: number;
    page: number;
    size: number;
}
export interface StoreVipPlan {
    productId: 1 | 3 | 12;
    type: 'month' | 'quarter' | 'year';
    name: string;
    days: number;
    price: number;
    recommended: boolean;
}
export function getStoreVipPlans() {
    return request<StoreVipPlan[]>({ url: '/store/product/vip-plans', method: 'GET' });
}
export function getStoreProduct(productType: StoreProductType, productId: string | number) {
    return request<StoreProduct>({ url: `/store/product/${productType}/${productId}`, method: 'GET' });
}
export function changeOwnerProductPolicy(productType: StoreProductType, productId: string | number, action: StoreProductAction, reason?: string) {
    return request<StoreProductPolicy>({
        url: `/store/product/${productType}/${productId}/owner-policy`,
        method: 'PUT',
        data: { action, reason }
    });
}
export function getAdminStoreProducts(params: {
    productType?: StoreProductType | '';
    keyword?: string;
    page?: number;
    size?: number;
}) {
    return request<StoreProductPage>({ url: '/admin/store/products', method: 'GET', params });
}
export function changeAdminProductPolicy(productType: StoreProductType, productId: string | number, action: StoreProductAction, reason?: string) {
    return request<StoreProductPolicy>({
        url: `/admin/store/product/${productType}/${productId}/policy`,
        method: 'PUT',
        data: { action, reason }
    });
}
