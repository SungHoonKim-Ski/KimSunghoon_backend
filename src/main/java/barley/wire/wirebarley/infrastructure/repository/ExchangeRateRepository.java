package barley.wire.wirebarley.infrastructure.repository;

import barley.wire.wirebarley.domain.account.Currency;
import barley.wire.wirebarley.domain.exchange.ExchangeRate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Query("SELECT e FROM ExchangeRate e " + "WHERE e.fromCurrency = :fromCurrency " + "AND e.toCurrency = :toCurrency "
        + "ORDER BY e.updatedAt DESC " + "LIMIT 1")
    Optional<ExchangeRate> findLatestRate(@Param("fromCurrency") Currency fromCurrency,
        @Param("toCurrency") Currency toCurrency);
}
