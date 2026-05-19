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

  it("accepts cancel route", async () => {
    app = buildApp();
    const conversationId = "11111111-1111-4111-8111-111111111111";

    const response = await app.inject({
      method: "POST",
      url: `/conversations/${conversationId}/cancel`,
      payload: {
        deviceId: "device-001",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      conversationId,
      status: "cancelled",
      closedReason: "user_exit",
    });
  });
});
