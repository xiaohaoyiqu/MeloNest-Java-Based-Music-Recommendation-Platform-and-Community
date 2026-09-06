


package com.haoran.music.service;

import com.haoran.music.entity.Artist;


public interface ArtistProfileService {

    Artist resolveOwnedProfile(Long userId, String displayName, String sourceType);
}
