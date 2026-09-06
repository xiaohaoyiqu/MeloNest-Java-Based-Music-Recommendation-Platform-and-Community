import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { useUserStore } from '@/stores/user';
import { analyzeWallpaperColors } from '@/utils/wallpaperColor';
export type WallpaperType = 'default' | 'landscape' | 'custom';
export interface WallpaperItem {
    image?: string;
    gradient?: string;
    name: string;
    url?: string;
}
export interface TextColorConfig {
    textPrimary: string;
    textSecondary: string;
    textTertiary: string;
    textShadow: string;
    cardBackground: string;
    cardBorder: string;
    inputBg: string;
    inputBorder: string;
    inputBgHover: string;
    buttonBg: string;
    buttonBorder: string;
    buttonBgHover: string;
    buttonText: string;
    detailStateSurface: string;
    detailStateText: string;
    detailStateTextSecondary: string;
    detailStateIcon: string;
    messageBackground: string;
    messageText: string;
    messageBorder: string;
    messageShadow: string;
    iconPrimary: string;
    iconSecondary: string;
    iconDisabled: string;
    iconHover: string;
    linkColor: string;
    linkHoverColor: string;
    linkActiveColor: string;
    dropdownBackground: string;
    dropdownTextPrimary: string;
    dropdownTextSecondary: string;
    dropdownBorder: string;
    dropdownHover: string;
}
export const DEFAULT_WALLPAPERS: WallpaperItem[] = [
    { name: '纯黑', gradient: '#000000' },
    { name: '纯白', gradient: '#ffffff' },
    { name: '深灰', gradient: '#2d3436' },
    { name: '浅灰', gradient: '#dfe6e9' },
    { name: '彩虹', gradient: 'linear-gradient(90deg, #ff0000 0%, #ff7f00 16%, #ffff00 33%, #00ff00 50%, #0000ff 66%, #4b0082 83%, #9400d3 100%)' },
    { name: '彩虹柔和', gradient: 'linear-gradient(135deg, #ff9a9e 0%, #fecfef 25%, #fecfef 50%, #a1c4fd 75%, #c2e9fb 100%)' },
    { name: '彩虹垂直', gradient: 'linear-gradient(180deg, #ff5f6d 0%, #ffc371 33%, #4facfe 66%, #00f2fe 100%)' },
    { name: '彩虹梦幻', gradient: 'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 50%, #a6c1ee 100%)' },
    { name: '星空', gradient: 'linear-gradient(135deg, #0c0c1e 0%, #1a1a3e 50%, #2d1b4e 100%)' },
    { name: '日落', gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' },
    { name: '日出', gradient: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)' },
    { name: '黄昏', gradient: 'linear-gradient(135deg, #ff7e5f 0%, #feb47b 100%)' },
    { name: '极光', gradient: 'linear-gradient(135deg, #a1c4fd 0%, #c2e9fb 100%)' },
    { name: '夜空', gradient: 'linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%)' },
    { name: '海洋', gradient: 'linear-gradient(135deg, #89f7fe 0%, #66a6ff 100%)' },
    { name: '深海', gradient: 'linear-gradient(135deg, #2193b0 0%, #6dd5ed 100%)' },
    { name: '碧海', gradient: 'linear-gradient(135deg, #00cdac 0%, #8ddad5 100%)' },
    { name: '森林', gradient: 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)' },
    { name: '竹林', gradient: 'linear-gradient(135deg, #134e5e 0%, #71b280 100%)' },
    { name: '草原', gradient: 'linear-gradient(135deg, #56ab2f 0%, #a8e063 100%)' },
    { name: '薄荷', gradient: 'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)' },
    { name: '樱花', gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)' },
    { name: '玫瑰', gradient: 'linear-gradient(135deg, #ee9ca7 0%, #ffdde1 100%)' },
    { name: '薰衣草', gradient: 'linear-gradient(135deg, #c471f5 0%, #fa71cd 100%)' },
    { name: '牡丹', gradient: 'linear-gradient(135deg, #f43b47 0%, #453a94 100%)' },
    { name: '柠檬', gradient: 'linear-gradient(135deg, #f6d365 0%, #fda085 100%)' },
    { name: '火焰', gradient: 'linear-gradient(135deg, #f85032 0%, #e73827 100%)' },
    { name: '秋叶', gradient: 'linear-gradient(135deg, #e96443 0%, #904e95 100%)' },
    { name: '夕阳', gradient: 'linear-gradient(135deg, #ff9966 0%, #ff5e62 100%)' },
    { name: '冰川', gradient: 'linear-gradient(135deg, #e0eafc 0%, #cfdef3 100%)' },
    { name: '冰雪', gradient: 'linear-gradient(135deg, #a1c4fd 0%, #c2e9fb 100%)' },
    { name: '蓝调', gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)' },
    { name: '神秘', gradient: 'linear-gradient(135deg, #30cfd0 0%, #330867 100%)' }
];
export const LANDSCAPE_WALLPAPERS: WallpaperItem[] = [
    { name: '富士山', url: 'https://images.unsplash.com/photo-1490806843957-31f4c9a91c65?w=1920&q=80' },
    { name: '阿尔卑斯山', url: 'https://images.unsplash.com/photo-1531973576160-7125cd663d86?w=1920&q=80' },
    { name: '挪威峡湾', url: 'https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?w=1920&q=80' },
    { name: '稻田梯田', url: 'https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=1920&q=80' },
    { name: '桂林山水', url: 'https://images.unsplash.com/photo-1528164344705-47542687000d?w=1920&q=80' },
    { name: '九寨沟', url: 'https://images.unsplash.com/photo-1508804185872-d7badad00f7b?w=1920&q=80' },
    { name: '大峡谷', url: 'https://images.unsplash.com/photo-1474044159687-1ee9f3a51722?w=1920&q=80' },
    { name: '黄石公园', url: 'https://images.unsplash.com/photo-1534234828563-02511c759c5d?w=1920&q=80' },
    { name: '班夫公园', url: 'https://images.unsplash.com/photo-1503614472-8c93d56e92ce?w=1920&q=80' },
    { name: '新西兰', url: 'https://images.unsplash.com/photo-1507699622108-4be3abd695ad?w=1920&q=80' },
    { name: '马尔代夫', url: 'https://images.unsplash.com/photo-1514282401047-d79a71a590e8?w=1920&q=80' },
    { name: '大堡礁', url: 'https://images.unsplash.com/photo-1546026423-cc4642628d2b?w=1920&q=80' },
    { name: '圣托里尼', url: 'https://images.unsplash.com/photo-1570077188670-e3a8d69ac5ff?w=1920&q=80' },
    { name: '冰岛极光', url: 'https://images.unsplash.com/photo-1483347756197-71ef80e95f73?w=1920&q=80' },
    { name: '北极光', url: 'https://images.unsplash.com/photo-1484589065579-248aad0d8b13?w=1920&q=80' },
    { name: '星空', url: 'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=1920&q=80' },
    { name: '撒哈拉', url: 'https://images.unsplash.com/photo-1509316785289-025f5b846b35?w=1920&q=80' },
    { name: '约旦沙漠', url: 'https://images.unsplash.com/photo-1539650116574-75c0c6d73f6e?w=1920&q=80' },
    { name: '森林', url: 'https://images.unsplash.com/photo-1448375240586-882707db888b?w=1920&q=80' },
    { name: '竹林', url: 'https://images.unsplash.com/photo-1523712999610-f77fbcfc3843?w=1920&q=80' },
    { name: '草原', url: 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?w=1920&q=80' },
    { name: '苏格兰', url: 'https://images.unsplash.com/photo-1506377585622-bedcbb027afc?w=1920&q=80' },
    { name: '山脉', url: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=1920&q=80' },
    { name: '樱花', url: 'https://images.unsplash.com/photo-1522383225653-ed111181a951?w=1920&q=80' },
    { name: '花海', url: 'https://images.unsplash.com/photo-1497250681960-ef046c08a56e?w=1920&q=80' },
    { name: '薰衣草', url: 'https://images.unsplash.com/photo-1499002238440-d264edd596ec?w=1920&q=80' },
    { name: '瀑布', url: 'https://images.unsplash.com/photo-1432405972618-c60b0225b8f9?w=1920&q=80' },
    { name: '雪山', url: 'https://images.unsplash.com/photo-1454496522488-7a8e488e8606?w=1920&q=80' },
    { name: '湖泊', url: 'https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1920&q=80' },
    { name: '日落海滩', url: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1920&q=80' },
    { name: '星空山脉', url: 'https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1920&q=80' },
    { name: '秋日森林', url: 'https://images.unsplash.com/photo-1476820865390-c52aeebb9891?w=1920&q=80' }
];
const LIGHT_CONFIG: TextColorConfig = {
    textPrimary: 'rgba(30, 30, 30, 0.95)', textSecondary: 'rgba(60, 60, 60, 0.75)', textTertiary: 'rgba(100, 100, 100, 0.6)',
    textShadow: '0 1px 2px rgba(255,255,255,0.9), 0 1px 4px rgba(255,255,255,0.7)', cardBackground: 'rgba(255, 255, 255, 0.6)', cardBorder: 'rgba(0, 0, 0, 0.1)',
    inputBg: 'rgba(0, 0, 0, 0.08)', inputBorder: 'rgba(0, 0, 0, 0.2)', inputBgHover: 'rgba(0, 0, 0, 0.15)', buttonBg: 'rgba(0, 0, 0, 0.7)', buttonBorder: 'rgba(0, 0, 0, 1)', buttonBgHover: 'rgba(0, 0, 0, 0.9)', buttonText: 'rgba(255, 255, 255, 0.9)',
    detailStateSurface: 'rgba(24, 24, 27, 0.92)', detailStateText: 'rgba(255, 255, 255, 0.96)', detailStateTextSecondary: 'rgba(255, 255, 255, 0.74)', detailStateIcon: 'rgba(255, 255, 255, 0.96)',
    messageBackground: 'rgba(255, 255, 255, 0.96)', messageText: 'rgba(24, 24, 27, 0.96)', messageBorder: 'rgba(24, 24, 27, 0.16)', messageShadow: '0 8px 28px rgba(15, 23, 42, 0.22)',
    iconPrimary: 'rgba(30, 30, 30, 0.9)', iconSecondary: 'rgba(60, 60, 60, 0.7)', iconDisabled: 'rgba(100, 100, 100, 0.4)', iconHover: 'rgba(30, 30, 30, 1)',
    linkColor: '#409eff', linkHoverColor: '#66b1ff', linkActiveColor: '#3a8ee6', dropdownBackground: 'rgba(255, 255, 255, 0.95)', dropdownTextPrimary: 'rgba(30, 30, 30, 0.9)', dropdownTextSecondary: 'rgba(60, 60, 60, 0.7)', dropdownBorder: 'rgba(0, 0, 0, 0.15)', dropdownHover: 'rgba(0, 0, 0, 0.08)'
};
const DARK_CONFIG: TextColorConfig = {
    textPrimary: 'rgba(255, 255, 255, 0.95)', textSecondary: 'rgba(255, 255, 255, 0.7)', textTertiary: 'rgba(255, 255, 255, 0.5)',
    textShadow: '0 2px 4px rgba(0, 0, 0, 0.8)', cardBackground: 'rgba(255, 255, 255, 0.08)', cardBorder: 'rgba(255, 255, 255, 0.12)',
    inputBg: 'rgba(255, 255, 255, 0.1)', inputBorder: 'rgba(255, 255, 255, 0.2)', inputBgHover: 'rgba(255, 255, 255, 0.15)', buttonBg: 'rgba(255, 255, 255, 0.85)', buttonBorder: 'rgba(255, 255, 255, 1)', buttonBgHover: 'rgba(255, 255, 255, 1)', buttonText: 'rgba(0, 0, 0, 0.85)',
    detailStateSurface: 'rgba(255, 255, 255, 0.94)', detailStateText: 'rgba(24, 24, 27, 0.96)', detailStateTextSecondary: 'rgba(24, 24, 27, 0.68)', detailStateIcon: 'rgba(24, 24, 27, 0.96)',
    messageBackground: 'rgba(24, 24, 27, 0.96)', messageText: 'rgba(255, 255, 255, 0.96)', messageBorder: 'rgba(255, 255, 255, 0.18)', messageShadow: '0 8px 28px rgba(0, 0, 0, 0.42)',
    iconPrimary: 'rgba(255, 255, 255, 0.9)', iconSecondary: 'rgba(255, 255, 255, 0.6)', iconDisabled: 'rgba(255, 255, 255, 0.3)', iconHover: 'rgba(255, 255, 255, 1)',
    linkColor: '#409eff', linkHoverColor: '#66b1ff', linkActiveColor: '#3a8ee6', dropdownBackground: 'rgba(30, 30, 30, 0.95)', dropdownTextPrimary: 'rgba(255, 255, 255, 0.9)', dropdownTextSecondary: 'rgba(255, 255, 255, 0.6)', dropdownBorder: 'rgba(255, 255, 255, 0.15)', dropdownHover: 'rgba(255, 255, 255, 0.1)'
};
function isWallpaperType(value: string | null): value is WallpaperType {
    return value === 'default' || value === 'landscape' || value === 'custom';
}
function parseIndex(value: string | null): number {
    const parsed = Number(value);
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
}
function parseCustomWallpapers(value: string | null): WallpaperItem[] {
    if (!value)
        return [];
    try {
        const parsed = JSON.parse(value);
        if (!Array.isArray(parsed))
            return [];
        return parsed.filter(item => item && typeof item.name === 'string' && (typeof item.image === 'string' || typeof item.gradient === 'string'));
    }
    catch {
        return [];
    }
}
export const useWallpaperStore = defineStore('wallpaper', () => {
    const userStore = useUserStore();
    const userCustomWallpapers = ref<WallpaperItem[]>([]);
    const selectedWallpaperIndex = ref(0);
    const currentWallpaperType = ref<WallpaperType>('default');
    const textColorConfig = ref<TextColorConfig>({ ...DARK_CONFIG });
    const initializedStorageKey = ref('');
    const colorCache = new Map<string, TextColorConfig>();
    let colorRequestId = 0;
    const storageSuffix = computed(() => userStore.isLogin && userStore.userId ? `_${String(userStore.userId)}` : '');
    const backgroundStyle = computed(() => {
        const index = selectedWallpaperIndex.value;
        if (currentWallpaperType.value === 'landscape') {
            const item = LANDSCAPE_WALLPAPERS[index];
            return item?.url ? `url(${item.url}) center/cover no-repeat` : '#000000';
        }
        if (currentWallpaperType.value === 'custom') {
            const item = userCustomWallpapers.value[index];
            return item?.image ? `url(${item.image}) center/cover no-repeat` : item?.gradient || '#000000';
        }
        return DEFAULT_WALLPAPERS[index]?.gradient || '#000000';
    });
    function applyCssVariables() {
        const root = document.documentElement;
        const config = textColorConfig.value;
        const variables: Record<string, string> = {
            '--text-primary': config.textPrimary, '--text-secondary': config.textSecondary, '--text-tertiary': config.textTertiary, '--text-shadow': config.textShadow,
            '--card-background': config.cardBackground, '--card-bg': config.cardBackground, '--card-border': config.cardBorder,
            '--input-bg': config.inputBg, '--input-border': config.inputBorder, '--input-bg-hover': config.inputBgHover,
            '--button-bg': config.buttonBg, '--button-border': config.buttonBorder, '--button-bg-hover': config.buttonBgHover, '--button-text': config.buttonText,
            '--message-background': config.messageBackground, '--message-text': config.messageText, '--message-border': config.messageBorder, '--message-shadow': config.messageShadow,
            '--dropdown-background': config.dropdownBackground, '--dropdown-text-primary': config.dropdownTextPrimary, '--dropdown-text-secondary': config.dropdownTextSecondary, '--dropdown-border': config.dropdownBorder, '--dropdown-hover': config.dropdownHover,
            '--detail-state-surface': config.detailStateSurface, '--detail-state-text': config.detailStateText, '--detail-state-text-secondary': config.detailStateTextSecondary, '--detail-state-icon': config.detailStateIcon,
            '--icon-primary': config.iconPrimary, '--icon-secondary': config.iconSecondary, '--icon-disabled': config.iconDisabled, '--icon-hover': config.iconHover,
            '--link-color': config.linkColor, '--link-hover-color': config.linkHoverColor, '--link-active-color': config.linkActiveColor,
            '--detail-text-primary': config.textPrimary, '--detail-text-secondary': config.textSecondary, '--detail-text-tertiary': config.textTertiary,
            '--mv-text-primary': config.textPrimary, '--mv-text-secondary': config.textSecondary, '--mv-text-tertiary': config.textTertiary, '--mv-border': config.cardBorder, '--mv-bg': config.cardBackground, '--mv-bg-hover': config.inputBgHover
        };
        Object.entries(variables).forEach(([name, value]) => root.style.setProperty(name, value));
    }
    async function updateTextColorConfig() {
        const background = backgroundStyle.value;
        const cached = colorCache.get(background);
        if (cached) {
            textColorConfig.value = { ...cached };
            applyCssVariables();
            return;
        }
        const requestId = ++colorRequestId;
        let detected: Awaited<ReturnType<typeof analyzeWallpaperColors>>;
        try {
            detected = await analyzeWallpaperColors(background);
        }
        catch {
            if (requestId !== colorRequestId || backgroundStyle.value !== background)
                return;
            textColorConfig.value = { ...DARK_CONFIG };
            applyCssVariables();
            return;
        }
        if (requestId !== colorRequestId || backgroundStyle.value !== background)
            return;
        const base = detected.textPrimary.includes('30, 30, 30') ? LIGHT_CONFIG : DARK_CONFIG;
        const next = { ...base, ...detected };
        colorCache.set(background, next);
        textColorConfig.value = next;
        applyCssVariables();
    }
    function persist() {
        const suffix = storageSuffix.value;
        localStorage.setItem(`wallpaperIndex${suffix}`, String(selectedWallpaperIndex.value));
        localStorage.setItem(`wallpaperType${suffix}`, currentWallpaperType.value);
        localStorage.setItem(suffix ? `userCustomWallpapers${suffix}` : 'customWallpapers', JSON.stringify(userCustomWallpapers.value));
    }
    function applyImmediateSolidColorConfig() {
        const match = backgroundStyle.value.match(/^#([0-9a-f]{6})$/i);
        if (!match)
            return;
        const value = Number.parseInt(match[1], 16);
        const red = value >> 16;
        const green = value >> 8 & 0xff;
        const blue = value & 0xff;
        const luminance = red * 0.299 + green * 0.587 + blue * 0.114;
        textColorConfig.value = { ...(luminance >= 160 ? LIGHT_CONFIG : DARK_CONFIG) };
        applyCssVariables();
    }
    function ensureValidSelection() {
        const length = currentWallpaperType.value === 'default' ? DEFAULT_WALLPAPERS.length
            : currentWallpaperType.value === 'landscape' ? LANDSCAPE_WALLPAPERS.length
                : userCustomWallpapers.value.length;
        if (length === 0) {
            currentWallpaperType.value = 'default';
            selectedWallpaperIndex.value = 0;
        }
        else if (selectedWallpaperIndex.value >= length) {
            selectedWallpaperIndex.value = 0;
        }
    }
    async function initialize(force = false) {
        const suffix = storageSuffix.value;
        const key = suffix || 'guest';
        if (!force && initializedStorageKey.value === key)
            return;
        currentWallpaperType.value = isWallpaperType(localStorage.getItem(`wallpaperType${suffix}`))
            ? localStorage.getItem(`wallpaperType${suffix}`) as WallpaperType
            : 'default';
        selectedWallpaperIndex.value = parseIndex(localStorage.getItem(`wallpaperIndex${suffix}`));
        userCustomWallpapers.value = parseCustomWallpapers(localStorage.getItem(suffix ? `userCustomWallpapers${suffix}` : 'customWallpapers'));
        ensureValidSelection();
        initializedStorageKey.value = key;
        await updateTextColorConfig();
    }
    function select(type: WallpaperType, index: number) {
        currentWallpaperType.value = type;
        selectedWallpaperIndex.value = index;
        ensureValidSelection();
        persist();
        applyImmediateSolidColorConfig();
        void updateTextColorConfig();
    }
    function selectDefaultWallpaper(index: number) { select('default', index); }
    function selectLandscapeWallpaper(index: number) { select('landscape', index); }
    function selectCustomWallpaper(index: number) { select('custom', index); }
    function addCustomWallpaper(url: string) {
        if (!url.trim())
            return;
        userCustomWallpapers.value.push({ name: `自定义壁纸${userCustomWallpapers.value.length + 1}`, image: url });
        persist();
    }
    function removeCustomWallpaper(index: number) {
        if (index < 0 || index >= userCustomWallpapers.value.length)
            return;
        userCustomWallpapers.value.splice(index, 1);
        if (currentWallpaperType.value === 'custom') {
            if (selectedWallpaperIndex.value === index)
                selectedWallpaperIndex.value = 0;
            else if (selectedWallpaperIndex.value > index)
                selectedWallpaperIndex.value--;
            ensureValidSelection();
            void updateTextColorConfig();
        }
        persist();
    }
    function refreshWallpapers() {
        return initialize(true);
    }
    return {
        userCustomWallpapers, selectedWallpaperIndex, currentWallpaperType, textColorConfig, initializedStorageKey,
        backgroundStyle, storageSuffix, initialize, selectDefaultWallpaper, selectLandscapeWallpaper,
        selectCustomWallpaper, addCustomWallpaper, removeCustomWallpaper, refreshWallpapers,
        updateTextColorConfig, applyCssVariables, persist
    };
});
