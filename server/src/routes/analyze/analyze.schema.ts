import { z } from "zod";

export const boundsSchema = z.object({
  left: z.number().int(),
  top: z.number().int(),
  right: z.number().int(),
  bottom: z.number().int(),
});

export const analyzeErrorSchema = z.object({
  code: z.enum(["INVALID_REQUEST", "ANALYSIS_FAILED", "INTERNAL_ERROR"]),
  message: z.string(),
});

export const analyzeResponseSchema = z.object({
  answer: z.string(),
  target: z
    .object({
      label: z.string(),
      bounds: boundsSchema,
    })
    .nullable(),
  action: z.object({
    type: z.enum(["tap", "scroll", "none"]),
  }),
  savedMetadataPath: z.string(),
  savedScreenshotPath: z.string(),
});

export type AnalyzeResponse = z.infer<typeof analyzeResponseSchema>;
export type AnalyzeError = z.infer<typeof analyzeErrorSchema>;
