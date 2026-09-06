import { Calendar, ChatDotRound, ChatLineSquare, CircleCheck, CircleClose, Document, DocumentDelete, Download, Flag, Headset, Hide, Medal, MoreFilled, Service, Share, Star, Timer, User, VideoCamera, Warning, WarningFilled } from '@element-plus/icons-vue';
import type { Component } from 'vue';
const icons: Record<string, Component> = {
    Calendar, ChatDotRound, ChatLineSquare, CircleCheck, CircleClose, Document,
    DocumentDelete, Download, Flag, Headset, Hide, Medal, MoreFilled, Service,
    Share, Star, Timer, User, VideoCamera, Warning, WarningFilled
};
export function getElementPlusIcon(name: string, fallback: Component): Component {
    return icons[name] || fallback;
}
