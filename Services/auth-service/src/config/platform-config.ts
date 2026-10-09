import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';
import { parse } from 'yaml';

/**
 * Shape of Config/application.yaml, the shared port and database settings for every service.
 * Every field is optional so a partial or missing file falls back to defaults.
 */
export interface PlatformConfig {
  frontend?: { url?: string; port?: number };
  services?: Record<string, { url?: string; port?: number } | undefined>;
  database?: { host?: string; port?: number; name?: string; user?: string };
}

/**
 * Reads Config/application.yaml. The path defaults to two levels above the working directory
 * (the repo root when run from Services/auth-service) and can be set with PLATFORM_CONFIG_PATH.
 * Returns an empty config when the file is absent, e.g. inside a container.
 */
export function loadPlatformConfig(
  path = process.env.PLATFORM_CONFIG_PATH || resolve(process.cwd(), '..', '..', 'Config', 'application.yaml'),
): PlatformConfig {
  if (!existsSync(path)) {
    return {};
  }
  return (parse(readFileSync(path, 'utf8')) as PlatformConfig | null) ?? {};
}

/** Builds an origin such as http://localhost:4200 from a url/port entry. */
export function toOrigin(entry: { url?: string; port?: number } | undefined): string | undefined {
  if (!entry?.url) {
    return undefined;
  }
  return entry.port ? `${entry.url}:${entry.port}` : entry.url;
}
