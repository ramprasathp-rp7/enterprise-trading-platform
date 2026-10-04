import { Injectable } from '@angular/core';
import { environment } from './environment';

/**
 * API Origins Configuration Service
 * 
 * Manages the allow-list of origins that are allowed to receive bearer tokens.
 * This prevents accidentally sending authentication tokens to third-party services.
 * 
 * Security principle: Fail closed (deny by default). Only origins explicitly
 * configured in the allow-list receive tokens.
 */
@Injectable({
  providedIn: 'root',
})
export class ApiOriginsConfig {
  /**
   * List of allowed API origins that are permitted to receive bearer tokens.
   * Examples: 'http://localhost:3000', 'http://localhost:4000', 'https://api.example.com'
   */
  private readonly allowedOrigins: string[] = this.buildAllowedOrigins();

  /**
   * Build the list of allowed origins from environment configuration.
   * Normalizes URLs to origins (protocol + hostname + port only).
   */
  private buildAllowedOrigins(): string[] {
    const origins = new Set<string>();

    if (environment.AUTH_SERVICE_URL) {
      origins.add(this.extractOrigin(environment.AUTH_SERVICE_URL));
    }

    if (environment.TRADE_API_URL) {
      origins.add(this.extractOrigin(environment.TRADE_API_URL));
    }

    return Array.from(origins);
  }

  /**
   * Extract and normalize the origin (protocol + host + port) from a URL.
   * 
   * Examples:
   *   'http://localhost:3000/api/v1/login' => 'http://localhost:3000'
   *   'https://api.example.com:443/orders' => 'https://api.example.com'
   *   'http://localhost:3000' => 'http://localhost:3000'
   */
  private extractOrigin(url: string): string {
    try {
      const urlObj = new URL(url);
      return urlObj.origin;
    } catch {
      // If URL parsing fails, return the input as-is
      // This handles edge cases like relative URLs or malformed strings
      return url;
    }
  }

  /**
   * Check if a given URL's origin is in the allowed list.
   * 
   * @param url The URL to check (can be absolute or relative)
   * @returns true if the origin is allowed, false otherwise
   */
  isAllowedOrigin(url: string): boolean {
    // If URL is relative (starts with /), it's same-origin and always allowed
    if (url.startsWith('/')) {
      return true;
    }

    try {
      const requestOrigin = this.extractOrigin(url);
      return this.allowedOrigins.includes(requestOrigin);
    } catch {
      // If we can't parse the URL, deny it (fail closed)
      return false;
    }
  }

  /**
   * Get the list of allowed origins (useful for debugging/testing).
   */
  getAllowedOrigins(): string[] {
    return [...this.allowedOrigins];
  }
}
