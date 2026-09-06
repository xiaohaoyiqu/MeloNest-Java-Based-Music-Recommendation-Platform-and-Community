


package com.haoran.music.service;

import com.haoran.music.dto.appeal.AccountRestrictionAppealCodeDTO;
import com.haoran.music.dto.appeal.AccountRestrictionAppealSubmitDTO;




public interface AccountRestrictionAppealService {





    void requestBoundContactCode(AccountRestrictionAppealCodeDTO dto, String clientIp, String userAgent);





    boolean submitBoundContactAppeal(AccountRestrictionAppealSubmitDTO dto);
}
