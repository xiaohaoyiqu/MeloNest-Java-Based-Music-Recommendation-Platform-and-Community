import { request } from '@/utils/request';
export function disableUser(userId: string | number) {
    return request<void>({
        url: `/admin/user/disable/${userId}`,
        method: 'POST'
    });
}
export function enableUser(userId: string | number) {
    return request<void>({
        url: `/admin/user/enable/${userId}`,
        method: 'POST'
    });
}
export function batchDisableUsers(userIds: Array<string | number>) {
    return request<{
        successCount: number;
        total: number;
    }>({
        url: '/admin/user/batch/disable',
        method: 'POST',
        data: userIds
    });
}
export function batchEnableUsers(userIds: Array<string | number>) {
    return request<{
        successCount: number;
        total: number;
    }>({
        url: '/admin/user/batch/enable',
        method: 'POST',
        data: userIds
    });
}
export function updateUserRole(userId: string | number, role: string) {
    return request<void>({
        url: `/admin/user/role/${userId}`,
        method: 'POST',
        params: { role }
    });
}
export function batchUpdateUserRole(userIds: Array<string | number>, role: string) {
    return request<{
        successCount: number;
        total: number;
    }>({
        url: '/admin/user/batch/role',
        method: 'POST',
        data: userIds,
        params: { role }
    });
}
export function deleteUser(userId: string | number) {
    return request<void>({
        url: `/admin/user/${userId}`,
        method: 'DELETE'
    });
}
export function batchDeleteUsers(userIds: Array<string | number>) {
    return request<{
        successCount: number;
        total: number;
    }>({
        url: '/admin/user/batch',
        method: 'DELETE',
        data: userIds
    });
}
export function resetUserPassword(userId: string | number, newPassword: string) {
    return request<void>({
        url: `/admin/user/reset-password/${userId}`,
        method: 'POST',
        params: { newPassword }
    });
}
export function editUserInfo(userId: string | number, params: Record<string, any>) {
    return request<void>({
        url: `/admin/user/edit/${userId}`,
        method: 'PUT',
        data: params
    });
}
export function getUserDetail(userId: string | number) {
    return request<any>({
        url: `/admin/user/detail/${userId}`,
        method: 'GET'
    });
}
export const adminUserApi = {
    disableUser,
    enableUser,
    batchDisableUsers,
    batchEnableUsers,
    updateUserRole,
    batchUpdateUserRole,
    deleteUser,
    batchDeleteUsers,
    resetUserPassword,
    editUserInfo,
    getUserDetail
};
