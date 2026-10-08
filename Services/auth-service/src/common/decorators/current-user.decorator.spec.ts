import { ROUTE_ARGS_METADATA } from '@nestjs/common/constants';
import { CurrentUser } from './current-user.decorator';

function getFactory(decorator: () => ParameterDecorator) {
  class TestController {
    handler(@decorator() _user: unknown) {}
  }
  const args = Reflect.getMetadata(ROUTE_ARGS_METADATA, TestController, 'handler');
  return args[Object.keys(args)[0]].factory;
}

describe('CurrentUser decorator', () => {
  test('returns the user attached to the request by JwtAuthGuard', () => {
    const user = { sub: 'u1', accountId: 1, roles: ['CUSTOMER'] };
    const ctx: any = { switchToHttp: () => ({ getRequest: () => ({ user }) }) };
    expect(getFactory(CurrentUser)(undefined, ctx)).toBe(user);
  });

  test('returns undefined when no user is attached', () => {
    const ctx: any = { switchToHttp: () => ({ getRequest: () => ({}) }) };
    expect(getFactory(CurrentUser)(undefined, ctx)).toBeUndefined();
  });
});
