import { NgModule, ModuleWithProviders, SkipSelf, Optional } from '@angular/core';
import { Configuration } from './configuration';
import { HttpClient } from '@angular/common/http';


@NgModule({
  imports:      [],
  declarations: [],
  exports:      [],
  providers: []
})
export class MarketDataApiApiModule {
    public static forRoot(configurationFactory: () => Configuration): ModuleWithProviders<MarketDataApiApiModule> {
        return {
            ngModule: MarketDataApiApiModule,
            providers: [ { provide: Configuration, useFactory: configurationFactory } ]
        };
    }

    constructor( @Optional() @SkipSelf() parentModule: MarketDataApiApiModule,
                 @Optional() http: HttpClient) {
        if (parentModule) {
            throw new Error('MarketDataApiApiModule is already loaded. Import in your base AppModule only.');
        }
        if (!http) {
            throw new Error('You need to import the HttpClientModule in your AppModule! \n' +
            'See also https://github.com/angular/angular/issues/20575');
        }
    }
}
