import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { useUserStore } from '@/stores/user';
declare module 'vue-router' {
    interface RouteMeta {
        title?: string;
        public?: boolean;
        requireAuth?: boolean;
        roles?: readonly string[];
        adminOnly?: boolean;
    }
}
const ADMIN_ROLES = ['ADMIN', 'SUPER_ADMIN'] as const;
const MODERATION_ROLES = ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'] as const;
function normalizeRole(role?: string | null) {
    return String(role || '').trim().toUpperCase();
}
function hasAnyRole(role: string, requiredRoles: readonly string[]) {
    return requiredRoles.includes(normalizeRole(role));
}
const standaloneRoutes: RouteRecordRaw[] = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/Login/index.vue'),
        meta: { title: '登录', public: true }
    },
    {
        path: '/register',
        name: 'Register',
        component: () => import('@/views/Register/index.vue'),
        meta: { title: '注册', public: true }
    },
    {
        path: '/forgot-password',
        name: 'ForgotPassword',
        component: () => import('@/views/Password/Forgot.vue'),
        meta: { title: '忘记密码', public: true }
    },
    {
        path: '/404',
        name: 'NotFound',
        component: () => import('@/views/Error/404.vue'),
        meta: { title: '页面未找到', public: true }
    }
];
const mainRoutes: RouteRecordRaw[] = [
    {
        path: '/',
        name: 'Layout',
        component: () => import('@/layouts/BasicLayout.vue'),
        redirect: '/home',
        children: [
            {
                path: '/home',
                name: 'Home',
                component: () => import('@/views/Home/Index.vue'),
                meta: { title: '首页', public: true }
            },
            {
                path: '/discover',
                name: 'Discover',
                component: () => import('@/views/Discover/Index.vue'),
                meta: { title: '发现', public: true }
            },
            {
                path: '/discover-more',
                name: 'DiscoverMore',
                component: () => import('@/views/DiscoverPage.vue'),
                meta: { title: '远方窗口', public: true }
            },
            {
                path: '/settings',
                name: 'GeneralSettings',
                component: () => import('@/views/Settings/GeneralSettings.vue'),
                meta: { title: '通用设置', public: true }
            },
            {
                path: '/town-showcase',
                name: 'TownShowcase',
                component: () => import('@/views/TownShowcase.vue'),
                meta: { title: '小镇总橱窗', requireAuth: true }
            },
            {
                path: '/music-tags',
                name: 'MusicTags',
                component: () => import('@/views/Discover/MusicTags.vue'),
                meta: { title: '音乐标签', requireAuth: true }
            },
            {
                path: '/song/comments',
                redirect: to => {
                    const rawSongId = Array.isArray(to.query.songId) ? to.query.songId[0] : to.query.songId;
                    return rawSongId
                        ? { name: 'SongDetail', params: { id: String(rawSongId) }, hash: '#comments' }
                        : '/404';
                }
            },
            {
                path: '/song/:id',
                name: 'SongDetail',
                component: () => import('@/views/Song/Detail.vue'),
                meta: { title: '歌曲详情', public: true }
            },
            {
                path: '/new-songs',
                name: 'NewSongs',
                component: () => import('@/views/Song/NewSongs.vue'),
                meta: { title: '新歌速递', public: true }
            },
            {
                path: '/album/:id',
                name: 'AlbumDetail',
                component: () => import('@/views/Album/Detail.vue'),
                meta: { title: '专辑详情', public: true }
            },
            {
                path: '/albums',
                name: 'AlbumList',
                component: () => import('@/views/Album/List.vue'),
                meta: { title: '新碟上架', public: true }
            },
            {
                path: '/artist/:id',
                name: 'ArtistDetail',
                component: () => import('@/views/Artist/Detail.vue'),
                meta: { title: '歌手详情', public: true }
            },
            {
                path: '/artists',
                name: 'ArtistList',
                component: () => import('@/views/Artist/List.vue'),
                meta: { title: '热门歌手', public: true }
            },
            {
                path: '/playlist/:id',
                name: 'PlaylistDetail',
                component: () => import('@/views/Playlist/Detail.vue'),
                meta: { title: '歌单详情', public: true }
            },
            {
                path: '/playlists',
                name: 'PlaylistList',
                component: () => import('@/views/Playlist/List.vue'),
                meta: { title: '精选歌单', public: true }
            },
            {
                path: '/mv/:id',
                name: 'MvDetail',
                component: () => import('@/views/MV/Detail.vue'),
                meta: { title: 'MV详情', public: true }
            },
            {
                path: '/mvs',
                name: 'MvList',
                component: () => import('@/views/MV/List.vue'),
                meta: { title: 'MV列表', public: true }
            },
            {
                path: '/search',
                name: 'Search',
                component: () => import('@/views/Search/index.vue'),
                props: { surface: 'public' },
                meta: { title: '搜索', public: true }
            },
            {
                path: '/my',
                redirect: '/my/favorites'
            },
            {
                path: '/my/favorites',
                name: 'MyFavorites',
                component: () => import('@/views/My/Favorites.vue'),
                meta: { title: '我的收藏', requireAuth: true }
            },
            {
                path: '/my/history',
                name: 'ListenHistory',
                component: () => import('@/views/My/History.vue'),
                meta: { title: '收听历史', requireAuth: true }
            },
            {
                path: '/my/playlists',
                name: 'MyPlaylists',
                component: () => import('@/views/My/Playlists.vue'),
                meta: { title: '我的歌单', requireAuth: true }
            },
            {
                path: '/playlist/add-song',
                redirect: to => ({
                    name: 'MyPlaylists',
                    query: { ...to.query, action: 'add-song' }
                })
            },
            {
                path: '/my/checkin',
                name: 'Checkin',
                component: () => import('@/views/My/CheckIn.vue'),
                meta: { title: '每日签到', requireAuth: true }
            },
            {
                path: '/my/search-history',
                name: 'SearchHistory',
                component: () => import('@/views/Search/index.vue'),
                props: { surface: 'personal' },
                meta: { title: '搜索历史', requireAuth: true }
            },
            {
                path: '/my/credit',
                name: 'Credit',
                component: () => import('@/views/My/Credit.vue'),
                meta: { title: '积分中心', requireAuth: true }
            },
            {
                path: '/my/decoration',
                alias: '/my/decoration-warehouse',
                name: 'Decoration',
                component: () => import('@/views/My/DecorationWarehouse.vue'),
                meta: { title: '装饰仓库', requireAuth: true }
            },
            {
                path: '/my/blacklist',
                name: 'MyBlacklist',
                component: () => import('@/views/My/Blacklist.vue'),
                meta: { title: '黑名单', requireAuth: true }
            },
            {
                path: '/my/local-music',
                name: 'MyLocalMusic',
                component: () => import('@/views/My/LocalMusic.vue'),
                meta: { title: '本地音乐', requireAuth: true }
            },
            {
                path: '/local-song/:id',
                name: 'LocalSongDetail',
                component: () => import('@/views/LocalSong/Detail.vue'),
                meta: { title: '本地歌曲详情', requireAuth: true }
            },
            {
                path: '/my/creator',
                alias: '/my/creator-center',
                name: 'MyCreator',
                component: () => import('@/views/My/Creator.vue'),
                meta: { title: '创作者中心', requireAuth: true }
            },
            {
                path: '/my/following',
                name: 'MyFollowing',
                component: () => import('@/views/My/Following.vue'),
                meta: { title: '我的关注', requireAuth: true }
            },
            {
                path: '/my/followers',
                name: 'MyFollowers',
                component: () => import('@/views/My/Followers.vue'),
                meta: { title: '我的粉丝', requireAuth: true }
            },
            {
                path: '/my/friends',
                name: 'MyFriends',
                component: () => import('@/views/My/Friends.vue'),
                meta: { title: '我的好友', requireAuth: true }
            },
            {
                path: '/my/messages',
                name: 'MyMessages',
                component: () => import('@/views/My/Messages.vue'),
                meta: { title: '消息中心', requireAuth: true }
            },
            {
                path: '/my/submit-work',
                name: 'SubmitWork',
                component: () => import('@/views/My/SubmitWork.vue'),
                meta: { title: '投稿作品', requireAuth: true }
            },
            {
                path: '/my/creative-studio',
                alias: '/my/emoji-studio',
                name: 'EmojiStudio',
                component: () => import('@/views/My/CreativeStudio.vue'),
                meta: { title: '创意工坊', requireAuth: true }
            },
            {
                path: '/my/badges',
                name: 'MyBadges',
                component: () => import('@/views/Badge/BadgePage.vue'),
                meta: { title: '装饰仓库', requireAuth: true }
            },
            {
                path: '/conversation',
                name: 'Conversation',
                redirect: '/my/messages',
                meta: { title: '私信', requireAuth: true }
            },
            {
                path: '/conversation/:id',
                name: 'ConversationDetail',
                redirect: to => ({ path: '/my/messages', query: { userId: String(to.params.id) } }),
                meta: { title: '私信详情', requireAuth: true }
            },
            {
                path: '/creator/apply',
                name: 'CreatorApply',
                component: () => import('@/views/My/CreatorApply.vue'),
                meta: { title: '创作者申请', requireAuth: true }
            },
            {
                path: '/creator/center',
                name: 'CreatorCenterPage',
                redirect: { name: 'MyCreator' },
                meta: { title: '创作者中心', requireAuth: true }
            },
            {
                path: '/vip',
                alias: '/my/subscribe',
                name: 'Vip',
                component: () => import('@/views/My/Subscribe.vue'),
                meta: { title: '会员中心', requireAuth: true }
            },
            {
                path: '/square',
                alias: ['/music-square'],
                name: 'MusicSquare',
                component: () => import('@/views/MusicSquare/Index.vue'),
                meta: { title: '音乐广场', public: true }
            },
            {
                path: '/square/marketplace',
                alias: ['/music-square/marketplace'],
                name: 'MusicMarketplace',
                component: () => import('@/views/MusicSquare/Marketplace.vue'),
                meta: { title: '音乐收藏集市', public: true }
            },
            {
                path: '/square/marketplace/:id',
                alias: ['/music-square/marketplace/:id'],
                name: 'MusicMarketplaceDetail',
                component: () => import('@/views/MusicSquare/MarketplaceDetail.vue'),
                meta: { title: '商品帖详情', public: true }
            },
            {
                path: '/square/hot',
                alias: ['/music-square/hot'],
                name: 'MusicSquareHot',
                component: () => import('@/views/MusicSquare/Hot.vue'),
                meta: { title: '音乐广场热门总览', public: true }
            },
            {
                path: '/announcements',
                name: 'OfficialAnnouncements',
                component: () => import('@/views/Announcements/Index.vue'),
                meta: { title: '官方公告', public: true }
            },
            {
                path: '/announcements/:id',
                name: 'OfficialAnnouncementDetail',
                component: () => import('@/views/Announcements/Index.vue'),
                meta: { title: '公告详情', public: true }
            },
            {
                path: '/ranking',
                name: 'Ranking',
                component: () => import('@/views/Ranking/Index.vue'),
                meta: { title: '排行榜', public: true }
            },
            {
                path: '/ranking/:type',
                name: 'RankingType',
                component: () => import('@/views/Ranking/Index.vue'),
                meta: { title: '排行榜', public: true }
            },
            {
                path: '/user/:id',
                name: 'UserProfile',
                component: () => import('@/views/User/Profile.vue'),
                meta: { title: '用户主页', public: true }
            },
            {
                path: '/user/:id/following',
                name: 'UserFollowing',
                component: () => import('@/views/My/Following.vue'),
                meta: { title: 'TA的关注', public: true }
            },
            {
                path: '/user/:id/followers',
                name: 'UserFollowers',
                component: () => import('@/views/My/Followers.vue'),
                meta: { title: 'TA的粉丝', public: true }
            },
            {
                path: '/user/:id/mood-map',
                name: 'UserMoodMap',
                component: () => import('@/views/User/MoodMapProfile.vue'),
                meta: { title: '音乐心情', public: true }
            },
            {
                path: '/payment/order/:id',
                name: 'PaymentOrderDetail',
                component: () => import('@/views/Payment/OrderDetail.vue'),
                meta: { title: '订单详情', requireAuth: true }
            },
            {
                path: '/shop/decoration',
                name: 'DecorationShop',
                component: () => import('@/views/Shop/Decoration.vue'),
                meta: { title: '装饰商城', requireAuth: true }
            },
            {
                path: '/admin/moderation',
                name: 'AdminModeration',
                component: () => import('@/views/Admin/Moderation.vue'),
                meta: { title: '内容审核', requireAuth: true, roles: MODERATION_ROLES }
            },
            {
                path: '/admin/users',
                name: 'AdminUsers',
                component: () => import('@/views/Admin/Users.vue'),
                meta: { title: '用户管理', requireAuth: true, roles: ADMIN_ROLES }
            },
            {
                path: '/admin/payment-review',
                name: 'AdminPaymentReview',
                component: () => import('@/views/Admin/PaymentReview.vue'),
                meta: { title: '支付审核', requireAuth: true, roles: ADMIN_ROLES }
            },
            {
                path: '/admin/store-products',
                name: 'AdminStoreProducts',
                redirect: { path: '/admin/moderation', query: { tab: 'store' } },
                meta: { title: '小镇店务台', requireAuth: true, roles: ADMIN_ROLES }
            },
            {
                path: '/admin/statistics',
                name: 'AdminStatistics',
                component: () => import('@/views/Admin/Statistics.vue'),
                meta: { title: '审核员看板', requireAuth: true, roles: MODERATION_ROLES }
            },
            {
                path: '/admin/platform-statistics',
                name: 'AdminPlatformStatistics',
                component: () => import('@/views/Admin/StatisticsEnhanced.vue'),
                meta: { title: '平台统计', requireAuth: true, roles: ADMIN_ROLES }
            },
            {
                path: '/admin/notification-broadcast',
                name: 'AdminNotificationBroadcast',
                component: () => import('@/views/Admin/NotificationBroadcast.vue'),
                meta: { title: '系统公告', requireAuth: true, roles: ADMIN_ROLES }
            },
            {
                path: "/admin/external",
                name: "AdminExternal",
                component: () => import("@/views/Admin/ExternalContentManage.vue"),
                meta: { title: "外部内容管理", requireAuth: true, roles: ADMIN_ROLES }
            },
            {
                path: '/admin/curated-content',
                name: 'AdminCuratedContent',
                component: () => import('@/views/Admin/CuratedContentManage.vue'),
                meta: { title: '新闻与轮播', requireAuth: true, roles: ADMIN_ROLES }
            },
            {
                path: '/me',
                redirect: '/my/favorites'
            },
            {
                path: '/me/:pathMatch(.*)*',
                redirect: to => `/my/${to.params.pathMatch}`
            },
            {
                path: '/topics',
                redirect: '/404'
            }
        ]
    }
];
const fallbackRoutes: RouteRecordRaw[] = [
    {
        path: '/:pathMatch(.*)*',
        redirect: '/404'
    }
];
const router = createRouter({
    history: createWebHistory(),
    routes: [...standaloneRoutes, ...mainRoutes, ...fallbackRoutes],
    scrollBehavior(to, _from, savedPosition) {
        if (savedPosition)
            return savedPosition;
        if (to.hash)
            return { el: to.hash, top: 16 };
        return { top: 0, left: 0 };
    }
});
router.beforeEach(async (to) => {
    const userStore = useUserStore();
    if (to.meta?.public) {
        return true;
    }
    const requiredRoles = to.meta?.roles ||
        (to.meta?.adminOnly ? ADMIN_ROLES : undefined);
    if (to.meta?.requireAuth && (!userStore.userInfo || requiredRoles?.length)) {
        const info = await userStore.ensureUserInfo();
        if (!info) {
            return { name: 'Login', query: { redirect: to.fullPath } };
        }
    }
    if (requiredRoles?.length) {
        const role = normalizeRole(userStore.userInfo?.role);
        if (!hasAnyRole(role, requiredRoles)) {
            return { name: 'Home' };
        }
    }
    return true;
});
export default router;
