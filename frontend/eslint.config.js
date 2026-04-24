import js from "@eslint/js";
import tseslint from "typescript-eslint";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import security from "eslint-plugin-security";
import globals from "globals";

export default tseslint.config(
  {
    ignores: ["dist/**", "node_modules/**", "*.config.js", "*.config.ts"],
  },
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  ...tseslint.configs.stylisticTypeChecked,
  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: {
      parserOptions: {
        project: ["./tsconfig.json"],
        tsconfigRootDir: import.meta.dirname,
      },
      globals: {
        ...globals.browser,
      },
    },
    plugins: {
      react,
      "react-hooks": reactHooks,
      security,
    },
    settings: {
      react: { version: "detect" },
    },
    rules: {
      ...react.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      ...security.configs.recommended.rules,
      "react/react-in-jsx-scope": "off",
      "react/prop-types": "off",
      "@typescript-eslint/no-misused-promises": [
        "error",
        { checksVoidReturn: { attributes: false } },
      ],
    },
  },
);
