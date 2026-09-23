import { Injectable } from '@nestjs/common';
import { DataSource, Repository } from 'typeorm';
import { User } from '../entities/user.entity';

@Injectable()
export class UsersRepository extends Repository<User> {
  constructor(private dataSource: DataSource) {
    super(User, dataSource.createEntityManager());
  }

  async findByUsername(username: string): Promise<User | null> {
    return this.findOne({
      where: { username },
    });
  }

  async findById(id: string): Promise<User | null> {
    return this.findOne({
      where: { id },
    });
  }

  async saveUser(user: Partial<User>): Promise<User> {
    const newUser = this.create(user);
    return this.save(newUser);
  }
}
