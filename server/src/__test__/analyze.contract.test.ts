import { readFile, stat } from "node:fs/promises";
import path from "node:path";

import { afterEach, describe, expect, it } from "vitest";

import { buildApp } from "../app.js";

let app: ReturnType<typeof buildApp> | undefined;

afterEach(async () => {
  if (app) {
    await app.close();
    app = undefined;
  }
});

describe("analyze route", () => {
  it("accepts multipart metadata and screenshot", async () => {
    app = buildApp();

    const boundary = "----guide-assistant-test-boundary";
    const metadata = JSON.stringify({
      question: "请分析当前页面",
      packageName: "com.example.target",
      activityName: "TargetActivity",
      screenWidth: 1080,
      screenHeight: 2400,
      imageWidth: 1080,
      imageHeight: 2400,
      nodes: [
        {
          text: "发送",
          contentDescription: "",
          className: "android.widget.Button",
          clickable: true,
          editable: false,
          enabled: true,
          bounds: { left: 900, top: 2100, right: 1040, bottom: 2220 },
        },
      ],
    });

    const body =
      `--${boundary}\r\n` +
      `Content-Disposition: form-data; name="metadata"\r\n` +
      `Content-Type: application/json\r\n\r\n` +
      `${metadata}\r\n` +
      `--${boundary}\r\n` +
      `Content-Disposition: form-data; name="screenshot"; filename="screen.jpg"\r\n` +
      `Content-Type: image/jpeg\r\n\r\n` +
      `fake-jpeg-binary\r\n` +
      `--${boundary}--\r\n`;

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: {
        "content-type": `multipart/form-data; boundary=${boundary}`,
      },
      payload: body,
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      answer: "Analyze pipeline placeholder response.",
      target: null,
      action: { type: "none" },
      savedMetadataPath: expect.any(String),
      savedScreenshotPath: expect.any(String),
    });

    const json = response.json();
    expect(path.basename(json.savedMetadataPath)).toBe("metadata.json");
    expect(path.basename(json.savedScreenshotPath)).toBe("screenshot.jpg");

    await expect(stat(json.savedMetadataPath)).resolves.toBeTruthy();
    await expect(stat(json.savedScreenshotPath)).resolves.toBeTruthy();

    const savedMetadata = await readFile(json.savedMetadataPath, "utf8");
    const savedScreenshot = await readFile(json.savedScreenshotPath);
    expect(JSON.parse(savedMetadata)).toEqual(JSON.parse(metadata));
    expect(savedScreenshot.toString("utf8")).toBe("fake-jpeg-binary");
  });

  it("rejects requests missing screenshot", async () => {
    app = buildApp();

    const boundary = "----guide-assistant-test-boundary";
    const metadata = JSON.stringify({
      question: "请分析当前页面",
      packageName: "com.example.target",
      screenWidth: 1080,
      screenHeight: 2400,
      imageWidth: 1080,
      imageHeight: 2400,
      nodes: [],
    });

    const body =
      `--${boundary}\r\n` +
      `Content-Disposition: form-data; name="metadata"\r\n` +
      `Content-Type: application/json\r\n\r\n` +
      `${metadata}\r\n` +
      `--${boundary}--\r\n`;

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
      headers: {
        "content-type": `multipart/form-data; boundary=${boundary}`,
      },
      payload: body,
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      code: "INVALID_REQUEST",
      message: "metadata and screenshot are required.",
    });
  });
});
