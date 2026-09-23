import { IsString, IsInt, IsArray, IsUUID, IsDate } from 'class-validator';

export class UserResponseDto {
  @IsUUID()
  id: string;

  @IsString()
  username: string;

  @IsInt()
  accountId: number;

  @IsArray()
  roles: string[];

  @IsDate()
  createdOn: Date;
}
