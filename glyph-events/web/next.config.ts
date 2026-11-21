import type { NextConfig } from "next";

const config: NextConfig = {
  // Los sonidos se suben con una server action: hasta 3,5 MB (Vercel corta en 4,5 MB).
  experimental: { serverActions: { bodySizeLimit: "4mb" } },
};

export default config;
