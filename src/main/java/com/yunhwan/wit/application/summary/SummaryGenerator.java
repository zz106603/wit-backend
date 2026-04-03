package com.yunhwan.wit.application.summary;

import com.yunhwan.wit.domain.model.OutfitDecision;

public interface SummaryGenerator {

    String generate(OutfitDecision outfitDecision);
}
