import type { NodePgDatabase } from "drizzle-orm/node-postgres";

import * as schema from "../../db/schema/index.js";

const { screenSnapshots } = schema;

type Database = NodePgDatabase<typeof schema>;

export async function saveScreenSnapshot(
  db: Database,
  params: {
    conversationId: string;
    runId: string;
    packageName: string;
    activityName: string | null;
    screenWidth: number;
    screenHeight: number;
    imageWidth: number;
    imageHeight: number;
    nodes: Array<{
      text: string;
      contentDescription: string;
      className: string;
      clickable: boolean;
      editable: boolean;
      enabled: boolean;
      bounds: { left: number; top: number; right: number; bottom: number };
    }>;
    capturedAt: Date;
  },
) {
  await db.insert(screenSnapshots).values({
    conversationId: params.conversationId,
    runId: params.runId,
    packageName: params.packageName,
    activityName: params.activityName,
    screenWidth: params.screenWidth,
    screenHeight: params.screenHeight,
    imageWidth: params.imageWidth,
    imageHeight: params.imageHeight,
    nodesJson: params.nodes as unknown as Record<string, unknown>[],
    nodeCount: params.nodes.length,
    capturedAt: params.capturedAt,
  });
}
