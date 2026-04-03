package com.yunhwan.wit.application.location;

import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.Optional;

public interface LocationResolutionCache {

    Optional<ResolvedLocation> find(String rawLocation);

    void put(String rawLocation, ResolvedLocation resolvedLocation);
}
