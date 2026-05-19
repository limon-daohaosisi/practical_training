import { afterEach, describe, expect, it, vi } from "vitest";

const ORIGINAL_DATABASE_URL = process.env.DATABASE_URL;

afterEach(() => {
  if (ORIGINAL_DATABASE_URL === undefined) {
    delete process.env.DATABASE_URL;
  } else {
    process.env.DATABASE_URL = ORIGINAL_DATABASE_URL;
  }

  vi.resetModules();
});

describe("db schema exports", () => {
  it("exports all MVP tables", async () => {
    const schema = await import("../db/schema/index.js");

    expect(schema).toMatchObject({
      conversations: expect.any(Object),
      runs: expect.any(Object),
      messages: expect.any(Object),
      screenSnapshots: expect.any(Object),
    });
  });
});

describe("createDbClient", () => {
  it("throws when DATABASE_URL is missing", async () => {
    delete process.env.DATABASE_URL;

    const { createDbClient } = await import("../db/client.js");

    expect(() => createDbClient()).toThrowError(
      "DATABASE_URL is required to initialize the database client.",
    );
  });
});
