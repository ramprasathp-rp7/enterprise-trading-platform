import { NgModule, ModuleWithProviders, SkipSelf, Optional } from '@angular/core';
import { Configuration } from './configuration';
import { HttpClient } from '@angular/common/http';


@NgModule({
  imports:      [],
  declarations: [],
  exports:      [],
  providers: []
})
export class StrategyApiApiModule {
    public static forRoot(configurationFactory: () => Configuration): ModuleWithProviders<StrategyApiApiModule> {
        return {
            ngModule: StrategyApiApiModule,
            providers: [ { provide: Configuration, useFactory: configurationFactory } ]
        };
    }

    constructor( @Optional() @SkipSelf() parentModule: StrategyApiApiModule,
                 @Optional() http: HttpClient) {
        if (parentModule) {
            throw new Error('StrategyApiApiModule is already loaded. Import in your base AppModule only.');
        }
        if (!http) {
            throw new Error('You need to import the HttpClientModule in your AppModule! \n' +
            'See also https://github.com/angular/angular/issues/20575');
        }
    }
}
