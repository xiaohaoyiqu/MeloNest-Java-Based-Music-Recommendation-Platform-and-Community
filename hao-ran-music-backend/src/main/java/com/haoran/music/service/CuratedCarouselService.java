


package com.haoran.music.service;

import com.haoran.music.dto.curated.CuratedCarouselRequest;
import com.haoran.music.entity.CuratedCarouselItem;
import com.haoran.music.vo.curated.CuratedCarouselItemVO;

import java.util.List;

public interface CuratedCarouselService {

    List<CuratedCarouselItemVO> getPublicItems(String scene, Integer limit);

    boolean incrementViewCount(Long itemId);

    List<CuratedCarouselItem> listAdminItems(String scene, boolean removed);

    Long create(CuratedCarouselRequest request, Long operatorId);

    boolean update(Long itemId, CuratedCarouselRequest request, Long operatorId);

    boolean review(Long itemId, boolean approved, String remark, Long reviewerId);

    boolean disable(Long itemId, Long operatorId);

    boolean restore(Long itemId, Long operatorId);
}
