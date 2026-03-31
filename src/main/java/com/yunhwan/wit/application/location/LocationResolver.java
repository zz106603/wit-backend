package com.yunhwan.wit.application.location;

import com.yunhwan.wit.domain.model.ResolvedLocation;

public interface LocationResolver {

    ResolvedLocation resolve(String rawLocation);
}
