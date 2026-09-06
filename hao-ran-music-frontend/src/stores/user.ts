import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { UserInfo } from '@/api';
import { getCurrentUserInfo, logout as apiLogout } from '@/api';
import { getCurrentAccountUnavailableMessage, getUserAccountStatus } from '@/utils/userAccountStatus';
import { logger } from '@/utils/logger';
export const useUserStore = defineStore('user', () => {
    const token = ref<string>('');
    const userInfo = ref<UserInfo | null>(null);
    const vipLevel = ref<number>(0);
    const isVip = ref<boolean>(false);
    const isLogin = computed(() => !!userInfo.value);
    const accountStatus = computed(() => getUserAccountStatus(userInfo.value));
    const isAccountUnavailable = computed(() => accountStatus.value.unavailable);
    const canInteract = computed(() => isLogin.value && !isAccountUnavailable.value);
    const accountUnavailableMessage = computed(() => getCurrentAccountUnavailableMessage(userInfo.value));
    const userId = computed(() => userInfo.value?.id);
    const nickname = computed(() => userInfo.value?.nickname || '');
    const avatar = computed(() => userInfo.value?.avatar || '');
    function setToken(newToken: string) {
        token.value = '';
        localStorage.removeItem('token');
    }
    function setUserInfo(info: UserInfo) {
        userInfo.value = info;
    }
    function clearUserInfo() {
        token.value = '';
        userInfo.value = null;
        vipLevel.value = 0;
        isVip.value = false;
        localStorage.removeItem('token');
    }
    if (typeof window !== 'undefined') {
        window.addEventListener('haoran:auth-expired', clearUserInfo);
    }
    function setVipStatus(level: number, vip: boolean) {
        vipLevel.value = level;
        isVip.value = vip;
    }
    async function fetchUserInfo() {
        try {
            const res = await getCurrentUserInfo();
            setUserInfo(res.data);
            return res.data;
        }
        catch (error) {
            clearUserInfo();
            throw error;
        }
    }
    async function ensureUserInfo(): Promise<UserInfo | null> {
        if (!userInfo.value) {
            try {
                logger.debug('user_info_refresh_started');
                const info = await fetchUserInfo();
                logger.debug('user_info_refresh_completed');
                return info;
            }
            catch (error) {
                logger.error('user_info_refresh_failed', error);
                return null;
            }
        }
        return userInfo.value;
    }
    async function logout() {
        try {
            await apiLogout();
        }
        finally {
            clearUserInfo();
            localStorage.removeItem('autoLogin');
        }
    }
    function updateUserInfo(partialInfo: Partial<UserInfo>) {
        if (userInfo.value) {
            Object.assign(userInfo.value, partialInfo);
        }
    }
    return {
        token,
        userInfo,
        isLogin,
        accountStatus,
        isAccountUnavailable,
        canInteract,
        accountUnavailableMessage,
        userId,
        nickname,
        avatar,
        vipLevel,
        isVip,
        setToken,
        setUserInfo,
        clearUserInfo,
        setVipStatus,
        fetchUserInfo,
        ensureUserInfo,
        logout,
        updateUserInfo
    };
});
