package com.retailmedia.insights.security;

/** Who is calling, which decides which of a tenant's campaigns they may see. */
public enum Role {

    /** A retailer user (for example a Tesco analyst). Sees every campaign in their tenant. */
    RETAILER,

    /** An advertiser user (for example Nestlé on Tesco). Sees only their own brand's campaigns. */
    BRAND
}
