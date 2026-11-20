package com.cocos.challenge.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.cocos.challenge.infrastructure.rest.ResponseAssertions.described;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Order and portfolio API with PostgreSQL")
class OrderApiIT extends AbstractPersistenceIT {
    private static final int ASSET = 2;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    private static String marketBody(String side, String field, String value) {
        return "{\"instrumentId\":" + ASSET + ",\"side\":\"" + side + "\",\"type\":\"MARKET\",\"" + field + "\":"
                + value + "}";
    }

    private static String limitBody(String side, String quantity, String price) {
        return "{\"instrumentId\":" + ASSET + ",\"side\":\"" + side + "\",\"type\":\"LIMIT\",\"quantity\":"
                + quantity + ",\"limitPrice\":" + price + "}";
    }

    private static <T> List<T> runConcurrently(int count, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch ready = new CountDownLatch(count);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO users (email, accountNumber) VALUES ('a@example.test', 'A-1'), ('b@example.test', 'B-1')");
        jdbc.update("INSERT INTO instruments (ticker, name, type) VALUES ('ARS', 'Peso', 'MONEDA'), "
                + "('AAA', 'Activo A', 'ACCIONES'), ('A%B', 'Percent Co', 'ACCIONES')");
        jdbc.update("INSERT INTO marketdata (instrumentId, close, previousClose, date) "
                + "VALUES (2, 80, 75, '2026-01-01')");
        cashIn(1, "1000");
    }

    @Test
    @DisplayName("Fills a market buy and updates the portfolio")
    void givenCashAndQuote_whenBuyingAtMarket_thenPortfolioReflectsTrade() throws Exception {
        // Given
        String request = marketBody("BUY", "quantity", "10");

        // When
        var response = placeOrder(1, request);

        // Then
        response
                .andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.status value \"FILLED\"", jsonPath("$.status").value("FILLED")))
                .andExpect(described("JSON $.quantity value 10", jsonPath("$.quantity").value(10)))
                .andExpect(described("JSON $.price value 80.00000000", jsonPath("$.price").value(80.00000000)))
                .andExpect(described("JSON $.rejectionReason value org.hamcrest.Matchers.nullValue()", jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.nullValue())));

        portfolio(1)
                .andExpect(described("JSON $.availableCash value 200.00000000", jsonPath("$.availableCash").value(200.00000000)))
                .andExpect(described("JSON $.totalValue value 1000.00000000", jsonPath("$.totalValue").value(1000.00000000)))
                .andExpect(described("JSON $.positions[0].ticker value \"AAA\"", jsonPath("$.positions[0].ticker").value("AAA")))
                .andExpect(described("JSON $.positions[0].quantity value 10", jsonPath("$.positions[0].quantity").value(10)))
                .andExpect(described("JSON $.positions[0].marketValue value 800.00000000", jsonPath("$.positions[0].marketValue").value(800.00000000)))
                .andExpect(described("JSON $.positions[0].totalReturnPercent value 0.0000", jsonPath("$.positions[0].totalReturnPercent").value(0.0000)))
                .andExpect(described("JSON $.positions[0].dailyReturnPercent value 6.6667", jsonPath("$.positions[0].dailyReturnPercent").value(6.6667)));
    }

    @Test
    @DisplayName("A market sell reduces holdings and increases cash")
    void givenOwnedShares_whenSellingAtMarket_thenPortfolioReflectsSale() throws Exception {
        // Given
        placeOrder(1, marketBody("BUY", "quantity", "10")).andExpect(described("HTTP status isCreated", status().isCreated()));

        // When
        var response = placeOrder(1, marketBody("SELL", "quantity", "4"));

        // Then
        response
                .andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.status value \"FILLED\"", jsonPath("$.status").value("FILLED")));

        portfolio(1)
                .andExpect(described("JSON $.availableCash value 520.00000000", jsonPath("$.availableCash").value(520.00000000)))
                .andExpect(described("JSON $.positions[0].quantity value 6", jsonPath("$.positions[0].quantity").value(6)));
    }

    @Test
    @DisplayName("A limit buy reserves cash until cancellation")
    void givenLimitBuy_whenCancelling_thenReservedCashIsReleased() throws Exception {
        // Given
        int orderId = createdOrderId(placeOrder(1, limitBody("BUY", "5", "100")));

        portfolio(1).andExpect(described("JSON $.availableCash value 500.00000000", jsonPath("$.availableCash").value(500.00000000)));

        // When
        var response = cancel(1, orderId);

        // Then
        response.andExpect(described("HTTP status isOk", status().isOk()))
                .andExpect(described("JSON $.id value orderId", jsonPath("$.id").value(orderId)))
                .andExpect(described("JSON $.status value \"CANCELLED\"", jsonPath("$.status").value("CANCELLED")));
        portfolio(1).andExpect(described("JSON $.availableCash value 1000.00000000", jsonPath("$.availableCash").value(1000.00000000)));

        cancel(1, orderId).andExpect(described("HTTP status isConflict", status().isConflict()))
                .andExpect(described("JSON $.code value \"ORDER_NOT_CANCELLABLE\"", jsonPath("$.code").value("ORDER_NOT_CANCELLABLE")));
        assertThat(statusOf(orderId)).as("The stored order remains cancelled").isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("A limit sell reserves shares")
    void givenReservedShares_whenSellingMoreThanAvailable_thenSaleIsRejected() throws Exception {
        // Given
        placeOrder(1, marketBody("BUY", "quantity", "10")).andExpect(described("HTTP status isCreated", status().isCreated()));
        placeOrder(1, limitBody("SELL", "8", "120"))
                .andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.status value \"NEW\"", jsonPath("$.status").value("NEW")))
                .andExpect(described("JSON $.price value 120.00000000", jsonPath("$.price").value(120.00000000)));

        // When
        var response = placeOrder(1, marketBody("SELL", "quantity", "3"));

        // Then
        response
                .andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.status value \"REJECTED\"", jsonPath("$.status").value("REJECTED")))
                .andExpect(described("JSON $.rejectionReason value \"INSUFFICIENT_SHARES\"", jsonPath("$.rejectionReason").value("INSUFFICIENT_SHARES")));
        placeOrder(1, marketBody("SELL", "quantity", "2")).andExpect(described("JSON $.status value \"FILLED\"", jsonPath("$.status").value("FILLED")));
    }

    @Test
    @DisplayName("An amount buys whole units and leaves the remainder")
    void givenAmountWithRemainder_whenBuyingAtMarket_thenOnlyWholeUnitsAreBought() throws Exception {
        // Given
        String request = marketBody("BUY", "amount", "1000");

        // When
        var response = placeOrder(1, request);

        // Then
        response
                .andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.quantity value 12", jsonPath("$.quantity").value(12)))
                .andExpect(described("JSON $.status value \"FILLED\"", jsonPath("$.status").value("FILLED")));

        portfolio(1).andExpect(described("JSON $.availableCash value 40.00000000", jsonPath("$.availableCash").value(40.00000000)));
    }

    @Test
    @DisplayName("Rejects an amount below one unit")
    void givenAmountBelowUnitPrice_whenBuying_thenOrderIsNotStored() throws Exception {
        // Given
        String request = marketBody("BUY", "amount", "79.99999999");

        // When
        var response = placeOrder(1, request);

        // Then
        response
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()))
                .andExpect(described("JSON $.code value \"INVALID_ORDER\"", jsonPath("$.code").value("INVALID_ORDER")));
        assertThat(orderCount()).as("Only the initial cash deposit exists").isEqualTo(1);
    }

    @Test
    @DisplayName("Accepts exponents while enforcing financial limits")
    void givenExponentAndOutOfRangeValues_whenPlacingOrders_thenFinancialLimitsAreEnforced() throws Exception {
        // Given
        String validAmount = marketBody("BUY", "amount", "1e3");

        // When
        var response = placeOrder(1, validAmount);

        // Then
        response
                .andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.quantity value 12", jsonPath("$.quantity").value(12)));
        placeOrder(1, marketBody("BUY", "amount", "1e2147483647"))
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, marketBody("BUY", "amount", "100e2147483647"))
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, marketBody("BUY", "quantity", "100e2147483647"))
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, limitBody("BUY", "1", "100e2147483647"))
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, marketBody("BUY", "amount", "1e-9"))
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, marketBody("BUY", "quantity", "2.147483648e9"))
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, limitBody("BUY", "1e0", "1.001"))
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        assertThat(orderCount()).as("Only the valid order and deposit are stored").isEqualTo(2);
    }

    @Test
    @DisplayName("Persists an insufficient-funds rejection without changing balances")
    void givenInsufficientCash_whenBuyingAtMarket_thenRejectionIsStoredWithoutChangingBalances() throws Exception {
        // Given
        String request = marketBody("BUY", "quantity", "13");

        // When
        var response = placeOrder(1, request);

        // Then
        response
                .andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.status value \"REJECTED\"", jsonPath("$.status").value("REJECTED")))
                .andExpect(described("JSON $.rejectionReason value \"INSUFFICIENT_FUNDS\"", jsonPath("$.rejectionReason").value("INSUFFICIENT_FUNDS")));

        assertThat(orderCount()).as("The rejected order is persisted alongside the deposit").isEqualTo(2);
        portfolio(1).andExpect(described("JSON $.availableCash value 1000.00000000", jsonPath("$.availableCash").value(1000.00000000)))
                .andExpect(described("JSON $.positions isEmpty", jsonPath("$.positions").isEmpty()));
    }

    @Test
    @DisplayName("A full trading journey keeps cash and holdings consistent")
    void givenTradingJourney_whenPlacingCancellingAndRejectingOrders_thenPortfolioStaysConsistent() throws Exception {
        // Given
        int reservedOrderId = createdOrderId(placeOrder(1, limitBody("BUY", "5", "100")));
        portfolio(1).andExpect(described("Cash is reserved by the limit buy", jsonPath("$.availableCash").value(500.00000000)));

        // When
        cancel(1, reservedOrderId).andExpect(described("HTTP status isOk", status().isOk()));
        placeOrder(1, marketBody("BUY", "quantity", "10")).andExpect(described("HTTP status isCreated", status().isCreated()));
        placeOrder(1, marketBody("SELL", "quantity", "4")).andExpect(described("HTTP status isCreated", status().isCreated()));
        var rejected = placeOrder(1, marketBody("BUY", "amount", "100000"));

        // Then
        rejected.andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.status value \"REJECTED\"", jsonPath("$.status").value("REJECTED")))
                .andExpect(described("JSON $.rejectionReason value \"INSUFFICIENT_FUNDS\"", jsonPath("$.rejectionReason").value("INSUFFICIENT_FUNDS")));
        assertThat(orderCount()).as("Deposit, cancelled, two fills and the rejection are persisted").isEqualTo(5);
        portfolio(1)
                .andExpect(described("JSON $.availableCash value 520.00000000", jsonPath("$.availableCash").value(520.00000000)))
                .andExpect(described("JSON $.positions[0].quantity value 6", jsonPath("$.positions[0].quantity").value(6)))
                .andExpect(described("JSON $.totalValue value 1000.00000000", jsonPath("$.totalValue").value(1000.00000000)));
    }

    @Test
    @DisplayName("Validation and lookup errors do not persist orders")
    void givenInvalidRequestsAndMissingEntities_whenPlacingOrders_thenNoOrdersAreStored() throws Exception {
        // Given
        int initialCount = orderCount();

        // When
        var response = placeOrder(1, "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1,\"x\":1}");

        // Then
        response
                .andExpect(described("HTTP status isBadRequest", status().isBadRequest())).andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));
        placeOrder(1, "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"STOP\",\"quantity\":1}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER"))
                .andExpect(jsonPath("$.message").value("type must be one of: MARKET, LIMIT"));
        placeOrder(1, "{\"instrumentId\":2,\"side\":\"CASH_IN\",\"type\":\"MARKET\",\"quantity\":1}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("side must be one of: BUY, SELL"));
        placeOrder(1, "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":0,\"quantity\":1}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("type must be one of: MARKET, LIMIT"));
        placeOrder(1, "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":{},\"quantity\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        placeOrder(1, "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1,\"amount\":5}")
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity())).andExpect(described("JSON $.code value \"INVALID_ORDER\"", jsonPath("$.code").value("INVALID_ORDER")));
        placeOrder(1, "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1.5}")
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1,\"limitPrice\":1}")
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1}")
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, "{\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}")
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, marketBody("BUY", "quantity", "-1")).andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()));
        placeOrder(1, "{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}")
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity())).andExpect(described("JSON $.code value \"INVALID_ORDER\"", jsonPath("$.code").value("INVALID_ORDER")));
        placeOrder(99, marketBody("BUY", "quantity", "1"))
                .andExpect(described("HTTP status isNotFound", status().isNotFound())).andExpect(described("JSON $.code value \"USER_NOT_FOUND\"", jsonPath("$.code").value("USER_NOT_FOUND")));
        placeOrder(1, "{\"instrumentId\":99,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}")
                .andExpect(described("HTTP status isNotFound", status().isNotFound())).andExpect(described("JSON $.code value \"INSTRUMENT_NOT_FOUND\"", jsonPath("$.code").value("INSTRUMENT_NOT_FOUND")));
        placeOrder(1, "{\"instrumentId\":3,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}")
                .andExpect(described("HTTP status isConflict", status().isConflict())).andExpect(described("JSON $.code value \"MARKET_DATA_UNAVAILABLE\"", jsonPath("$.code").value("MARKET_DATA_UNAVAILABLE")));

        assertThat(orderCount()).as("No order is added after validation and lookup errors").isEqualTo(initialCount);
    }

    @Test
    @DisplayName("Hides another user's order during cancellation")
    void givenAnotherUsersOrder_whenCancelling_thenOrderIsNotFoundAndUnchanged() throws Exception {
        // Given
        int orderId = createdOrderId(placeOrder(1, limitBody("BUY", "5", "100")));

        // When
        cancel(2, orderId).andExpect(described("HTTP status isNotFound", status().isNotFound())).andExpect(described("JSON $.code value \"ORDER_NOT_FOUND\"", jsonPath("$.code").value("ORDER_NOT_FOUND")));
        // Then
        cancel(1, 9999).andExpect(described("HTTP status isNotFound", status().isNotFound()));
        assertThat(statusOf(orderId)).as("The order remains new").isEqualTo("NEW");
    }

    @Test
    @DisplayName("Concurrent buys do not overdraw cash")
    void givenLimitedCash_whenSubmittingConcurrentBuys_thenCashIsNotOverdrawn() throws Exception {
        // Given
        int requests = 10;

        // When
        List<Integer> statuses = runConcurrently(requests, () ->
                placeOrder(1, marketBody("BUY", "quantity", "5")).andReturn().getResponse().getStatus());

        // Then
        assertThat(statuses).as("Every persisted decision returns 201").containsOnly(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE status = 'FILLED' AND side = 'BUY'",
                Integer.class)).as("Only two buys can be filled").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE status = 'REJECTED'", Integer.class))
                .as("The remaining buys are rejected").isEqualTo(8);
        portfolio(1).andExpect(described("JSON $.availableCash value 200.00000000", jsonPath("$.availableCash").value(200.00000000)))
                .andExpect(described("JSON $.positions[0].quantity value 10", jsonPath("$.positions[0].quantity").value(10)));
    }

    @Test
    @DisplayName("Concurrent sells do not oversell shares")
    void givenOwnedShares_whenSubmittingConcurrentSells_thenSharesAreNotOversold() throws Exception {
        // Given
        placeOrder(1, marketBody("BUY", "quantity", "10")).andExpect(described("HTTP status isCreated", status().isCreated()));

        // When
        runConcurrently(8, () ->
                placeOrder(1, marketBody("SELL", "quantity", "3")).andReturn().getResponse().getStatus());

        // Then
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE status = 'FILLED' AND side = 'SELL'",
                Integer.class)).as("Only three sales can be filled").isEqualTo(3);
        portfolio(1).andExpect(described("JSON $.positions[0].quantity value 1", jsonPath("$.positions[0].quantity").value(1)));
    }

    @Test
    @DisplayName("Only one concurrent cancellation succeeds")
    void givenNewOrder_whenCancellingConcurrently_thenExactlyOneCancellationSucceeds() throws Exception {
        // Given
        int orderId = createdOrderId(placeOrder(1, limitBody("BUY", "5", "100")));

        // When
        List<Integer> statuses = runConcurrently(6, () -> cancel(1, orderId).andReturn().getResponse().getStatus());

        // Then
        assertThat(statuses).as("One cancellation succeeds").filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).as("The other cancellations conflict").filteredOn(s -> s == 409).hasSize(5);
        assertThat(statusOf(orderId)).as("The stored order is cancelled").isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Search treats wildcards literally and supports pagination")
    void givenInstrumentsAndSearchParameters_whenSearching_thenLiteralMatchesAndPagesAreReturned() throws Exception {
        // Given
        String literalWildcard = "%";

        // When
        mvc.perform(get("/api/v1/instruments").param("q", literalWildcard))
                // Then
                .andExpect(described("HTTP status isOk", status().isOk()))
                .andExpect(described("JSON $.items.length() value 1", jsonPath("$.items.length()").value(1)))
                .andExpect(described("JSON $.items[0].ticker value \"A%B\"", jsonPath("$.items[0].ticker").value("A%B")));
        mvc.perform(get("/api/v1/instruments").param("q", "ACTIVO"))
                .andExpect(described("JSON $.items[0].ticker value \"AAA\"", jsonPath("$.items[0].ticker").value("AAA")))
                .andExpect(described("JSON $.limit value 20", jsonPath("$.limit").value(20)));
        mvc.perform(get("/api/v1/instruments").param("q", "a").param("limit", "1").param("offset", "1"))
                .andExpect(described("JSON $.items.length() value 1", jsonPath("$.items.length()").value(1)))
                .andExpect(described("JSON $.offset value 1", jsonPath("$.offset").value(1)));
        mvc.perform(get("/api/v1/instruments").param("q", "nomatch"))
                .andExpect(described("JSON $.items isEmpty", jsonPath("$.items").isEmpty()));
        mvc.perform(get("/api/v1/instruments").param("q", "  ")).andExpect(described("HTTP status isBadRequest", status().isBadRequest()));
        mvc.perform(get("/api/v1/instruments").param("q", "a").param("limit", "101")).andExpect(described("HTTP status isBadRequest", status().isBadRequest()));
        mvc.perform(get("/api/v1/instruments").param("q", "a").param("offset", "-1")).andExpect(described("HTTP status isBadRequest", status().isBadRequest()));
    }

    @Test
    @DisplayName("Returns not found for an unknown user's portfolio")
    void givenUnknownUser_whenGettingPortfolio_thenUserIsNotFound() throws Exception {
        // Given
        String path = "/api/v1/users/99/portfolio";

        // When
        var response = mvc.perform(get(path));

        // Then
        response
                .andExpect(described("HTTP status isNotFound", status().isNotFound())).andExpect(described("JSON $.code value \"USER_NOT_FOUND\"", jsonPath("$.code").value("USER_NOT_FOUND")));
    }

    @Test
    @DisplayName("Returns the portfolio contract's numeric example")
    void givenContractExample_whenGettingPortfolio_thenValuesAndReturnsMatch() throws Exception {
        // Given
        jdbc.update("TRUNCATE TABLE orders RESTART IDENTITY");
        cashIn(1, "1100");
        order("BUY", "10", "70");
        quote("80", "75", "2026-01-02T10:00:00Z");

        // When
        var response = portfolio(1);

        // Then
        response
                .andExpect(described("JSON $.availableCash value 400.00000000", jsonPath("$.availableCash").value(400.00000000)))
                .andExpect(described("JSON $.totalValue value 1200.00000000", jsonPath("$.totalValue").value(1200.00000000)))
                .andExpect(described("JSON $.positions[0].marketPrice value 80.00000000", jsonPath("$.positions[0].marketPrice").value(80.00000000)))
                .andExpect(described("JSON $.positions[0].marketValue value 800.00000000", jsonPath("$.positions[0].marketValue").value(800.00000000)))
                .andExpect(described("JSON $.positions[0].totalReturnPercent value 14.2857", jsonPath("$.positions[0].totalReturnPercent").value(14.2857)))
                .andExpect(described("JSON $.positions[0].dailyReturnPercent value 6.6667", jsonPath("$.positions[0].dailyReturnPercent").value(6.6667)));
    }

    @Test
    @DisplayName("Shows the negative BMA position of the untouched reference data instead of hiding it or failing")
    void givenReferenceData_whenGettingPortfolio_thenNegativePositionIsReported() throws Exception {
        // Given
        jdbc.execute("TRUNCATE TABLE marketdata, orders, instruments, users RESTART IDENTITY CASCADE");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__load_reference_data.sql"));
        }

        // When / Then
        portfolio(1)
                .andExpect(described("BMA is reported",
                        jsonPath("$.positions[?(@.ticker == 'BMA')].quantity").value(-10)))
                .andExpect(described("BMA has a negative market value",
                        jsonPath("$.positions[?(@.ticker == 'BMA')].marketValue").value(-15028.0)));
    }

    private ResultActions placeOrder(int userId, String body) throws Exception {
        return mvc.perform(post("/api/v1/users/" + userId + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions cancel(int userId, int orderId) throws Exception {
        return mvc.perform(post("/api/v1/users/" + userId + "/orders/" + orderId + "/cancel"));
    }

    private ResultActions portfolio(int userId) throws Exception {
        return mvc.perform(get("/api/v1/users/" + userId + "/portfolio")).andExpect(described("HTTP status isOk", status().isOk()));
    }

    private int createdOrderId(ResultActions result) throws Exception {
        result.andExpect(described("HTTP status isCreated", status().isCreated())).andExpect(described("JSON $.status value \"NEW\"", jsonPath("$.status").value("NEW")));
        return jdbc.queryForObject("SELECT max(id) FROM orders", Integer.class);
    }

    private int orderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class);
    }

    private String statusOf(int orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private void cashIn(int userId, String amount) {
        jdbc.update("INSERT INTO orders (instrumentId, userId, side, size, price, type, status, datetime) "
                + "VALUES (1, ?, 'CASH_IN', ?::numeric, NULL, 'MARKET', 'FILLED', '2026-01-01T09:00:00Z')", userId, amount);
    }

    private void order(String side, String size, String price) {
        jdbc.update("INSERT INTO orders (instrumentId, userId, side, size, price, type, status, datetime) "
                        + "VALUES (2, 1, ?, ?::numeric, ?::numeric, 'MARKET', 'FILLED', '2026-01-01T12:00:00Z')",
                side, size, price);
    }

    private void quote(String close, String previousClose, String datetime) {
        jdbc.update("INSERT INTO marketdata (instrumentId, close, previousClose, date) "
                + "VALUES (2, ?::numeric, ?::numeric, ?::date)", close, previousClose, datetime.substring(0, 10));
    }
}
