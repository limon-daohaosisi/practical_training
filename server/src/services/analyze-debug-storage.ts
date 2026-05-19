import { randomBytes } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

import { normalizeCapturedNodes } from "./normalized-capture.js";

type PersistAnalyzeDebugPayloadInput = {
  contentType: string;
  body: Buffer;
};

export type PersistAnalyzeDebugPayloadResult = {
  metadataJson: string;
  screenshotBytes: Buffer;
  savedMetadataPath: string;
  savedScreenshotPath: string;
};

export async function persistAnalyzeDebugPayload(
  input: PersistAnalyzeDebugPayloadInput,
): Promise<PersistAnalyzeDebugPayloadResult> {
  const boundary = extractBoundary(input.contentType);
  const { metadataJson, screenshotBytes } = parseMultipartPayload(
    input.body,
    boundary,
  );
  const parsedMetadata = JSON.parse(metadataJson) as {
    screenWidth: number;
    screenHeight: number;
    nodes: Array<{
      text: string;
      contentDescription: string;
      className: string;
      clickable: boolean;
      editable: boolean;
      enabled: boolean;
      bounds: { left: number; top: number; right: number; bottom: number };
    }>;
  };
  parsedMetadata.nodes = normalizeCapturedNodes(
    {
      screenWidth: parsedMetadata.screenWidth,
      screenHeight: parsedMetadata.screenHeight,
    },
    parsedMetadata.nodes,
  );
  const formattedMetadata = JSON.stringify(parsedMetadata, null, 2);

  const directory = await createDebugDirectory();
  const savedMetadataPath = path.join(directory, "metadata.json");
  const savedScreenshotPath = path.join(directory, "screenshot.jpg");

  await writeFile(savedMetadataPath, formattedMetadata, "utf8");
  await writeFile(savedScreenshotPath, screenshotBytes);

  return {
    metadataJson,
    screenshotBytes,
    savedMetadataPath,
    savedScreenshotPath,
  };
}

function extractBoundary(contentType: string): string {
  const match = contentType.match(/boundary=([^;]+)/i);
  if (!match?.[1]) {
    throw new Error("Missing multipart boundary.");
  }
  return match[1];
}

function parseMultipartPayload(
  body: Buffer,
  boundary: string,
): {
  metadataJson: string;
  screenshotBytes: Buffer;
} {
  const delimiter = Buffer.from(`--${boundary}`);
  const parts = splitBuffer(body, delimiter)
    .map((part) => trimMultipartPart(part))
    .filter((part) => part.length > 0 && part.toString("ascii") !== "--");

  let metadataJson: string | null = null;
  let screenshotBytes: Buffer | null = null;

  for (const part of parts) {
    const separator = Buffer.from("\r\n\r\n");
    const separatorIndex = part.indexOf(separator);
    if (separatorIndex < 0) continue;

    const rawHeaders = part.subarray(0, separatorIndex).toString("utf8");
    const rawContent = trimTrailingCrlf(
      part.subarray(separatorIndex + separator.length),
    );

    if (rawHeaders.includes('name="metadata"')) {
      metadataJson = rawContent.toString("utf8");
      continue;
    }

    if (rawHeaders.includes('name="screenshot"')) {
      screenshotBytes = rawContent;
    }
  }

  if (!metadataJson || !screenshotBytes) {
    throw new Error("metadata and screenshot are required.");
  }

  return {
    metadataJson,
    screenshotBytes,
  };
}

function splitBuffer(body: Buffer, delimiter: Buffer): Buffer[] {
  const parts: Buffer[] = [];
  let offset = 0;

  while (offset < body.length) {
    const nextIndex = body.indexOf(delimiter, offset);
    if (nextIndex < 0) {
      parts.push(body.subarray(offset));
      break;
    }
    parts.push(body.subarray(offset, nextIndex));
    offset = nextIndex + delimiter.length;
  }

  return parts;
}

function trimMultipartPart(part: Buffer): Buffer {
  let start = 0;
  let end = part.length;

  while (start < end && (part[start] === 0x0d || part[start] === 0x0a)) {
    start++;
  }
  while (end > start && (part[end - 1] === 0x0d || part[end - 1] === 0x0a)) {
    end--;
  }

  return part.subarray(start, end);
}

function trimTrailingCrlf(part: Buffer): Buffer {
  let end = part.length;
  while (end > 0 && (part[end - 1] === 0x0d || part[end - 1] === 0x0a)) {
    end--;
  }
  return part.subarray(0, end);
}

async function createDebugDirectory(): Promise<string> {
  const rootDir = path.join(os.tmpdir(), "guide-assistant-analyze");
  await mkdir(rootDir, { recursive: true });

  const timestamp = new Date()
    .toISOString()
    .replace(/[-:]/g, "")
    .replace(/\.\d{3}Z$/, "Z");
  const randomSuffix = randomBytes(3).toString("hex");
  const directory = path.join(rootDir, `${timestamp}-${randomSuffix}`);

  await mkdir(directory, { recursive: true });
  return directory;
}
