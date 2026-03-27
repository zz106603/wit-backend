# Design Overview

This project is a backend MVP that recommends outfits based on:
- calendar events
- location
- weather

## Core Flow

CalendarEvent  
→ Location Resolution (rule-based + AI fallback)  
→ Weather Snapshot  
→ Rule Engine  
→ Outfit Decision  
→ AI Summary

## Key Principle

- Recommendation logic is entirely rule-based
- AI is only used for:
    - location interpretation
    - summary generation