package com.travelmapper.service.prep;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.security.CurrentUser;
import com.travelmapper.controller.prep.dto.OfficialLinkDtos;
import com.travelmapper.repository.entity.CountryOfficialLink;
import com.travelmapper.repository.mapper.CountryOfficialLinkMapper;
import com.travelmapper.service.audit.AuditLogService;
import com.travelmapper.service.catalog.CatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OfficialLinkService {

    private final CountryOfficialLinkMapper mapper;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;

    public OfficialLinkService(CountryOfficialLinkMapper mapper, CatalogService catalogService,
                               AuditLogService auditLogService) {
        this.mapper = mapper;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
    }

    public OfficialLinkDtos.PublicResponse getPublic(String rawCountryCode) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        String countryName = catalogService.countryName(countryCode);
        List<OfficialLinkDtos.PublicLink> links = mapper.selectList(
                        new LambdaQueryWrapper<CountryOfficialLink>()
                                .eq(CountryOfficialLink::getCountryCode, countryCode)
                                .orderByAsc(CountryOfficialLink::getSortOrder)
                                .orderByAsc(CountryOfficialLink::getId))
                .stream()
                .filter(link -> isValidUrl(link.getUrl()))
                .limit(3)
                .map(link -> new OfficialLinkDtos.PublicLink(link.getTitle(), link.getUrl()))
                .toList();
        if (links.isEmpty()) {
            String searchUrl = "https://www.bing.com/search?q=" + URLEncoder.encode(
                    countryName + " 签证 官方", StandardCharsets.UTF_8);
            return new OfficialLinkDtos.PublicResponse(countryCode, true, List.of(), searchUrl);
        }
        return new OfficialLinkDtos.PublicResponse(countryCode, false, links, null);
    }

    public List<OfficialLinkDtos.AdminView> adminList(String rawCountryCode) {
        String countryCode = rawCountryCode == null ? null : rawCountryCode.toUpperCase(Locale.ROOT);
        return mapper.selectList(new LambdaQueryWrapper<CountryOfficialLink>()
                        .eq(countryCode != null, CountryOfficialLink::getCountryCode, countryCode)
                        .orderByAsc(CountryOfficialLink::getCountryCode)
                        .orderByAsc(CountryOfficialLink::getSortOrder)
                        .orderByAsc(CountryOfficialLink::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public OfficialLinkDtos.AdminView create(OfficialLinkDtos.SaveRequest request) {
        String countryCode = request.getCountryCode().toUpperCase(Locale.ROOT);
        catalogService.countryName(countryCode);
        Long count = mapper.selectCount(new LambdaQueryWrapper<CountryOfficialLink>()
                .eq(CountryOfficialLink::getCountryCode, countryCode));
        if (count >= 3) {
            throw new BusinessException(ErrorCode.OFFICIAL_LINK_LIMIT);
        }
        validateUrl(request.getUrl());
        CountryOfficialLink link = new CountryOfficialLink();
        link.setCountryCode(countryCode);
        link.setTitle(request.getTitle());
        link.setUrl(request.getUrl());
        link.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        mapper.insert(link);
        auditLogService.record(CurrentUser.id(), "OFFICIAL_LINK_CREATE", "CONFIG", link.getId(),
                Map.of("countryCode", countryCode, "url", request.getUrl()), null);
        return toView(link);
    }

    @Transactional
    public OfficialLinkDtos.AdminView update(Long id, OfficialLinkDtos.SaveRequest request) {
        CountryOfficialLink link = mapper.selectById(id);
        if (link == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "官方链接不存在");
        }
        validateUrl(request.getUrl());
        link.setTitle(request.getTitle());
        link.setUrl(request.getUrl());
        if (request.getSortOrder() != null) {
            link.setSortOrder(request.getSortOrder());
        }
        mapper.updateById(link);
        auditLogService.record(CurrentUser.id(), "OFFICIAL_LINK_UPDATE", "CONFIG", id,
                Map.of("countryCode", link.getCountryCode(), "url", link.getUrl()), null);
        return toView(link);
    }

    @Transactional
    public void delete(Long id) {
        CountryOfficialLink link = mapper.selectById(id);
        if (link == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "官方链接不存在");
        }
        mapper.deleteById(id);
        auditLogService.record(CurrentUser.id(), "OFFICIAL_LINK_DELETE", "CONFIG", id,
                Map.of("countryCode", link.getCountryCode()), null);
    }

    private OfficialLinkDtos.AdminView toView(CountryOfficialLink link) {
        return new OfficialLinkDtos.AdminView(
                link.getId(), link.getCountryCode(), link.getTitle(),
                link.getUrl(), link.getSortOrder());
    }

    private void validateUrl(String url) {
        if (!isValidUrl(url)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "URL 必须为 http/https 且不超过 500 字符");
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank() || url.length() > 500) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return uri.getScheme() != null
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
