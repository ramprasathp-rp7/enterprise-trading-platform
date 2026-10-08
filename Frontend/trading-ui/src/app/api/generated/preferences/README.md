# @

Binding contract for the customer preferences endpoints of the Trade REST API (Sprint 10, module `com.tradingplatform.preferences`). The Angular UI generates its typed client from this file, so a field renamed here is a compile error there.  ## Identifiers  `accountId` means the numeric surrogate key `ACCOUNTS.id` everywhere in this contract: in the path parameter and in the JWT `accountId` claim. One user holds exactly one account, so the preference record is keyed on that account.  ## What a preference holds  - `alertChannels`: every channel the platform reaches the customer on about their trades and   alerts. One to three entries, each channel at most once (`PREF-422` otherwise). A   notification is delivered on each chosen channel. - `channel`: one of `EMAIL`, `SMS` or `IN_APP`. The set is closed: there is no webhook or URL   channel, so no customer can make the platform post to a destination of their choosing. - `contactDetail`: the address for that channel, one per channel. Several addresses for one   channel are out of scope. Required for `EMAIL` (an email address, at most 254 characters)   and `SMS` (an E.164 number such as `+447700900123`). Must be absent or null for `IN_APP`,   which delivers to the platform\'s own notification inbox.  Entries are returned in the order `EMAIL`, `SMS`, `IN_APP`, whatever order they were sent in.  Contact details are personal data. They are encrypted at rest, returned only to the owning account, and never written to logs or Kafka payloads.  ## When nothing has been stored  `GET` answers `200` with the platform default, a single `IN_APP` entry with no contact detail, and `configured: false`. It never answers an empty body or `404` for an account that has simply not chosen yet. `DELETE` returns an account to that state.  ## Internal resolution  The notifications module resolves a customer\'s channel through the in-process Java interface `com.tradingplatform.preferences.PreferenceResolver`. That path is deliberately **not** part of this contract and has no HTTP route: a resolution route reachable with a customer token would let any customer read another customer\'s contact details.  ## Admins  Admins have no trading account of their own, so their preferences are keyed by the admin\'s user id (the JWT `sub` claim) and served under `/api/v1/admin/preferences`, with no id in the path: an admin only ever reads and changes their own. The channel rules, validation and encryption are the same as for customers. Admins are notified on their chosen channels through `POST /api/v1/admin/notifications/customer-registered`, which the admin UI calls after a customer has been registered; `EMAIL` and `SMS` go through the same sender stubs as customer notifications, and the answer says whether `IN_APP` (the UI bell) was chosen.  ## Authentication  Every `/api/v1/_**` route requires a valid signed JWT in the `Authorization` header. A missing, malformed, expired or wrongly signed token is `AUTH-401`, 401. A valid token whose `accountId` claim does not match the account being addressed is `ACC-403`, 403. Another account\'s preferences are refused, never returned empty. `/api/v1/admin/_**` routes also need the `ADMIN` role; without it they answer `AUTH-403`, 403. 

The version of the OpenAPI document: 1.0.0

## Building

To install the required dependencies and to build the typescript sources run:

```console
npm install
npm run build
```

## Publishing

First build the package then run `npm publish dist` (don't forget to specify the `dist` folder!)

## Consuming

Navigate to the folder of your consuming project and run one of next commands.

_published:_

```console
npm install @ --save
```

_without publishing (not recommended):_

```console
npm install PATH_TO_GENERATED_PACKAGE/dist.tgz --save
```

_It's important to take the tgz file, otherwise you'll get trouble with links on windows_

_using `npm link`:_

In PATH_TO_GENERATED_PACKAGE/dist:

```console
npm link
```

In your project:

```console
npm link 
```

__Note for Windows users:__ The Angular CLI has troubles to use linked npm packages.
Please refer to this issue <https://github.com/angular/angular-cli/issues/8284> for a solution / workaround.
Published packages are not effected by this issue.

### General usage

In your Angular project:

```typescript

import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideApi } from '';

export const appConfig: ApplicationConfig = {
    providers: [
        // ...
        provideHttpClient(),
        provideApi()
    ],
};
```

**NOTE**
If you're still using `AppModule` and haven't [migrated](https://angular.dev/reference/migrations/standalone) yet, you can still import an Angular module:
```typescript
import { PreferencesApiApiModule } from '';
```

If different from the generated base path, during app bootstrap, you can provide the base path to your service.

```typescript
import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideApi } from '';

export const appConfig: ApplicationConfig = {
    providers: [
        // ...
        provideHttpClient(),
        provideApi('http://localhost:9999')
    ],
};
```

```typescript
// with a custom configuration
import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideApi } from '';

export const appConfig: ApplicationConfig = {
    providers: [
        // ...
        provideHttpClient(),
        provideApi({
            withCredentials: true,
            username: 'user',
            password: 'password'
        })
    ],
};
```

```typescript
// with factory building a custom configuration
import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideApi, Configuration } from '';

export const appConfig: ApplicationConfig = {
    providers: [
        // ...
        provideHttpClient(),
        {
            provide: Configuration,
            useFactory: (authService: AuthService) => new Configuration({
                    basePath: 'http://localhost:9999',
                    withCredentials: true,
                    username: authService.getUsername(),
                    password: authService.getPassword(),
            }),
            deps: [AuthService],
            multi: false
        }
    ],
};
```

### Using multiple OpenAPI files / APIs

In order to use multiple APIs generated from different OpenAPI files,
you can create an alias name when importing the modules
in order to avoid naming conflicts:

```typescript
import { provideApi as provideUserApi } from 'my-user-api-path';
import { provideApi as provideAdminApi } from 'my-admin-api-path';
import { HttpClientModule } from '@angular/common/http';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
    providers: [
        // ...
        provideHttpClient(),
        provideUserApi(environment.basePath),
        provideAdminApi(environment.basePath),
    ],
};
```

### Customizing path parameter encoding

Without further customization, only [path-parameters][parameter-locations-url] of [style][style-values-url] 'simple'
and Dates for format 'date-time' are encoded correctly.

Other styles (e.g. "matrix") are not that easy to encode
and thus are best delegated to other libraries (e.g.: [@honoluluhenk/http-param-expander]).

To implement your own parameter encoding (or call another library),
pass an arrow-function or method-reference to the `encodeParam` property of the Configuration-object
(see [General Usage](#general-usage) above).

Example value for use in your Configuration-Provider:

```typescript
new Configuration({
    encodeParam: (param: Param) => myFancyParamEncoder(param),
})
```

[parameter-locations-url]: https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.1.0.md#parameter-locations
[style-values-url]: https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.1.0.md#style-values
[@honoluluhenk/http-param-expander]: https://www.npmjs.com/package/@honoluluhenk/http-param-expander
