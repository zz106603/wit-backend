package com.yunhwan.wit.application.location;

import com.yunhwan.wit.domain.model.ResolvedLocation;

public interface AiLocationFallbackResolver {

    ResolvedLocation resolve(String rawLocation);
}
