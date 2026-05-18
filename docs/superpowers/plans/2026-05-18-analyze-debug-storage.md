# Analyze Debug Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Accept `POST /analyze` multipart requests, store `metadata.json` and `screenshot.jpg` into a per-request temp directory, and return the saved paths in the response.

**Architecture:** Add a Fastify multipart content-type parser that preserves the raw request body as a buffer, keep the route handler thin, parse and persist payloads inside a dedicated server service, and lock the behavior with route-level tests.

**Tech Stack:** Fastify, TypeScript, Node.js fs/path/os/crypto, Vitest

---

### Task 1: Add failing tests for debug storage response and filesystem output

**Files:**
- Modify: `server/src/__test__/analyze.contract.test.ts`

- [ ] **Step 1: Extend the success-path assertions to expect debug file paths**
- [ ] **Step 2: Verify that both saved paths exist on disk and match expected filenames**
- [ ] **Step 3: Keep the invalid multipart test asserting `400 INVALID_REQUEST`**

### Task 2: Add raw multipart parsing support at the HTTP boundary

**Files:**
- Modify: `server/src/app.ts`
- Modify: `server/src/routes/analyze/analyze.handler.ts`

- [ ] **Step 1: Register a Fastify content-type parser for `multipart/form-data` that preserves the raw body as `Buffer`**
- [ ] **Step 2: Update the analyze handler to require multipart content type and a raw buffer body**
- [ ] **Step 3: Delegate multipart parsing and persistence to a dedicated service**

### Task 3: Implement analyze debug storage service

**Files:**
- Create: `server/src/services/analyze-debug-storage.ts`
- Modify: `server/src/routes/analyze/analyze.schema.ts`
- Modify: `contracts/openapi/analyze.yaml`
- Modify: `docs/20250515-analyze接口第一版合同RFC.md`

- [ ] **Step 1: Parse multipart boundary, extract `metadata` and `screenshot` parts, and validate both are present**
- [ ] **Step 2: Create `<tmp>/guide-assistant-analyze/<timestamp-random>/` and write `metadata.json` and `screenshot.jpg`**
- [ ] **Step 3: Return the saved paths so the handler can include them in the response**
- [ ] **Step 4: Extend the route response schema and external contract docs to include the debug file paths**

### Task 4: Run server verification

**Files:**
- Modify: none

- [ ] **Step 1: Run `cd server && pnpm run test -- analyze.contract.test.ts` if dependencies are available**
- [ ] **Step 2: Run `cd server && pnpm run check` if the environment allows it**
- [ ] **Step 3: If verification is blocked, report the exact environment blocker**
