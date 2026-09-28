// 兼容 shadcn-svelte 模板中从 "$lib/utils.js" 导入的写法
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs) {
	return twMerge(clsx(inputs));
}