# Sprint 09: Enterprise Trading Platform - Angular UI

## Project Overview

Angular 21 standalone components frontend for the Enterprise Trading Platform. Built with signals for state management, generated API clients from OpenAPI contracts, and comprehensive test coverage.

**Status**: Production Ready | **Node**: 20.19+ | **Angular**: 21

---

## Engineering Contract

This project adheres to 10 fixed requirements:

1.   **Angular 21 Workspace** - `npm ci`, `npm run build`, `npm test` all succeed
2.   **Standalone Components & Signals** - No NgModules, observables stop at HttpClient
3.   **Sources in `src/`** - Specs as `*.spec.ts` beside implementations
4.   **Generated API Clients** - Typed clients from contracts, never hand-written
5.   **Committed Generated Output** - Regenerated on contract change, never edited manually
6.   **Functional Interceptor** - Registered once, specs for attach and do-not-attach cases
7.   **Route Guards** - All routes except sign-in protected, redirect with return address
8.   **Order Ticket Validation** - Validates before submission, renders all error codes
9.   **Blotter Badges** - Per-status badge distinguishing working from finished orders
10.  **Playwright E2E Tests** - Environment-driven, two journey scenarios

---

## Quick Start

### Prerequisites

- **Node.js** 20.19+, 22.12+, or 24+
- **npm** 10+
- Backend services running:
  - Auth Service: `http://localhost:3000`
  - Trade API: `http://localhost:8080`

### Installation & Build

```bash
# Install dependencies
npm ci

# Build application
npm run build

# Run tests
npm test

# Generate API clients from contracts
npm run generate:auth
npm run generate:trade
```

### Development Server

```bash
# Start dev server (default: http://localhost:4200)
npm start
```

---

## Project Structure

```
src/app/
+-- core/                      # Infrastructure 
|   +-- auth/                  # JWT management, guards, interceptors
|   +-- services/              # HTTP, logging, error handling
|   +-- config/                # Environment, API endpoints, constants
|   +-- interceptors/          # HTTP error handling
|
+-- features/                  # Business logic by domain
|   +-- sign-in/                  # User login & registration
|   |   +-- pages/login/
|   |   +-- pages/register/
|   |   +-- components/credential-form/
|   |   +-- sign-in.service.ts
|   |   +-- sign-in.store.ts
|   |   +-- sign-in.models.ts
|   |
|   +-- order-ticket/          # Order ticket creation
|   |   +-- pages/order-ticket/
|   |   +-- components/order-form/
|   |   +-- components/symbol-lookup/
|   |   +-- components/side-selector/
|   |   +-- components/order-summary/
|   |   +-- services/order-ticket.service.ts
|   |   +-- services/order-ticket-validator.ts
|   |   +-- order-ticket.store.ts
|   |   +-- order-ticket.models.ts
|   |
|   +-- blotter/               # Order history & tracking
|   |   +-- pages/blotter/
|   |   +-- components/order-list/
|   |   +-- components/order-status-badge/
|   |   +-- components/order-filters/
|   |   +-- services/order-history.service.ts
|   |   +-- services/order-cancel.service.ts
|   |   +-- blotter.store.ts
|   |   +-- blotter.models.ts
|   |
|   +-- dashboard/             # Portfolio & analytics dashboard
|       +-- pages/dashboard/
|       +-- components/portfolio-header/
|       +-- components/balance-summary/
|       +-- components/positions-overview/
|       +-- components/performance-chart/
|       +-- services/dashboard.service.ts
|       +-- dashboard.store.ts
|       +-- dashboard.models.ts
|
+-- shared/                    # Reusable components, services, models & utilities
|   +-- components/
|   |   +-- common/            # Base UI components
|   |   |
|   |   +-- trading/           # Trading-domain components
|   |
|   +-- pipes/                 # Data formatting
|   |
|   +-- directives/            # Behavioral directives
|   |
|   +-- services/              # Business logic services
|   +-- models/                # Shared type definitions
|   |
|   +-- utils/                 # Utility functions & constants
|   |
|   +-- shared.module.ts       # NgModule with all exports
|   +-- index.ts               # Barrel export for easy imports
|   
|
+-- layout/                    # Application shell 
|   +-- shell/                 # Root layout container
|   +-- navbar/                # Top navigation bar
|   +-- sidebar/               # Left feature menu
|   +-- footer/                # Bottom footer
|
+-- api/generated/             # Generated API clients 
    +-- auth/                  # Auth service client (from auth-api.yaml)
    +-- trade/                 # Trade API client (from trade-api.yaml)
```
