package com.tradealert.alertservice.route;

public class AlertRoutes {
    public static final String BASE = "/api/alerts";
    public static final String GET_ALL = BASE;
    public static final String CREATE = BASE;
    public static final String UPDATE = BASE + "/{id}";
    public static final String DELETE = BASE + "/{id}";
}
