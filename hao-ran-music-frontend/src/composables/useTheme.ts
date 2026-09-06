import { ref, computed } from 'vue';
const THEME_KEY = 'haoranmusic-theme';
export type ThemeMode = 'light' | 'dark' | 'auto';
const getSystemTheme = (): ThemeMode => {
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
        return 'dark';
    }
    return 'light';
};
const theme = ref<ThemeMode>((localStorage.getItem(THEME_KEY) as ThemeMode) || getSystemTheme());
export function useTheme() {
    const isDark = computed(() => theme.value === 'dark');
    const setTheme = (newTheme: ThemeMode) => {
        theme.value = newTheme;
        localStorage.setItem(THEME_KEY, newTheme);
        if (newTheme === 'dark') {
            document.documentElement.classList.add('dark');
        }
        else {
            document.documentElement.classList.remove('dark');
        }
    };
    const toggleTheme = () => {
        setTheme(isDark.value ? 'light' : 'dark');
    };
    const initTheme = () => {
        if (theme.value === 'dark') {
            document.documentElement.classList.add('dark');
        }
    };
    return {
        theme,
        isDark,
        setTheme,
        toggleTheme,
        initTheme
    };
}
