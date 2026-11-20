package com.cocos.challenge.application;

import com.cocos.challenge.domain.exceptions.InconsistentDataException;
import com.cocos.challenge.domain.exceptions.MarketDataUnavailableException;
import com.cocos.challenge.domain.exceptions.UserNotFoundException;
import com.cocos.challenge.domain.models.HoldingModel;
import com.cocos.challenge.domain.models.InstrumentModel;
import com.cocos.challenge.domain.models.MarketDataModel;
import com.cocos.challenge.domain.models.PortfolioModel;
import com.cocos.challenge.domain.models.PortfolioPositionModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.UserModel;
import com.cocos.challenge.domain.ports.out.InstrumentPersistencePort;
import com.cocos.challenge.domain.ports.out.MarketDataPersistencePort;
import com.cocos.challenge.domain.ports.out.OrderPersistencePort;
import com.cocos.challenge.domain.ports.out.UserPersistencePort;
import com.cocos.challenge.domain.strategies.CalculatePositionsContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Get portfolio use case")
class GetPortfolioUseCaseTest {
    private static final int USER_ID = 1;
    private static final int ASSET = 2;

    private UserPersistencePort users;
    private InstrumentPersistencePort instruments;
    private MarketDataPersistencePort marketData;
    private CalculatePositionsContext calculatePositionsContext;
    private GetPortfolioUseCase useCase;

    private static HoldingModel holding(String quantity, String cost) {
        return HoldingModel.builder().instrumentId(ASSET).quantity(new BigDecimal(quantity))
                .remainingCost(new BigDecimal(cost)).reservedShares(BigDecimal.ZERO).build();
    }

    private static PositionsModel positions(String settled, String reserved, HoldingModel... holdings) {
        Map<Integer, HoldingModel> byInstrument = new HashMap<>();
        for (HoldingModel holding : holdings) {
            byInstrument.put(holding.instrumentId(), holding);
        }
        return PositionsModel.builder().settledCash(new BigDecimal(settled)).reservedCash(new BigDecimal(reserved))
                .holdings(byInstrument).build();
    }

    private static MarketDataModel quote(String close, String previousClose) {
        return MarketDataModel.builder().instrumentId(ASSET).close(close == null ? null : new BigDecimal(close))
                .previousClose(new BigDecimal(previousClose)).build();
    }

    @BeforeEach
    void setUp() {
        users = mock(UserPersistencePort.class);
        instruments = mock(InstrumentPersistencePort.class);
        marketData = mock(MarketDataPersistencePort.class);
        calculatePositionsContext = mock(CalculatePositionsContext.class);
        useCase = new GetPortfolioUseCase(users, instruments, mock(OrderPersistencePort.class), marketData,
                calculatePositionsContext);

        when(users.findById(USER_ID)).thenReturn(Optional.of(UserModel.builder().id(USER_ID).build()));
        when(instruments.findByIds(any())).thenReturn(
                Map.of(ASSET, new InstrumentModel(ASSET, "AAA", "Activo A", "ACCIONES")));
    }

    @Test
    @DisplayName("Fails when the user does not exist")
    void givenUnknownUser_whenExecuting_thenUserIsNotFound() {
        // Given
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID));

        // Then
        assertThat(failure).as("The user must exist").isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Reports only cash when nothing is held")
    void givenOnlyCash_whenExecuting_thenPortfolioHasNoPositions() {
        // Given
        when(calculatePositionsContext.apply(any())).thenReturn(positions("1000", "200"));
        when(marketData.findLatest(any(Collection.class))).thenReturn(Map.of());

        // When
        PortfolioModel portfolio = useCase.execute(USER_ID);

        // Then
        assertThat(portfolio.currency()).as("The currency is ARS").isEqualTo("ARS");
        assertThat(portfolio.totalValue()).as("The total is the settled cash").isEqualByComparingTo("1000");
        assertThat(portfolio.availableCash()).as("The reservation is discounted").isEqualByComparingTo("800");
        assertThat(portfolio.positions()).as("There are no positions").isEmpty();
    }

    @Test
    @DisplayName("Values the holdings at the latest close and computes both returns")
    void givenHolding_whenExecuting_thenValuesAndReturnsAreComputed() {
        // Given
        when(calculatePositionsContext.apply(any())).thenReturn(positions("200", "0", holding("10", "800")));
        when(marketData.findLatest(any(Collection.class))).thenReturn(Map.of(ASSET, quote("88", "80")));

        // When
        PortfolioModel portfolio = useCase.execute(USER_ID);

        // Then
        PortfolioPositionModel position = portfolio.positions().get(0);
        assertThat(position.ticker()).as("The instrument ticker is reported").isEqualTo("AAA");
        assertThat(position.quantity()).as("The quantity is whole").isEqualByComparingTo("10");
        assertThat(position.marketValue()).as("10 shares at 88").isEqualByComparingTo("880");
        assertThat(position.totalReturnPercent()).as("880 over a cost of 800").isEqualByComparingTo("10");
        assertThat(position.dailyReturnPercent()).as("88 over 80").isEqualByComparingTo("10");
        assertThat(portfolio.totalValue()).as("Cash plus market value").isEqualByComparingTo("1080");
    }

    @Test
    @DisplayName("Ignores holdings that were fully sold")
    void givenEmptyHolding_whenExecuting_thenItIsNotReported() {
        // Given
        when(calculatePositionsContext.apply(any())).thenReturn(positions("500", "0", holding("0", "0")));
        when(marketData.findLatest(any(Collection.class))).thenReturn(Map.of());

        // When
        PortfolioModel portfolio = useCase.execute(USER_ID);

        // Then
        assertThat(portfolio.positions()).as("A sold holding is not a position").isEmpty();
    }

    @Test
    @DisplayName("Fails when a held instrument is unknown")
    void givenUnknownInstrument_whenExecuting_thenDataIsInconsistent() {
        // Given
        when(calculatePositionsContext.apply(any())).thenReturn(positions("0", "0", holding("1", "10")));
        when(instruments.findByIds(any())).thenReturn(Map.of());
        when(marketData.findLatest(any(Collection.class))).thenReturn(Map.of(ASSET, quote("10", "10")));

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID));

        // Then
        assertThat(failure).as("The instrument must exist").isInstanceOf(InconsistentDataException.class);
    }

    @Test
    @DisplayName("Fails when the quote is missing")
    void givenMissingQuote_whenExecuting_thenMarketDataIsUnavailable() {
        // Given
        when(calculatePositionsContext.apply(any())).thenReturn(positions("0", "0", holding("1", "10")));
        when(marketData.findLatest(any(Collection.class))).thenReturn(Map.of());

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID));

        // Then
        assertThat(failure).as("A quote is required").isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    @DisplayName("Fails when the close or the previous close is not positive")
    void givenNonPositiveQuote_whenExecuting_thenMarketDataIsUnavailable() {
        // Given
        when(calculatePositionsContext.apply(any())).thenReturn(positions("0", "0", holding("1", "10")));
        when(marketData.findLatest(any(Collection.class))).thenReturn(Map.of(ASSET, quote("0", "10")));
        Throwable zeroClose = catchThrowable(() -> useCase.execute(USER_ID));
        when(marketData.findLatest(any(Collection.class))).thenReturn(Map.of(ASSET, quote(null, "10")));
        Throwable nullClose = catchThrowable(() -> useCase.execute(USER_ID));
        when(marketData.findLatest(any(Collection.class))).thenReturn(Map.of(ASSET, quote("10", "0")));

        // When
        Throwable zeroPrevious = catchThrowable(() -> useCase.execute(USER_ID));

        // Then
        assertThat(List.of(zeroClose, nullClose, zeroPrevious)).as("Invalid quotes are unavailable data")
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(MarketDataUnavailableException.class));
    }

    @Test
    @DisplayName("Reports a negative position and discounts it from the total")
    void givenNegativeHolding_whenExecuting_thenItIsReportedAndDiscounted() {
        // Given
        when(calculatePositionsContext.apply(any())).thenReturn(positions("1000", "0", holding("-10", "-800")));
        when(marketData.findLatest(any(Collection.class))).thenReturn(Map.of(ASSET, quote("88", "80")));

        // When
        PortfolioModel portfolio = useCase.execute(USER_ID);

        // Then
        PortfolioPositionModel position = portfolio.positions().get(0);
        assertThat(position.quantity()).as("The negative quantity is shown").isEqualByComparingTo("-10");
        assertThat(position.marketValue()).as("Negative market value").isEqualByComparingTo("-880");
        assertThat(position.totalReturnPercent()).as("-880 against a cost of -800").isEqualByComparingTo("-10");
        assertThat(portfolio.totalValue()).as("The position is discounted").isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("Reports a zero return when the remaining cost is zero")
    void givenZeroCost_whenExecuting_thenReturnIsZero() {
        // Given
        when(calculatePositionsContext.apply(any())).thenReturn(positions("0", "0", holding("1", "0")));
        when(marketData.findLatest(any(Collection.class))).thenReturn(Map.of(ASSET, quote("10", "10")));

        // When
        PortfolioModel portfolio = useCase.execute(USER_ID);

        // Then
        assertThat(portfolio.positions().get(0).totalReturnPercent()).as("No division by zero").isZero();
    }
}
