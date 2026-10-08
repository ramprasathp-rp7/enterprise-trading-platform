import { IsNull } from 'typeorm';
import { UsersRepository } from './users.repository';
import { RefreshTokenRepository } from './refresh-token.repository';
import { KnownAccountIdsRepository } from './known-account-ids.repository';

// Repositories extend TypeORM's Repository; the base methods are stubbed so no database is needed.
const dataSource: any = { createEntityManager: () => ({}) };

describe('UsersRepository', () => {
  let repo: UsersRepository;

  beforeEach(() => {
    repo = new UsersRepository(dataSource);
  });

  test('findByUsername queries by username', async () => {
    const findOne = jest.spyOn(repo, 'findOne').mockResolvedValueOnce({ id: 'u1' } as any);
    await expect(repo.findByUsername('alice')).resolves.toEqual({ id: 'u1' });
    expect(findOne).toHaveBeenCalledWith({ where: { username: 'alice' } });
  });

  test('findById queries by id', async () => {
    const findOne = jest.spyOn(repo, 'findOne').mockResolvedValueOnce(null);
    await expect(repo.findById('u1')).resolves.toBeNull();
    expect(findOne).toHaveBeenCalledWith({ where: { id: 'u1' } });
  });

  test('saveUser creates then saves the entity', async () => {
    const entity = { username: 'alice' } as any;
    jest.spyOn(repo, 'create').mockReturnValueOnce(entity);
    const save = jest.spyOn(repo, 'save').mockResolvedValueOnce({ ...entity, id: 'u1' });
    await expect(repo.saveUser({ username: 'alice' })).resolves.toEqual({ username: 'alice', id: 'u1' });
    expect(save).toHaveBeenCalledWith(entity);
  });
});

describe('KnownAccountIdsRepository', () => {
  let repo: KnownAccountIdsRepository;

  beforeEach(() => {
    repo = new KnownAccountIdsRepository(dataSource);
  });

  test.each([
    [1, true],
    [0, false],
  ])('accountIdExists is %s when count is %i', async (count, expected) => {
    const countSpy = jest.spyOn(repo, 'count').mockResolvedValueOnce(count);
    await expect(repo.accountIdExists(5)).resolves.toBe(expected);
    expect(countSpy).toHaveBeenCalledWith({ where: { account_id: 5 } });
  });

  test('addAccountId creates then saves the record', async () => {
    const record = { account_id: 5 } as any;
    const create = jest.spyOn(repo, 'create').mockReturnValueOnce(record);
    jest.spyOn(repo, 'save').mockResolvedValueOnce(record);
    await expect(repo.addAccountId(5)).resolves.toBe(record);
    expect(create).toHaveBeenCalledWith({ account_id: 5 });
  });
});

describe('RefreshTokenRepository', () => {
  let repo: RefreshTokenRepository;

  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(new Date('2026-01-01T00:00:00Z'));
    repo = new RefreshTokenRepository(dataSource);
  });

  afterEach(() => jest.useRealTimers());

  test('saveToken creates then saves the token', async () => {
    const token = { token_hash: 'h' } as any;
    jest.spyOn(repo, 'create').mockReturnValueOnce(token);
    const save = jest.spyOn(repo, 'save').mockResolvedValueOnce(token);
    await expect(repo.saveToken(token)).resolves.toBe(token);
    expect(save).toHaveBeenCalledWith(token);
  });

  test('findByHash queries by token hash', async () => {
    const findOne = jest.spyOn(repo, 'findOne').mockResolvedValueOnce(null);
    await repo.findByHash('h');
    expect(findOne).toHaveBeenCalledWith({ where: { token_hash: 'h' } });
  });

  test('findById queries by id', async () => {
    const findOne = jest.spyOn(repo, 'findOne').mockResolvedValueOnce(null);
    await repo.findById('t1');
    expect(findOne).toHaveBeenCalledWith({ where: { id: 't1' } });
  });

  test('findActiveByUserId only returns unrevoked tokens', async () => {
    const find = jest.spyOn(repo, 'find').mockResolvedValueOnce([]);
    await repo.findActiveByUserId('u1');
    expect(find).toHaveBeenCalledWith({ where: { user_id: 'u1', revoked_on: IsNull() } });
  });

  test('findByFamilyId queries by family', async () => {
    const find = jest.spyOn(repo, 'find').mockResolvedValueOnce([]);
    await repo.findByFamilyId('f1');
    expect(find).toHaveBeenCalledWith({ where: { family_id: 'f1' } });
  });

  test('markRevoked sets revoked_on to now', async () => {
    const update = jest.spyOn(repo, 'update').mockResolvedValueOnce({} as any);
    await repo.markRevoked('t1');
    expect(update).toHaveBeenCalledWith('t1', { revoked_on: new Date('2026-01-01T00:00:00Z') });
  });

  test('revokeFamily revokes every token in the family', async () => {
    const update = jest.spyOn(repo, 'update').mockResolvedValueOnce({} as any);
    await repo.revokeFamily('f1');
    expect(update).toHaveBeenCalledWith({ family_id: 'f1' }, { revoked_on: new Date('2026-01-01T00:00:00Z') });
  });

  test('markReplacedBy links the old token to its replacement', async () => {
    const update = jest.spyOn(repo, 'update').mockResolvedValueOnce({} as any);
    await repo.markReplacedBy('t1', 't2');
    expect(update).toHaveBeenCalledWith('t1', { replaced_by: 't2' });
  });
});
