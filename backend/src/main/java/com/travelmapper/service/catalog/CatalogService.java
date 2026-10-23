package com.travelmapper.service.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.api.PageRequest;
import com.travelmapper.common.api.PageResult;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.admin.dto.AdminCityView;
import com.travelmapper.controller.admin.dto.CreateCityRequest;
import com.travelmapper.controller.admin.dto.DeleteCityResponse;
import com.travelmapper.controller.admin.dto.UpdateCityRequest;
import com.travelmapper.controller.catalog.dto.CityView;
import com.travelmapper.controller.catalog.dto.CountryView;
import com.travelmapper.repository.entity.City;
import com.travelmapper.repository.entity.Country;
import com.travelmapper.repository.enums.CityLevel;
import com.travelmapper.repository.enums.EnabledStatus;
import com.travelmapper.repository.mapper.CityMapper;
import com.travelmapper.repository.mapper.CountryMapper;
import com.travelmapper.service.audit.AuditLogService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CatalogService {

    private final CountryMapper countryMapper;
    private final CityMapper cityMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public CatalogService(CountryMapper countryMapper, CityMapper cityMapper,
                          JdbcTemplate jdbcTemplate, AuditLogService auditLogService) {
        this.countryMapper = countryMapper;
        this.cityMapper = cityMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    public List<CountryView> listCountries() {
        return countryMapper.selectList(new LambdaQueryWrapper<Country>()
                        .eq(Country::getStatus, EnabledStatus.ENABLED)
                        .orderByAsc(Country::getSortOrder)
                        .orderByAsc(Country::getCode))
                .stream()
                .map(country -> new CountryView(
                        country.getCode(),
                        country.getNameZh(),
                        country.getNameEn(),
                        country.getContinent() == null ? null : country.getContinent().name()))
                .toList();
    }

    public List<CityView> listCities(String countryCode) {
        Country country = requireEnabledCountry(countryCode);
        return cityMapper.selectList(new LambdaQueryWrapper<City>()
                        .eq(City::getCountryCode, country.getCode())
                        .eq(City::getStatus, EnabledStatus.ENABLED)
                        .orderByAsc(City::getSortOrder)
                        .orderByAsc(City::getCityCode))
                .stream()
                .map(city -> new CityView(
                        city.getCityCode(),
                        city.getName(),
                        city.getLevel().name(),
                        displayName(city, country)))
                .toList();
    }

    public Country requireEnabledCountry(String countryCode) {
        Country country = findCountry(countryCode);
        if (country == null || country.getStatus() != EnabledStatus.ENABLED) {
            throw new BusinessException(ErrorCode.COUNTRY_OR_CITY_INVALID);
        }
        return country;
    }

    public String countryName(String countryCode) {
        Country country = findCountry(countryCode);
        if (country == null) {
            throw new BusinessException(ErrorCode.COUNTRY_OR_CITY_INVALID);
        }
        return country.getNameZh();
    }

    public City requireEnabledCity(String countryCode, String cityCode) {
        Country country = requireEnabledCountry(countryCode);
        City city = findCity(countryCode, cityCode);
        if (city == null || city.getStatus() != EnabledStatus.ENABLED) {
            throw new BusinessException(ErrorCode.COUNTRY_OR_CITY_INVALID);
        }
        return city;
    }

    public City findCity(String countryCode, String cityCode) {
        if (countryCode == null || cityCode == null) {
            return null;
        }
        return cityMapper.selectOne(new LambdaQueryWrapper<City>()
                .eq(City::getCountryCode, countryCode.toUpperCase(Locale.ROOT))
                .eq(City::getCityCode, cityCode.toUpperCase(Locale.ROOT)));
    }

    public String cityName(String countryCode, String cityCode) {
        City city = findCity(countryCode, cityCode);
        return city == null ? cityCode : city.getName();
    }

    public String displayName(City city, Country country) {
        return city.getName() + "（" + country.getNameZh() + "）";
    }

    public PageResult<AdminCityView> adminList(String countryCode, EnabledStatus status,
                                               String keyword, PageRequest request) {
        request.validate();
        LambdaQueryWrapper<City> wrapper = new LambdaQueryWrapper<City>()
                .eq(countryCode != null && !countryCode.isBlank(), City::getCountryCode, countryCode)
                .eq(status != null, City::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), City::getName, keyword)
                .orderByAsc(City::getCountryCode)
                .orderByAsc(City::getSortOrder)
                .orderByAsc(City::getCityCode);
        Page<City> page = cityMapper.selectPage(new Page<>(request.getPage(), request.getSize()), wrapper);
        List<AdminCityView> list = page.getRecords().stream().map(this::toAdminView).toList();
        return PageResult.of(page.getTotal(), request, list);
    }

    @Transactional
    public AdminCityView createCity(CreateCityRequest request) {
        Country country = findCountry(request.getCountryCode());
        if (country == null) {
            throw new BusinessException(ErrorCode.COUNTRY_OR_CITY_INVALID);
        }
        Long count = cityMapper.selectCount(new LambdaQueryWrapper<City>()
                .eq(City::getCountryCode, request.getCountryCode())
                .eq(City::getCityCode, request.getCityCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该国家下城市代码已存在");
        }
        City city = new City();
        city.setCountryCode(request.getCountryCode());
        city.setCityCode(request.getCityCode());
        city.setName(request.getName());
        city.setLevel(request.getLevel() == null ? CityLevel.MAJOR : request.getLevel());
        city.setStatus(EnabledStatus.ENABLED);
        city.setSortOrder(0);
        try {
            cityMapper.insert(city);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该国家下城市代码已存在");
        }
        auditLogService.record(CurrentUser.id(), "CITY_CREATE", "CITY", city.getId(),
                Map.of("countryCode", city.getCountryCode(), "cityCode", city.getCityCode()), null);
        return toAdminView(city);
    }

    @Transactional
    public AdminCityView updateCity(Long id, UpdateCityRequest request) {
        City city = cityMapper.selectById(id);
        if (city == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "城市不存在");
        }
        if (request.getName() != null) {
            city.setName(request.getName());
        }
        if (request.getStatus() != null) {
            city.setStatus(request.getStatus());
        }
        cityMapper.updateById(city);
        auditLogService.record(CurrentUser.id(), "CITY_UPDATE", "CITY", id,
                Map.of("name", city.getName(), "status", city.getStatus().name()), null);
        return toAdminView(city);
    }

    @Transactional
    public DeleteCityResponse deleteCity(Long id) {
        City city = cityMapper.selectById(id);
        if (city == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "城市不存在");
        }
        if (isReferenced(city.getCountryCode(), city.getCityCode())) {
            throw new BusinessException(ErrorCode.CITY_REFERENCED);
        }
        cityMapper.deleteById(id);
        auditLogService.record(CurrentUser.id(), "CITY_DELETE", "CITY", id,
                Map.of("countryCode", city.getCountryCode(), "cityCode", city.getCityCode()), null);
        return new DeleteCityResponse(id, true);
    }

    public boolean isReferenced(String countryCode, String cityCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT "
                        + "(SELECT COUNT(*) FROM itinerary_day d JOIN itinerary i ON d.itinerary_id = i.id "
                        + " WHERE i.deleted = 0 AND i.country_code = ? AND d.city_code = ?) + "
                        + "(SELECT COUNT(*) FROM travel_note n "
                        + " WHERE n.deleted = 0 AND n.country_code = ? AND n.city_code = ?) + "
                        + "(SELECT COUNT(*) FROM expense e "
                        + " WHERE e.deleted = 0 AND e.country_code = ? AND e.city_code = ?)",
                Integer.class, countryCode, cityCode, countryCode, cityCode, countryCode, cityCode);
        return count != null && count > 0;
    }

    private Country findCountry(String countryCode) {
        if (countryCode == null) {
            return null;
        }
        return countryMapper.selectOne(new LambdaQueryWrapper<Country>()
                .eq(Country::getCode, countryCode.toUpperCase(Locale.ROOT)));
    }

    private AdminCityView toAdminView(City city) {
        return new AdminCityView(
                city.getId(),
                city.getCountryCode(),
                city.getCityCode(),
                city.getName(),
                city.getLevel().name(),
                city.getStatus().name(),
                isReferenced(city.getCountryCode(), city.getCityCode()),
                city.getCreatedAt());
    }
}
