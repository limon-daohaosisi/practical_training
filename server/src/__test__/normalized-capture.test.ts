import { describe, expect, it } from "vitest";

import { normalizeCapturedNodes } from "../services/normalized-capture.js";

describe("normalizeCapturedNodes", () => {
  it("drops invalid nodes and deduplicates equivalent entries", () => {
    const nodes = normalizeCapturedNodes(
      {
        screenWidth: 1080,
        screenHeight: 2400,
      },
      [
        {
          text: "发送",
          contentDescription: "",
          className: "android.widget.Button",
          clickable: true,
          editable: false,
          enabled: true,
          bounds: { left: 900, top: 2100, right: 1040, bottom: 2220 },
        },
        {
          text: "发送",
          contentDescription: "",
          className: "android.widget.Button",
          clickable: true,
          editable: false,
          enabled: true,
          bounds: { left: 900, top: 2100, right: 1040, bottom: 2220 },
        },
        {
          text: "坏节点",
          contentDescription: "",
          className: "android.widget.Button",
          clickable: true,
          editable: false,
          enabled: true,
          bounds: { left: 500, top: 700, right: 400, bottom: 800 },
        },
        {
          text: "屏外节点",
          contentDescription: "",
          className: "android.widget.Button",
          clickable: true,
          editable: false,
          enabled: true,
          bounds: { left: 1300, top: 700, right: 1500, bottom: 800 },
        },
      ],
    );

    expect(nodes).toEqual([
      {
        text: "发送",
        contentDescription: "",
        className: "android.widget.Button",
        clickable: true,
        editable: false,
        enabled: true,
        bounds: { left: 900, top: 2100, right: 1040, bottom: 2220 },
      },
    ]);
  });
});
