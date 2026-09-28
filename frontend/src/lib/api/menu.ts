/**
 * 菜单管理 API —— 根据 PUBLIC_USE_MOCK 自动切换 mock / 真实后端
 */
import { USE_MOCK } from '$lib/config';
import * as mock from './mock/menu';
import * as real from './real/menu';

export const getMenuList = USE_MOCK ? mock.getMenuList : real.getMenuList;
export const getMenuTree = USE_MOCK ? mock.getMenuTree : real.getMenuTree;
export const createMenu = USE_MOCK ? mock.createMenu : real.createMenu;
export const updateMenu = USE_MOCK ? mock.updateMenu : real.updateMenu;
export const removeMenu = USE_MOCK ? mock.removeMenu : real.removeMenu;
export const moveMenu = USE_MOCK ? mock.moveMenu : real.moveMenu;
