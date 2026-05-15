import { z } from "zod";

export const analyzeResponseSchema = z.object({
  status: z.literal("not_implemented"),
  message: z.string(),
});

export type AnalyzeResponse = z.infer<typeof analyzeResponseSchema>;
