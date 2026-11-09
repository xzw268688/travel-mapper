package com.travelmapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.repository.entity.City;
import com.travelmapper.repository.entity.Country;
import com.travelmapper.repository.entity.Itinerary;
import com.travelmapper.repository.entity.ItineraryDay;
import com.travelmapper.repository.entity.ItinerarySpot;
import com.travelmapper.repository.entity.User;
import com.travelmapper.repository.enums.CityLevel;
import com.travelmapper.repository.enums.ItineraryStatus;
import com.travelmapper.repository.enums.Role;
import com.travelmapper.repository.enums.SpotSource;
import com.travelmapper.repository.enums.UserStatus;
import com.travelmapper.repository.mapper.CityMapper;
import com.travelmapper.repository.mapper.CountryMapper;
import com.travelmapper.repository.mapper.ItineraryDayMapper;
import com.travelmapper.repository.mapper.ItineraryMapper;
import com.travelmapper.repository.mapper.ItinerarySpotMapper;
import com.travelmapper.repository.mapper.UserMapper;
import com.travelmapper.service.itinerary.ItineraryChildService;
import com.travelmapper.support.AbstractIntegrationTest;
import com.travelmapper.support.TestInfrastructure;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CountryMapper countryMapper;

    @Autowired
    private CityMapper cityMapper;

    @Autowired
    private ItineraryMapper itineraryMapper;

    @Autowired
    private ItineraryDayMapper itineraryDayMapper;

    @Autowired
    private ItinerarySpotMapper itinerarySpotMapper;

    @Autowired
    private ItineraryChildService itineraryChildService;

    @Test
    void allBusinessTablesExistAndOptionalTableIsExcluded() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
        assertThat(tables).contains(
                "user", "email_token", "country", "city", "trip", "itinerary",
                "itinerary_day", "itinerary_spot", "country_official_link",
                "trip_checklist", "travel_note", "note_share", "expense",
                "exchange_rate", "card_key", "quota_pack", "quota_ledger",
                "sys_config", "audit_log");
        assertThat(tables).doesNotContain("exchange_rate_history");
        assertThat(tables).doesNotContain("checklist_item", "checklist_items");
    }

    @Test
    void uniqueIndexesExist() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND non_unique = 0",
                String.class);
        assertThat(indexes).contains(
                "uk_username", "uk_email", "uk_country_city", "uk_code",
                "uk_token", "uk_user_itin_item", "uk_currency", "uk_key");
    }

    @Test
    void amountCnyIsStoredGeneratedColumn() {
        String remark = "amount-cny-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO expense "
                        + "(user_id, country_code, currency, amount, exchange_rate_snapshot, category, expense_date, remark, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                1L, "JP", "JPY", new BigDecimal("100.00"), new BigDecimal("0.500000"),
                "FOOD", LocalDate.now(), remark);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM expense WHERE remark = ?", Long.class, remark);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT amount_cny FROM expense WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("50.00");

        jdbcTemplate.update("UPDATE expense SET amount = ? WHERE id = ?", new BigDecimal("200.00"), id);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT amount_cny FROM expense WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("100.00");

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO expense "
                        + "(user_id, country_code, currency, amount, exchange_rate_snapshot, amount_cny, category, expense_date, remark, deleted) "
                        + "VALUES (1, 'JP', 'JPY', 1, 1, 1, 'FOOD', CURDATE(), 'bad-generated', 0)"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void duplicateBusinessKeysAreRejected() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long itineraryId = 900_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000L);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO city (country_code, city_code, name, level, status, sort_order) "
                        + "VALUES ('JP', 'TYO', 'duplicate', 'MAJOR', 'ENABLED', 99)"))
                .isInstanceOf(DuplicateKeyException.class);

        String cardCode = "TM-TEST-" + suffix;
        jdbcTemplate.update("INSERT INTO card_key "
                + "(batch_id, code, type, value, status, created_by) "
                + "VALUES ('B-TEST', ?, 'VIP', 30, 'UNUSED', 1)", cardCode);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO card_key "
                + "(batch_id, code, type, value, status, created_by) "
                + "VALUES ('B-TEST', ?, 'VIP', 30, 'UNUSED', 1)", cardCode))
                .isInstanceOf(DuplicateKeyException.class);

        String token = "s_test_" + suffix;
        jdbcTemplate.update("INSERT INTO note_share (note_id, token, revoked) "
                + "VALUES (999, ?, 0)", token);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO note_share (note_id, token, revoked) VALUES (999, ?, 0)", token))
                .isInstanceOf(DuplicateKeyException.class);

        jdbcTemplate.update("INSERT INTO trip_checklist "
                + "(user_id, country_code, itinerary_id, item_key, checked, deleted) "
                + "VALUES (1, 'JP', ?, 'VISA', 0, 0)", itineraryId);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO trip_checklist "
                + "(user_id, country_code, itinerary_id, item_key, checked, deleted) "
                + "VALUES (1, 'JP', ?, 'VISA', 0, 0)", itineraryId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void seedDataIsIdempotent() {
        int countriesBefore = countTable("country");
        int citiesBefore = countTable("city");
        int ratesBefore = countTable("exchange_rate");
        int configsBefore = countTable("sys_config");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V2__seed.sql"));
        populator.execute(dataSource);
        populator.execute(dataSource);

        assertThat(countTable("country")).isEqualTo(countriesBefore).isGreaterThanOrEqualTo(9);
        assertThat(countTable("city")).isEqualTo(citiesBefore).isGreaterThanOrEqualTo(13);
        assertThat(countTable("exchange_rate")).isEqualTo(ratesBefore).isGreaterThanOrEqualTo(20);
        assertThat(countTable("sys_config")).isEqualTo(configsBefore).isGreaterThanOrEqualTo(25);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE username = 'admin'", Integer.class)).isEqualTo(1);
    }

    private int countTable(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
    }

    @Test
    void optionalExchangeRateHistoryMigrationCanBeEnabled() throws Exception {
        String database = "tm_optional_" + System.nanoTime();
        try (Connection root = DriverManager.getConnection(
                TestInfrastructure.mysqlAdminJdbcUrl(), TestInfrastructure.mysqlAdminUsername(), TestInfrastructure.mysqlAdminPassword())) {
            root.createStatement().execute("CREATE DATABASE " + database);
        }
        String url = TestInfrastructure.mysqlAdminJdbcUrl()
                .replace("/travelmapper", "/" + database);
        try {
            Flyway.configure().dataSource(url, TestInfrastructure.mysqlAdminUsername(), TestInfrastructure.mysqlAdminPassword())
                    .locations("classpath:db/migration").load().migrate();
            assertThat(tableExists(url, "exchange_rate_history")).isFalse();

            Flyway.configure().dataSource(url, TestInfrastructure.mysqlAdminUsername(), TestInfrastructure.mysqlAdminPassword())
                    .locations("classpath:db/migration", "classpath:db/optional")
                    .outOfOrder(true).load().migrate();
            assertThat(tableExists(url, "exchange_rate_history")).isTrue();
        } finally {
            try (Connection root = DriverManager.getConnection(
                    TestInfrastructure.mysqlAdminJdbcUrl(), TestInfrastructure.mysqlAdminUsername(), TestInfrastructure.mysqlAdminPassword())) {
                root.createStatement().execute("DROP DATABASE IF EXISTS " + database);
            }
        }
    }

    @Test
    void mapperCrudAndEnumRoundTrip() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("enum_" + suffix);
        user.setEmail("enum_" + suffix + "@example.com");
        user.setPasswordHash("hash");
        user.setNickname("enum");
        user.setAvatarPreset("preset_01");
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.NORMAL);
        user.setEmailVerified(true);
        user.setTokenVersion(1);
        userMapper.insert(user);

        User loaded = userMapper.selectById(user.getId());
        assertThat(loaded.getRole()).isEqualTo(Role.ADMIN);
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.NORMAL);

        Country japan = countryMapper.selectOne(
                new LambdaQueryWrapper<Country>().eq(Country::getCode, "JP"));
        assertThat(japan.getNameZh()).isEqualTo("日本");

        City tokyo = cityMapper.selectOne(new LambdaQueryWrapper<City>()
                .eq(City::getCountryCode, "JP")
                .eq(City::getCityCode, "TYO"));
        assertThat(tokyo.getLevel()).isEqualTo(CityLevel.MAJOR);
    }

    @Test
    void softDeleteFiltersByDefaultAndExplicitReadIncludesDeleted() {
        String title = "soft-delete-" + UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setUserId(1L);
        itinerary.setCountryCode("JP");
        itinerary.setTitle(title);
        itinerary.setStartDate(LocalDate.now());
        itinerary.setEndDate(LocalDate.now().plusDays(1));
        itinerary.setStatus(ItineraryStatus.SAVED);
        itineraryMapper.insert(itinerary);

        itineraryMapper.deleteById(itinerary.getId());

        assertThat(itineraryMapper.selectById(itinerary.getId())).isNull();
        assertThat(itineraryMapper.selectByIdIncludingDeleted(itinerary.getId())).isNotNull();
        assertThat(itineraryMapper.selectList(
                new LambdaQueryWrapper<Itinerary>().eq(Itinerary::getTitle, title))).isEmpty();
    }

    @Test
    void childRowsArePhysicallyDeletedAndDayIndexCanBeReused() {
        Itinerary itinerary = new Itinerary();
        itinerary.setUserId(1L);
        itinerary.setCountryCode("JP");
        itinerary.setTitle("child-delete-" + UUID.randomUUID());
        itinerary.setStartDate(LocalDate.now());
        itinerary.setEndDate(LocalDate.now());
        itinerary.setStatus(ItineraryStatus.SAVED);
        itineraryMapper.insert(itinerary);

        ItineraryDay day = new ItineraryDay();
        day.setItineraryId(itinerary.getId());
        day.setDayIndex(1);
        day.setVisitDate(LocalDate.now());
        day.setCityCode("TYO");
        itineraryDayMapper.insert(day);

        ItinerarySpot spot = new ItinerarySpot();
        spot.setDayId(day.getId());
        spot.setSortOrder(0);
        spot.setSource(SpotSource.AI);
        spot.setName("浅草寺");
        itinerarySpotMapper.insert(spot);

        itineraryChildService.deleteChildren(itinerary.getId());
        assertThat(itineraryDayMapper.selectById(day.getId())).isNull();
        assertThat(itinerarySpotMapper.selectById(spot.getId())).isNull();

        ItineraryDay replacement = new ItineraryDay();
        replacement.setItineraryId(itinerary.getId());
        replacement.setDayIndex(1);
        replacement.setVisitDate(LocalDate.now());
        replacement.setCityCode("TYO");
        itineraryDayMapper.insert(replacement);
        assertThat(replacement.getId()).isNotNull();

        itineraryChildService.deleteChildren(itinerary.getId());
        itineraryMapper.deleteById(itinerary.getId());
    }

    private boolean tableExists(String url, String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, TestInfrastructure.mysqlAdminUsername(), TestInfrastructure.mysqlAdminPassword());
             ResultSet resultSet = connection.createStatement().executeQuery(
                     "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() "
                             + "AND table_name = '" + table + "'")) {
            return resultSet.next();
        }
    }
}
