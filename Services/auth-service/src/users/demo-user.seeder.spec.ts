import { DemoUserSeeder } from './demo-user.seeder';

describe('DemoUserSeeder', () => {
  const repo: any = { accountIdExists: jest.fn(), addAccountId: jest.fn() };

  beforeEach(() => {
    jest.resetAllMocks();
    jest.spyOn(console, 'log').mockImplementation(() => undefined);
  });

  afterEach(() => jest.restoreAllMocks());

  test('adds only the demo account ids that are missing', async () => {
    repo.accountIdExists.mockImplementation(async (id: number) => id === 2);
    await new DemoUserSeeder(repo).seedKnownAccountIds();

    expect(repo.accountIdExists).toHaveBeenCalledTimes(3);
    expect(repo.addAccountId.mock.calls).toEqual([[1], [3]]);
  });

  test('adds nothing when every demo account id exists', async () => {
    repo.accountIdExists.mockResolvedValue(true);
    await new DemoUserSeeder(repo).seedKnownAccountIds();
    expect(repo.addAccountId).not.toHaveBeenCalled();
  });
});
