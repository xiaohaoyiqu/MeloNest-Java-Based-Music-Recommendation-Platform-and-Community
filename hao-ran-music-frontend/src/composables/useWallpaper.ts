import { storeToRefs } from 'pinia';
import { DEFAULT_WALLPAPERS, LANDSCAPE_WALLPAPERS, useWallpaperStore } from '@/stores/wallpaper';
export type { TextColorConfig, WallpaperItem, WallpaperType } from '@/stores/wallpaper';
export { DEFAULT_WALLPAPERS, LANDSCAPE_WALLPAPERS };
export function useWallpaper() {
    const store = useWallpaperStore();
    const { userCustomWallpapers, selectedWallpaperIndex, currentWallpaperType, textColorConfig, backgroundStyle } = storeToRefs(store);
    return {
        userCustomWallpapers,
        selectedWallpaperIndex,
        currentWallpaperType,
        textColorConfig,
        backgroundStyle,
        DEFAULT_WALLPAPERS,
        LANDSCAPE_WALLPAPERS,
        selectDefaultWallpaper: store.selectDefaultWallpaper,
        selectLandscapeWallpaper: store.selectLandscapeWallpaper,
        selectCustomWallpaper: store.selectCustomWallpaper,
        removeCustomWallpaper: store.removeCustomWallpaper,
        refreshWallpapers: store.refreshWallpapers,
        addCustomWallpaper: store.addCustomWallpaper,
        updateTextColorConfig: store.updateTextColorConfig,
        applyCssVariables: store.applyCssVariables,
        loadFromStorage: store.initialize
    };
}
