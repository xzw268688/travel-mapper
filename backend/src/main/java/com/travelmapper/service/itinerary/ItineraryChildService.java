package com.travelmapper.service.itinerary;

import com.travelmapper.repository.mapper.ItineraryDayMapper;
import com.travelmapper.repository.mapper.ItinerarySpotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Child tables are physically deleted. Spots are removed before days so the
 * day subquery still resolves, and a subsequent regeneration can reuse dayIndex.
 */
@Service
public class ItineraryChildService {

    private final ItinerarySpotMapper spotMapper;
    private final ItineraryDayMapper dayMapper;

    public ItineraryChildService(ItinerarySpotMapper spotMapper, ItineraryDayMapper dayMapper) {
        this.spotMapper = spotMapper;
        this.dayMapper = dayMapper;
    }

    @Transactional
    public void deleteChildren(Long itineraryId) {
        spotMapper.deleteByItineraryIdPhysically(itineraryId);
        dayMapper.deleteByItineraryIdPhysically(itineraryId);
    }
}
