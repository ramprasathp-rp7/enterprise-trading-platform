import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import { DecodedToken } from '../../tokens/token-claims';

export const CurrentUser = createParamDecorator((data: unknown, ctx: ExecutionContext) => {
  const request = ctx.switchToHttp().getRequest();
  return request.user as DecodedToken;
});
