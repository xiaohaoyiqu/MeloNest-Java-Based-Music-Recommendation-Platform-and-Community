import { Window } from 'happy-dom';
import { afterEach, vi } from 'vitest';
if (typeof globalThis.window === 'undefined') {
    const happyWindow = new Window();
    Object.defineProperty(globalThis, 'window', {
        value: happyWindow,
        configurable: true
    });
    Object.defineProperty(globalThis, 'document', {
        value: happyWindow.document,
        configurable: true
    });
    Object.defineProperty(globalThis, 'navigator', {
        value: happyWindow.navigator,
        configurable: true
    });
    Object.defineProperty(globalThis, 'history', {
        value: happyWindow.history,
        configurable: true
    });
    Object.defineProperty(globalThis, 'location', {
        value: happyWindow.location,
        configurable: true
    });
    Object.defineProperty(globalThis, 'HTMLElement', {
        value: happyWindow.HTMLElement,
        configurable: true
    });
    Object.defineProperty(globalThis, 'Element', {
        value: happyWindow.Element,
        configurable: true
    });
    Object.defineProperty(globalThis, 'Node', {
        value: happyWindow.Node,
        configurable: true
    });
    Object.defineProperty(globalThis, 'CustomEvent', {
        value: happyWindow.CustomEvent,
        configurable: true
    });
    Object.defineProperty(globalThis, 'Event', {
        value: happyWindow.Event,
        configurable: true
    });
    Object.defineProperty(globalThis, 'getComputedStyle', {
        value: happyWindow.getComputedStyle.bind(happyWindow),
        configurable: true
    });
    Object.defineProperty(globalThis, 'requestAnimationFrame', {
        value: happyWindow.requestAnimationFrame.bind(happyWindow),
        configurable: true
    });
    Object.defineProperty(globalThis, 'cancelAnimationFrame', {
        value: happyWindow.cancelAnimationFrame.bind(happyWindow),
        configurable: true
    });
    Object.defineProperty(globalThis, 'MutationObserver', {
        value: happyWindow.MutationObserver,
        configurable: true
    });
    Object.defineProperty(globalThis, 'ResizeObserver', {
        value: happyWindow.ResizeObserver,
        configurable: true
    });
}
function createMemoryStorage(): Storage {
    const store = new Map<string, string>();
    return {
        get length() {
            return store.size;
        },
        clear() {
            store.clear();
        },
        getItem(key: string) {
            return store.has(key) ? store.get(key)! : null;
        },
        key(index: number) {
            return Array.from(store.keys())[index] ?? null;
        },
        removeItem(key: string) {
            store.delete(key);
        },
        setItem(key: string, value: string) {
            store.set(key, String(value));
        }
    };
}
Object.defineProperty(globalThis, 'localStorage', {
    value: createMemoryStorage(),
    configurable: true
});
afterEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
});
