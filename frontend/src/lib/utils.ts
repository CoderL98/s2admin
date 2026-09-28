import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
	return twMerge(clsx(inputs));
}

/** 去掉 children 字段的类型 helper */
export type WithoutChildren<T> = T extends { children?: unknown } ? Omit<T, 'children'> : T;

/** 去掉 child snippet 字段的类型 helper */
export type WithoutChild<T> = T extends { child?: unknown } ? Omit<T, 'child'> : T;

/** 同时去掉 children 与 child */
export type WithoutChildrenOrChild<T> = WithoutChildren<WithoutChild<T>>;

/** 带 ref 的元素属性 helper */
export type WithElementRef<T, U extends HTMLElement = HTMLElement> = T & { ref?: U | null };