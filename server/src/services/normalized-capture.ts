type Bounds = {
  left: number;
  top: number;
  right: number;
  bottom: number;
};

type NodeLike = {
  text: string;
  contentDescription: string;
  className: string;
  clickable: boolean;
  editable: boolean;
  enabled: boolean;
  bounds: Bounds;
};

type Viewport = {
  screenWidth: number;
  screenHeight: number;
};

export function normalizeCapturedNodes(
  viewport: Viewport,
  nodes: NodeLike[],
): NodeLike[] {
  const seen = new Set<string>();
  const normalized: NodeLike[] = [];

  for (const node of nodes) {
    const clipped = clipBounds(node.bounds, viewport);
    if (!clipped) continue;

    const candidate = {
      ...node,
      bounds: clipped,
    };
    const key = JSON.stringify(candidate);
    if (seen.has(key)) continue;

    seen.add(key);
    normalized.push(candidate);
  }

  return normalized;
}

function clipBounds(bounds: Bounds, viewport: Viewport): Bounds | null {
  if (bounds.left >= bounds.right || bounds.top >= bounds.bottom) {
    return null;
  }

  const left = Math.max(0, bounds.left);
  const top = Math.max(0, bounds.top);
  const right = Math.min(viewport.screenWidth, bounds.right);
  const bottom = Math.min(viewport.screenHeight, bounds.bottom);

  if (left >= right || top >= bottom) {
    return null;
  }

  return {
    left,
    top,
    right,
    bottom,
  };
}
