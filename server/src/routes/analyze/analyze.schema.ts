import { z } from "zod";

export const boundsSchema = z.object({
  left: z.number().int(),
  top: z.number().int(),
  right: z.number().int(),
  bottom: z.number().int(),
});

export const analyzeErrorSchema = z.object({
  code: z.enum([
    "INVALID_REQUEST",
    "DEVICE_BUSY",
    "INVALID_STATE",
    "ANALYSIS_FAILED",
    "INTERNAL_ERROR",
  ]),
  message: z.string(),
});

export const analyzeMessageTypeSchema = z.enum([
  "speech_text",
  "observed_click",
  "observed_scroll",
]);

export const analyzeMetadataSchema = z.object({
  deviceId: z.string(),
  conversationId: z.uuid().optional(),
  messageType: analyzeMessageTypeSchema,
  messageText: z.string().nullable(),
  packageName: z.string(),
  activityName: z.string().optional(),
  screenWidth: z.number().int().positive(),
  screenHeight: z.number().int().positive(),
  imageWidth: z.number().int().positive(),
  imageHeight: z.number().int().positive(),
  nodes: z.array(
    z.object({
      text: z.string(),
      contentDescription: z.string(),
      className: z.string(),
      clickable: z.boolean(),
      editable: z.boolean(),
      enabled: z.boolean(),
      bounds: boundsSchema,
    }),
  ),
});

export const analyzeResponseSchema = z.object({
  conversationId: z.string(),
  runId: z.string(),
  conversationStatus: z.enum(["waiting_interaction", "completed", "failed"]),
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

export type AnalyzeMetadata = z.infer<typeof analyzeMetadataSchema>;
export type AnalyzeResponse = z.infer<typeof analyzeResponseSchema>;
export type AnalyzeError = z.infer<typeof analyzeErrorSchema>;
