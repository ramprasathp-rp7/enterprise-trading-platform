import { IsString, IsInt, MinLength, MaxLength, Matches, IsArray, IsOptional, ArrayMinSize, IsEnum } from 'class-validator';
import { Transform } from 'class-transformer';
import { Role } from '../../common/roles.enum';

export class RegisterDto {
  @IsString()
  @MinLength(3)
  @MaxLength(64)
  @Matches(/^[a-zA-Z0-9._-]+$/, {
    message: 'username must contain only alphanumeric characters, dots, underscores, and hyphens',
  })
  username: string;

  @IsString()
  @MinLength(12, {
    message: 'password must be at least 12 characters long',
  })
  @MaxLength(128)
  password: string;

  @IsInt()
  @Transform(({ value }) => {
    const num = parseInt(value, 10);
    return isNaN(num) ? value : num;
  })
  accountId: number;

  @IsOptional()
  @IsArray()
  @ArrayMinSize(1)
  @IsEnum(Role, { each: true })
  roles?: Role[];
}
