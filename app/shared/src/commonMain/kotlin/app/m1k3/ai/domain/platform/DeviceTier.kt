package app.m1k3.ai.domain.platform

/**
 * Device Tier - Classification based on device capabilities.
 *
 * Used for adaptive feature configuration based on device RAM capacity.
 * Higher tiers support more advanced features and longer context windows.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 */
enum class DeviceTier {
    /** 12GB+ RAM - Full capabilities */
    FLAGSHIP,

    /** 8GB+ RAM - High-end features */
    HIGH_END,

    /** 6GB+ RAM - Standard features */
    MID_RANGE,

    /** 4GB+ RAM - Basic features */
    BUDGET,

    /** <4GB RAM - Minimal features */
    LOW_END
}
