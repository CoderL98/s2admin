export type BadgeTone = 'default' | 'success' | 'destructive' | 'warning' | 'info' | 'outline';

export type CellView =
	| { kind: 'text'; text: string }
	| { kind: 'badge'; text: string; tone?: BadgeTone }
	| { kind: 'code'; text: string };

export function badge(value: string | number, tone: BadgeTone = 'default'): CellView {
	return { kind: 'badge', text: String(value), tone };
}

export function codeCell(value: string): CellView {
	return { kind: 'code', text: value || '-' };
}

export function userStatusBadge(status: 0 | 1 | 2 | 3): CellView {
	switch (status) {
		case 0:
			return badge('正常', 'success');
		case 1:
			return badge('禁用', 'destructive');
		case 2:
			return badge('锁定', 'warning');
		case 3:
			return badge('过期', 'info');
		default:
			return badge('未知', 'outline');
	}
}

export function commonStatusBadge(status: 0 | 1): CellView {
	return status === 0 ? badge('正常', 'success') : badge('停用', 'destructive');
}

export function loginStatusBadge(status: 0 | 1): CellView {
	return status === 0 ? badge('成功', 'success') : badge('失败', 'destructive');
}

export function opStatusBadge(status: 0 | 1): CellView {
	return status === 0 ? badge('成功', 'success') : badge('失败', 'destructive');
}

export function menuTypeBadge(type: 1 | 2 | 3): CellView {
	if (type === 1) return badge('目录', 'info');
	if (type === 2) return badge('菜单', 'success');
	return badge('按钮', 'default');
}

export function permissionTypeBadge(type: 1 | 2 | 3): CellView {
	if (type === 1) return badge('菜单', 'info');
	if (type === 2) return badge('按钮', 'success');
	return badge('API', 'warning');
}

export function dataScopeLabel(scope: 1 | 2 | 3 | 4): string {
	return ({ 1: '全部数据', 2: '本部门及以下', 3: '本部门', 4: '本人' }[scope] ?? '未知');
}

export function dataScopeBadge(scope: 1 | 2 | 3 | 4): CellView {
	const map: Record<number, BadgeTone> = {
		1: 'destructive',
		2: 'warning',
		3: 'info',
		4: 'default'
	};
	return badge(dataScopeLabel(scope), map[scope] ?? 'default');
}
