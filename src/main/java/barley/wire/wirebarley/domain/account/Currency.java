package barley.wire.wirebarley.domain.account;

public enum Currency {
    KRW("원", "₩"),
    USD("달러", "$"),
    JPY("엔", "¥"),
    EUR("유로", "€");

    private final String koreanName;
    private final String symbol;

    Currency(String koreanName, String symbol) {
        this.koreanName = koreanName;
        this.symbol = symbol;
    }

    public String getKoreanName() {
    return koreanName;
    }

    public String getSymbol() {
    return symbol;
    }
}
