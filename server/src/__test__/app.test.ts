import { afterEach, describe, expect, it } from "vitest";

import { buildApp } from "../app.js";

let app: ReturnType<typeof buildApp> | undefined;

afterEach(async () => {
  if (app) {
    await app.close();
    app = undefined;
  }
});

describe("buildApp", () => {
  it("returns health status", async () => {
    app = buildApp();

    const response = await app.inject({
      method: "GET",
      url: "/health",
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      status: "ok",
      service: "guide-assistant-server",
    });
  });

  it("rejects incomplete analyze requests", async () => {
    app = buildApp();

    const response = await app.inject({
      method: "POST",
      url: "/analyze",
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      code: "INVALID_REQUEST",
      message: "metadata and screenshot are required.",
    });
  });
});
