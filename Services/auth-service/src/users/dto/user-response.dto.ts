import { IsString, IsInt, IsArray, IsUUID, IsDate } from 'class-validator';
import { Role } from '../../common/roles.enum';

export class UserResponseDto {
  @IsUUID()
  id: string;

  @IsString()
  username: string;

  @IsInt()
  accountId: number;

  @IsArray()
  roles: Role[];

  @IsDate()
  createdOn: Date;
}
