package barley.wire.wirebarley.infrastructure.service;

import barley.wire.wirebarley.domain.account.Account;
import barley.wire.wirebarley.common.exception.AccountNotFoundException;
import barley.wire.wirebarley.infrastructure.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public Account save(Account account) {
        return accountRepository.save(account);
    }

    @Transactional
    public void delete(Account account) {
        account.delete();
    }

    public Account getAccount(Long accountId) {
        return accountRepository.findByIdNotDeleted(accountId)
            .orElseThrow(() -> new AccountNotFoundException("계좌번호를 찾을 수 없습니다: " + accountId));
    }

    public Account getAccountWithLock(Long accountId) {
        return accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> new AccountNotFoundException("계좌번호(LOCK)를 찾을 수 없습니다: " + accountId));
    }

    public boolean existsByAccountNumber(String accountNumber) {
        return accountRepository.existsByAccountNumber(accountNumber);
    }

    /**
     * 2개 계좌를 조회할 때 데드락 방지를 위해 항상 낮은 계좌 ID를 먼저 조회하는 메서드
     */
    public Pair<Account, Account> getAccountsWithLockOrdered(Long accountId1, Long accountId2) {
        List<Long> ids = Arrays.asList(accountId1, accountId2);
        List<Account> accounts = accountRepository.findByIdsWithLockOrdered(ids);

        if (accounts.size() != 2) {
            throw new AccountNotFoundException("출금/송금 계좌 번호 확인이 필요합니다.");
        }

        Account account1 = accounts.stream().filter(a -> a.getId().equals(accountId1)).findFirst().orElseThrow();

        Account account2 = accounts.stream().filter(a -> a.getId().equals(accountId2)).findFirst().orElseThrow();

        return Pair.of(account1, account2);
    }
}
