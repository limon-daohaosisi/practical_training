import { z } from "zod";

export const cancelParamsSchema = z.object({
  conversationId: z.uuid(),
});

export const cancelBodySchema = z.object({
  deviceId: z.string(),
});

export const cancelResponseSchema = z.object({
  conversationId: z.string(),
  status: z.literal("cancelled"),
  closedReason: z.literal("user_exit"),
});

export type CancelParams = z.infer<typeof cancelParamsSchema>;
export type CancelBody = z.infer<typeof cancelBodySchema>;
export type CancelResponse = z.infer<typeof cancelResponseSchema>;
