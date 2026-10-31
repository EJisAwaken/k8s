import type { NextConfig } from "next";

/** Adresse du backend vue par le serveur Next (jamais par le navigateur). */
const BACKEND_INTERNAL_URL = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8095";

const nextConfig: NextConfig = {

  output: "standalone",

  async rewrites() {
    return [{ source: "/api/v1/:path*", destination: `${BACKEND_INTERNAL_URL}/api/v1/:path*` }];
  },

  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Frame-Options", value: "DENY" },
        ],
      },
    ];
  },

  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
