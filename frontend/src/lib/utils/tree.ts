export function buildTree<T extends { id: number; parentId: number; children?: T[] }>(flat: T[]): T[] {
	const nodes = flat.map((n) => ({ ...n, children: [] as T[] }));
	const map = new Map<number, T>();
	nodes.forEach((n) => map.set(n.id, n));
	const roots: T[] = [];
	for (const n of nodes) {
		if (!n.parentId) {
			roots.push(n);
			continue;
		}
		const parent = map.get(n.parentId);
		if (parent) {
			parent.children = parent.children ?? [];
			parent.children.push(n);
		} else {
			roots.push(n);
		}
	}
	return roots;
}

export function flattenTree<T extends { children?: T[] }>(nodes: T[], depth = 0): (T & { depth: number })[] {
	const out: (T & { depth: number })[] = [];
	for (const node of nodes) {
		out.push({ ...node, depth });
		if (node.children?.length) {
			out.push(...flattenTree(node.children, depth + 1));
		}
	}
	return out;
}
