export const STORE_PRODUCT_UPDATED_EVENT = 'haoran:store-product-updated';
export interface StoreProductUpdatedDetail {
    productType: 'emoji_package' | 'decoration';
    productId: string;
}
export function publishStoreProductUpdated(detail: StoreProductUpdatedDetail) {
    window.dispatchEvent(new CustomEvent<StoreProductUpdatedDetail>(STORE_PRODUCT_UPDATED_EVENT, { detail }));
}
