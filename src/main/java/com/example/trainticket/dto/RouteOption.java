package com.example.trainticket.dto;

import java.util.List;

public record RouteOption(
        List<SegmentOption> segments,
        long totalMinutes,
        int transfers
) {}
