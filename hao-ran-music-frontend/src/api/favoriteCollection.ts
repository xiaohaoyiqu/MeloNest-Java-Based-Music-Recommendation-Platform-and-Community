import { request } from '@/utils/request';
export type FavoriteResourceType = 'song' | 'album' | 'mv' | 'playlist';
export interface FavoriteGroup {
    id: string | number;
    name: string;
    sortOrder: number;
    itemCount: number;
    createTime?: string;
    updateTime?: string;
}
export interface FavoriteGroupItem {
    groupId: string | number;
    resourceType: FavoriteResourceType;
    resourceId: string | number;
}
export interface FavoriteGroupState {
    groups: FavoriteGroup[];
    items: FavoriteGroupItem[];
}
export function getFavoriteGroupState() {
    return request<FavoriteGroupState>({ url: '/favorite-groups', method: 'GET' });
}
export function createFavoriteGroup(name: string) {
    return request<FavoriteGroup>({ url: '/favorite-groups', method: 'POST', data: { name } });
}
export function renameFavoriteGroup(groupId: string | number, name: string) {
    return request<void>({ url: `/favorite-groups/${groupId}`, method: 'PUT', data: { name } });
}
export function reorderFavoriteGroups(groupIds: Array<string | number>) {
    return request<void>({ url: '/favorite-groups/order', method: 'PUT', data: { groupIds } });
}
export function deleteFavoriteGroup(groupId: string | number) {
    return request<void>({ url: `/favorite-groups/${groupId}`, method: 'DELETE' });
}
export function assignFavoriteGroup(groupId: string | number, resourceType: FavoriteResourceType, resourceId: string | number) {
    return request<void>({
        url: `/favorite-groups/${groupId}/items`,
        method: 'PUT',
        data: { resourceType, resourceId }
    });
}
export function removeFavoriteGroupItem(resourceType: FavoriteResourceType, resourceId: string | number) {
    return request<void>({
        url: `/favorite-groups/items/${resourceType}/${resourceId}`,
        method: 'DELETE'
    });
}
