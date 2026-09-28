const KEY = 's2admin.tenant';

export function getTenantScope(): string | null {
	if (typeof localStorage === 'undefined') return null;
	const value = localStorage.getItem(KEY);
	return value && /^\d+$/.test(value) ? value : null;
}

export function setTenantScope(id: string | null) {
	if (typeof localStorage === 'undefined') return;
	if (id && /^\d+$/.test(id)) localStorage.setItem(KEY, id);
	else localStorage.removeItem(KEY);
}
